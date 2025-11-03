package com.patra.ingest.app.usecase.relay.executor;

import com.patra.ingest.app.usecase.relay.coordinator.RelayLeaseCoordinator;
import com.patra.ingest.app.usecase.relay.coordinator.RelayLogCoordinator;
import com.patra.ingest.app.usecase.relay.coordinator.RelayLogCoordinator.LogAccumulator;
import com.patra.ingest.app.usecase.relay.coordinator.RelayPublishCoordinator;
import com.patra.ingest.app.usecase.relay.coordinator.RelayPublishCoordinator.RelayResult;
import com.patra.ingest.domain.event.OutboxLeaseMissedEvent;
import com.patra.ingest.domain.event.OutboxMessageDeferredEvent;
import com.patra.ingest.domain.event.OutboxMessageFailedEvent;
import com.patra.ingest.domain.event.OutboxMessagePublishedEvent;
import com.patra.ingest.domain.event.OutboxRelayDomainEvent;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.vo.relay.RelayBatchId;
import com.patra.ingest.domain.model.vo.relay.RelayBatchResult;
import com.patra.ingest.domain.model.vo.relay.RelayPlan;
import com.patra.ingest.domain.port.OutboxRelayStore;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Outbox 中继执行器 - 通过专门的协调器编排中继批次执行
 *
 * <h3>职责</h3>
 *
 * <ul>
 *   <li>从持久化存储获取待处理消息
 *   <li>将租约获取委托给 {@link RelayLeaseCoordinator}
 *   <li>将发布和状态转换委托给 {@link RelayPublishCoordinator}
 *   <li>将中继日志记录委托给 {@link RelayLogCoordinator}
 *   <li>累积批次统计信息和领域事件
 * </ul>
 *
 * <h3>架构: 编排器 + 协调器模式</h3>
 *
 * <p>该执行器是一个精简的编排器 (~250 行),将所有关注点委托给专门的协调器:
 *
 * <ul>
 *   <li><strong>RelayLeaseCoordinator</strong>: 使用乐观锁的分布式租约获取
 *   <li><strong>RelayPublishCoordinator</strong>: 消息发布、错误分类、重试调度
 *   <li><strong>RelayLogCoordinator</strong>: 完整审计跟踪创建和批量持久化
 * </ul>
 *
 * <h3>执行流程</h3>
 *
 * <pre>
 * 1. 获取待处理消息 (遵守通道过滤和批大小)
 * 2. 创建中继批次 ID 和日志累加器
 * 3. 对于每条消息:
 *    a. 尝试获取租约 → 失败时记录 LEASE_MISSED
 *    b. 发布消息 → 处理 SUCCESS/DEFERRED/FAILED 结果
 *    c. 记录中继日志用于审计跟踪
 * 4. 批量持久化所有中继日志 (单条 INSERT 语句)
 * 5. 返回包含统计信息和领域事件的批次结果
 * </pre>
 *
 * <h3>领域事件</h3>
 *
 * <p>发出以下事件:
 *
 * <ul>
 *   <li>{@link OutboxLeaseMissedEvent}: 租约获取失败
 *   <li>{@link OutboxMessagePublishedEvent}: 消息成功发布
 *   <li>{@link OutboxMessageDeferredEvent}: 暂时性错误,已调度重试
 *   <li>{@link OutboxMessageFailedEvent}: 达到最大重试次数后永久失败
 * </ul>
 *
 * @author Papertrace Team
 * @since 2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayExecutor {

  private final OutboxRelayStore relayStore;
  private final RelayLeaseCoordinator leaseCoordinator;
  private final RelayPublishCoordinator publishCoordinator;
  private final RelayLogCoordinator logCoordinator;

  /**
   * 根据计划执行单批次中继
   *
   * <p>获取待处理消息,通过中继管道处理每条消息 (租约 → 发布 → 日志),并返回批次级别的统计信息
   *
   * @param plan 中继计划,指定通道、批大小、重试策略和租约参数
   * @return 包含计数和领域事件的批次结果
   */
  public RelayBatchResult execute(RelayPlan plan) {
    // 获取待处理消息 (null 通道表示所有通道)
    String channel = plan.channel() != null ? plan.channel().channel() : null;
    List<OutboxMessage> messages =
        relayStore.fetchPending(channel, plan.triggeredAt(), plan.batchSize());

    if (messages.isEmpty()) {
      if (log.isDebugEnabled()) {
        String channelDesc = channel != null ? channel : "ALL_CHANNELS";
        log.debug("通道 [{}] 没有待处理消息, 触发时间={}", channelDesc, plan.triggeredAt());
      }
      return RelayBatchResult.empty(plan.channel());
    }

    if (log.isDebugEnabled()) {
      String channelDesc = channel != null ? channel : "ALL_CHANNELS";
      log.debug(
          "处理通道 [{}] 的中继批次: {} 条消息, 租约持有者={}", channelDesc, messages.size(), plan.leaseOwner());
    }

    // 创建批次 ID 和日志累加器
    RelayBatchId batchId = RelayBatchId.generate(plan.triggeredAt());
    LogAccumulator logAcc = logCoordinator.createAccumulator(batchId);

    // 处理消息并累积统计信息
    RelayContext context = new RelayContext(plan);
    for (OutboxMessage message : messages) {
      processMessage(message, context, logAcc);
    }

    // 批量持久化所有中继日志 (单条 INSERT,N 行数据)
    logCoordinator.persistBatch(logAcc);

    return context.toBatchResult(messages.size());
  }

  /**
   * 通过中继管道处理单条消息: 租约 → 发布 → 日志
   *
   * @param message 要中继的 Outbox 消息
   * @param context 用于统计信息和事件的批次上下文
   * @param logAcc 用于审计跟踪的日志累加器
   */
  private void processMessage(OutboxMessage message, RelayContext context, LogAccumulator logAcc) {
    Instant startTime = Instant.now();

    // 尝试获取租约
    if (!leaseCoordinator.tryAcquire(message, context.plan())) {
      context.onLeaseMissed(message);
      logAcc.recordLeaseMissed(message, context.plan().leaseOwner(), startTime);
      return;
    }

    // ✅ 修复: 成功获取租约后同步版本
    // acquireLease() 在数据库中递增版本 (version+1),必须同步以避免乐观锁失败
    message = message.toBuilder().version(message.getVersion() + 1).build();

    // 发布消息并处理结果
    RelayResult result = publishCoordinator.publish(message, context.plan());

    if (result.isSuccess()) {
      context.onPublished(message, result.attemptNumber());
      logAcc.recordPublished(message, context.plan().leaseOwner(), startTime, Instant.now());
    } else if (result.isDeferred()) {
      context.onDeferred(message, result);
      logAcc.recordDeferred(
          message,
          context.plan().leaseOwner(),
          startTime,
          result.nextRetryAt(),
          result.errorCode(),
          result.errorMessage(),
          "TRANSIENT"); // Deferred 总是表示暂时性错误
    } else {
      context.onFailed(message, result);
      logAcc.recordFailed(
          message,
          context.plan().leaseOwner(),
          startTime,
          result.errorCode(),
          result.errorMessage(),
          "FATAL"); // Failed 可能是 FATAL 或达到最大重试 (视为 FATAL)
    }
  }

  /**
   * 用于在中继执行期间累积统计信息和领域事件的批次上下文
   *
   * <p>线程安全性: 非线程安全,在单线程中继作业执行中使用
   */
  private static final class RelayContext {
    private final RelayPlan plan;
    private final List<OutboxRelayDomainEvent> events = new ArrayList<>();
    private int published;
    private int retried;
    private int failed;
    private int leaseMissed;

    private RelayContext(RelayPlan plan) {
      this.plan = plan;
    }

    private RelayPlan plan() {
      return plan;
    }

    private void onLeaseMissed(OutboxMessage message) {
      leaseMissed++;
      events.add(
          new OutboxLeaseMissedEvent(
              message.getId(),
              message.getChannel(),
              plan.leaseOwner(),
              message.getLeaseOwner(),
              plan.triggeredAt()));
    }

    private void onPublished(OutboxMessage message, int attemptNumber) {
      published++;
      events.add(
          new OutboxMessagePublishedEvent(
              message.getId(),
              message.getChannel(),
              message.getPartitionKey(),
              plan.triggeredAt()));
    }

    private void onDeferred(OutboxMessage message, RelayResult result) {
      retried++;
      events.add(
          new OutboxMessageDeferredEvent(
              message.getId(),
              message.getChannel(),
              result.attemptNumber(),
              result.nextRetryAt(),
              result.errorCode(),
              result.errorMessage(),
              plan.triggeredAt()));
    }

    private void onFailed(OutboxMessage message, RelayResult result) {
      failed++;
      events.add(
          new OutboxMessageFailedEvent(
              message.getId(),
              message.getChannel(),
              result.attemptNumber(),
              result.errorCode(),
              result.errorMessage(),
              plan.triggeredAt()));
    }

    private RelayBatchResult toBatchResult(int totalMessages) {
      if (log.isDebugEnabled()) {
        String channelDesc = plan.channel() != null ? plan.channel().channel() : "ALL_CHANNELS";
        log.debug(
            "通道 [{}] 的中继批次完成: 处理了 {} 条消息 ({} 已发布, {} 已重试, {} 失败, {} 租约丢失)",
            channelDesc,
            totalMessages,
            published,
            retried,
            failed,
            leaseMissed);
      }

      return new RelayBatchResult(
          plan.channel(), totalMessages, published, retried, failed, leaseMissed, events);
    }
  }
}
