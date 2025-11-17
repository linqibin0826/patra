package com.patra.ingest.app.outbox.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.app.outbox.config.OutboxPublisherProperties;
import com.patra.ingest.app.outbox.constants.OutboxAggregateTypes;
import com.patra.ingest.app.outbox.metrics.OutboxMetrics;
import com.patra.ingest.domain.messaging.IngestPublishingChannels;
import com.patra.ingest.domain.messaging.OperationType;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.outbox.OutboxHeaders;
import com.patra.ingest.domain.outbox.OutboxPayload;
import com.patra.ingest.domain.port.OutboxMessageRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

/**
 * Outbox 消息发布的抽象基类。
 *
 * <p>提供批量发布和重试逻辑的模板方法,并为子类提供钩子点以自定义负载/头部/分区策略。
 *
 * <h3>扩展点(抽象方法)</h3>
 *
 * <ul>
 *   <li><b>必须实现</b>:
 *       <ul>
 *         <li>{@link #getAggregateType()} - 返回 {@link OutboxAggregateTypes} 枚举
 *         <li>{@link #getChannel()} - 返回 {@link OutboxChannels} 枚举
 *         <li>{@link #buildPayload} - 构造业务负载 JSON
 *         <li>{@link #buildHeaders} - 构造消息头部 JSON
 *         <li>{@link #buildPartitionKey} - 定义分区策略
 *         <li>{@link #buildDedupKey} - 定义幂等键
 *         <li>{@link #getOperationType} - 返回 {@link OutboxBusinessTags} 枚举
 *         <li>{@link #getAggregateId} - 从事件中提取聚合 ID
 *       </ul>
 *   <li><b>可选覆盖</b> (具有默认行为):
 *       <ul>
 *         <li>{@link #validateEvent} - 事件验证(默认: 非空检查)
 *         <li>{@link #resolveNotBefore} - 延迟发布逻辑(默认: Instant.now())
 *       </ul>
 * </ul>
 *
 * <h3>使用示例</h3>
 *
 * <pre>{@code
 * @Component
 * public class TaskOutboxPublisher extends AbstractOutboxPublisher<TaskQueuedEvent, TaskPayload, TaskHeaders> {
 *
 *     @Override
 *     protected OutboxAggregateTypes getAggregateType() {
 *         return OutboxAggregateTypes.TASK;
 *     }
 *
 *     @Override
 *     protected IngestPublishingChannels getChannel() {
 *         return IngestPublishingChannels.TASK;
 *     }
 *
 *     @Override
 *     protected OperationType getOperationType(TaskQueuedEvent event) {
 *         return TaskOperations.READY;
 *     }
 *
 *     @Override
 *     protected TaskPayload buildPayload(TaskQueuedEvent event, OutboxPublishContext ctx) {
 *         PlanAggregate plan = ctx.get("plan", PlanAggregate.class);
 *         return new TaskPayload(
 *             event.taskId(),
 *             event.planId(),
 *             event.provenanceCode(),
 *             // ... 其他字段
 *         );
 *     }
 *
 *     // ... 其他扩展点实现
 * }
 * }</pre>
 *
 * @param <E> 领域事件类型
 * @param <P> Outbox 负载类型(必须实现 OutboxPayload)
 * @param <H> Outbox 头部类型(必须实现 OutboxHeaders)
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public abstract class AbstractOutboxPublisher<E, P extends OutboxPayload, H extends OutboxHeaders> {

  protected final OutboxMessageRepository repository;
  protected final OutboxMetrics metrics;
  protected final OutboxPublisherProperties properties;
  protected final ObjectMapper objectMapper;

  protected AbstractOutboxPublisher(
      OutboxMessageRepository repository,
      OutboxMetrics metrics,
      OutboxPublisherProperties properties,
      ObjectMapper objectMapper) {
    this.repository = repository;
    this.metrics = metrics;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  // ==================== 模板方法(公共 API) ====================

  /**
   * 将事件发布为 Outbox 消息(带事务的批量插入)。
   *
   * <p>工作流程:
   *
   * <ol>
   *   <li>验证和过滤事件
   *   <li>为每个有效事件构建 OutboxMessage 实例
   *   <li>按 batch-size 配置分区批量插入
   *   <li>记录指标和结构化日志
   * </ol>
   *
   * @param events 要发布的领域事件列表
   * @param ctx 发布上下文(聚合、元数据、traceId 等)
   * @return 包含成功/失败计数和持续时间的发布结果
   */
  @Transactional(rollbackFor = Exception.class)
  public OutboxPublishResult publish(List<E> events, OutboxPublishContext ctx) {
    return executePublish(events, ctx, "batch", this::saveBatchMessages);
  }

  /**
   * 使用 UPSERT 策略重试发布事件(幂等批量操作)。
   *
   * <p>工作流程:
   *
   * <ol>
   *   <li>验证批次大小(≤ maxBatchSize)
   *   <li>使用刷新的负载/头部构建 OutboxMessage 实例
   *   <li>UPSERT 批次: 插入新记录或更新现有记录(按 channel + dedupKey)
   *   <li>记录指标和结构化日志
   * </ol>
   *
   * <p>此方法是线程安全的,可防止并发重试场景中的竞态条件。
   *
   * @param events 要重试的领域事件列表
   * @param ctx 发布上下文
   * @return 发布结果
   */
  @Transactional(rollbackFor = Exception.class)
  public OutboxPublishResult publishRetry(List<E> events, OutboxPublishContext ctx) {
    validateRetryBatchSize(events.size());
    return executePublish(events, ctx, "retry", this::upsertMessages);
  }

  // ==================== 抽象方法(扩展点) ====================

  /**
   * 返回聚合类型枚举。
   *
   * <p>用于指标标记和数据库分区。
   *
   * @return 来自 {@link OutboxAggregateTypes} 的聚合类型
   */
  protected abstract OutboxAggregateTypes getAggregateType();

  /**
   * 返回消息通道枚举。
   *
   * <p>用于路由和去重作用域(唯一约束: channel + dedupKey)。
   *
   * @return 来自 {@link IngestPublishingChannels} 的通道
   */
  protected abstract IngestPublishingChannels getChannel();

  /**
   * 从事件构建消息负载。
   *
   * <p>负载包含下游消费者所需的业务数据。
   *
   * @param event 领域事件
   * @param ctx 发布上下文(聚合、元数据)
   * @return 实现 OutboxPayload 的强类型负载对象
   */
  protected abstract P buildPayload(E event, OutboxPublishContext ctx);

  /**
   * 构建消息头部(元数据)。
   *
   * <p>头部通常包括 traceId、eventType、timestamp 等。
   *
   * @param event 领域事件
   * @param ctx 发布上下文
   * @return 实现 OutboxHeaders 的强类型头部对象
   */
  protected abstract H buildHeaders(E event, OutboxPublishContext ctx);

  /**
   * 构建用于消息排序的分区键。
   *
   * <p>具有相同分区键的消息保证按顺序处理。
   *
   * @param event 领域事件
   * @param ctx 发布上下文
   * @return 分区键字符串(例如 taskId、publicationId)
   */
  protected abstract String buildPartitionKey(E event, OutboxPublishContext ctx);

  /**
   * 构建用于幂等性的去重键。
   *
   * <p>格式: 通常为 {channel}:{aggregateId}:{opType}
   *
   * @param event 领域事件
   * @param ctx 发布上下文
   * @return 去重键字符串
   */
  protected abstract String buildDedupKey(E event, OutboxPublishContext ctx);

  /**
   * 返回事件的操作类型。
   *
   * <p>从业务角度表示"发生了什么"(例如 TASK_READY、PUBLICATION_DATA_READY),而不是通用的 CRUD 操作。
   *
   * @param event 领域事件
   * @return 操作类型枚举（如 TaskOperations.READY、PublicationOperations.DATA_READY）
   */
  protected abstract OperationType getOperationType(E event);

  /**
   * 从事件中提取聚合 ID。
   *
   * @param event 领域事件
   * @return Long 类型的聚合 ID
   */
  protected abstract Long getAggregateId(E event);

  // ==================== 钩子方法(可选覆盖) ====================

  /**
   * 验证事件是否应该发布。
   *
   * <p>默认实现检查非空。子类可以覆盖以进行自定义验证。
   *
   * @param event 领域事件
   * @return 如果有效则返回 true,跳过则返回 false
   */
  protected boolean validateEvent(E event) {
    return event != null;
  }

  /**
   * 解析最早可发布时间(用于延迟发布)。
   *
   * <p>默认实现返回 Instant.now()。覆盖此方法以支持定时发布。
   *
   * @param event 领域事件
   * @param ctx 发布上下文
   * @return 最早发布时间戳
   */
  protected Instant resolveNotBefore(E event, OutboxPublishContext ctx) {
    return Instant.now();
  }

  // ==================== 私有辅助方法 ====================

  /**
   * 发布事件的模板方法。
   *
   * @param events 要发布的事件
   * @param ctx 发布上下文
   * @param operationType 操作类型,用于日志记录(batch 或 retry)
   * @param persistStrategy 持久化策略
   * @return 发布结果
   */
  private OutboxPublishResult executePublish(
      List<E> events,
      OutboxPublishContext ctx,
      String operationType,
      MessagePersistStrategy persistStrategy) {
    Instant startTime = Instant.now();
    String aggregateType = getAggregateType().getCode();

    try {
      List<E> validEvents = filterValidEvents(events, aggregateType);
      if (validEvents.isEmpty()) {
        return OutboxPublishResult.empty(calculateDuration(startTime));
      }

      MessagesWithFailures result = buildOutboxMessages(validEvents, ctx, aggregateType);
      persistStrategy.persist(result.messages(), aggregateType);

      Duration duration = calculateDuration(startTime);
      metrics.recordPublish(aggregateType, operationType, true, duration);
      logPublishSuccess(aggregateType, operationType, result.messages().size(), duration);

      return buildPublishResult(result, duration);

    } catch (Exception e) {
      return handlePublishError(e, aggregateType, operationType, startTime);
    }
  }

  /**
   * 记录成功的发布操作日志。
   *
   * @param aggregateType 聚合类型
   * @param operationType 操作类型
   * @param messageCount 发布的消息数量
   * @param duration 操作持续时间
   */
  private void logPublishSuccess(
      String aggregateType, String operationType, int messageCount, Duration duration) {
    String action = "retry".equals(operationType) ? "重试发布了" : "发布了";
    log.info(
        "{} {} 条 Outbox 消息,aggregateType={},耗时={}ms",
        action,
        messageCount,
        aggregateType,
        duration.toMillis());
  }

  /**
   * 处理发布错误并返回失败结果。
   *
   * @param exception 抛出的异常
   * @param aggregateType 聚合类型
   * @param operationType 操作类型
   * @param startTime 操作开始时间
   * @return 失败结果
   */
  private OutboxPublishResult handlePublishError(
      Exception exception, String aggregateType, String operationType, Instant startTime) {
    Duration duration = calculateDuration(startTime);
    metrics.recordPublish(aggregateType, operationType, false, duration);

    String action = "retry".equals(operationType) ? "重试发布" : "发布";
    log.error(
        "{}Outbox 消息失败,aggregateType={},错误={}",
        action,
        aggregateType,
        exception.getMessage(),
        exception);

    return OutboxPublishResult.failure(exception.getMessage(), duration);
  }

  /**
   * UPSERT 消息(幂等的插入/更新)。
   *
   * @param messages 要 upsert 的消息
   * @param aggregateType 聚合类型,用于日志记录
   */
  private void upsertMessages(List<OutboxMessage> messages, String aggregateType) {
    if (!messages.isEmpty()) {
      if (log.isDebugEnabled()) {
        log.debug(
            "正在 upsert {} 条 outbox 消息,aggregateType [{}] (重试操作)", messages.size(), aggregateType);
      }
      repository.upsertBatch(messages);
    }
  }

  /** 消息持久化策略的函数式接口。 */
  @FunctionalInterface
  private interface MessagePersistStrategy {
    void persist(List<OutboxMessage> messages, String aggregateType);
  }

  /** 从事件和上下文创建 OutboxMessage。 */
  private OutboxMessage createOutboxMessage(E event, OutboxPublishContext ctx) {
    try {
      P payload = buildPayload(event, ctx);
      H headers = buildHeaders(event, ctx);
      String partitionKey = buildPartitionKey(event, ctx);
      String dedupKey = buildDedupKey(event, ctx);

      String payloadJson = objectMapper.writeValueAsString(payload);
      String headersJson = objectMapper.writeValueAsString(headers);

      return OutboxMessage.builder()
          .aggregateType(getAggregateType().getCode())
          .aggregateId(getAggregateId(event))
          .channel(getChannel().channel())
          .opType(getOperationType(event).getCode())
          .partitionKey(partitionKey)
          .dedupKey(dedupKey)
          .payloadJson(payloadJson)
          .headersJson(headersJson)
          .notBefore(resolveNotBefore(event, ctx))
          .statusCode("PENDING")
          .retryCount(0)
          .build();
    } catch (Exception e) {
      String errorMsg =
          String.format(
              "为事件类型 %s 创建 OutboxMessage 失败,aggregateId=%s,dedupKey=%s",
              event.getClass().getSimpleName(), getAggregateId(event), buildDedupKey(event, ctx));
      throw new IllegalStateException(errorMsg, e);
    }
  }

  /** 将列表分区为指定大小的子列表。 */
  private <T> List<List<T>> partition(List<T> list, int size) {
    List<List<T>> partitions = new ArrayList<>();
    for (int i = 0; i < list.size(); i += size) {
      partitions.add(list.subList(i, Math.min(i + size, list.size())));
    }
    return partitions;
  }

  /**
   * 从输入列表中过滤有效事件。
   *
   * @param events 要过滤的事件
   * @param aggregateType 聚合类型,用于日志记录
   * @return 有效事件列表
   */
  private List<E> filterValidEvents(List<E> events, String aggregateType) {
    List<E> validEvents = events.stream().filter(this::validateEvent).collect(Collectors.toList());

    if (validEvents.isEmpty()) {
      log.warn("没有有效事件可发布,aggregateType={}", aggregateType);
    }

    return validEvents;
  }

  /**
   * 从有效事件构建 OutboxMessage 实例并收集失败信息。
   *
   * @param validEvents 要转换的有效事件
   * @param ctx 发布上下文
   * @param aggregateType 聚合类型,用于错误日志记录
   * @return 包含失败信息的消息
   */
  private MessagesWithFailures buildOutboxMessages(
      List<E> validEvents, OutboxPublishContext ctx, String aggregateType) {
    List<OutboxMessage> messages = new ArrayList<>(validEvents.size());
    List<OutboxPublishResult.FailureDetail> failures = new ArrayList<>();

    for (E event : validEvents) {
      try {
        OutboxMessage message = createOutboxMessage(event, ctx);
        messages.add(message);
      } catch (Exception e) {
        handleBuildFailure(event, e, ctx, aggregateType, failures);
      }
    }

    return new MessagesWithFailures(messages, failures);
  }

  /**
   * 处理 OutboxMessage 构建期间的失败。
   *
   * @param event 失败的事件
   * @param exception 抛出的异常
   * @param ctx 发布上下文
   * @param aggregateType 聚合类型,用于指标记录
   * @param failures 失败集合
   */
  private void handleBuildFailure(
      E event,
      Exception exception,
      OutboxPublishContext ctx,
      String aggregateType,
      List<OutboxPublishResult.FailureDetail> failures) {
    log.error("为事件构建 OutboxMessage 失败,event={},错误={}", event, exception.getMessage(), exception);

    String dedupKey = buildDedupKey(event, ctx);
    failures.add(
        new OutboxPublishResult.FailureDetail(
            null, dedupKey, exception.getMessage(), "BUILD_ERROR"));

    metrics.recordPublish(aggregateType, getOperationType(event).getCode(), false, Duration.ZERO);
  }

  /**
   * 批量保存消息并记录指标。
   *
   * @param messages 要保存的消息
   * @param aggregateType 聚合类型,用于指标记录
   */
  private void saveBatchMessages(List<OutboxMessage> messages, String aggregateType) {
    int batchSize = properties.getBatchSize();
    List<List<OutboxMessage>> batches = partition(messages, batchSize);

    if (log.isDebugEnabled()) {
      log.debug(
          "正在保存 {} 条 outbox 消息,分为 {} 个批次,aggregateType [{}],batchSize [{}]",
          messages.size(),
          batches.size(),
          aggregateType,
          batchSize);
    }

    for (List<OutboxMessage> batch : batches) {
      repository.saveAll(batch);
      metrics.recordBatchSize(aggregateType, batch.size());
    }
  }

  /**
   * 基于消息和失败信息构建最终发布结果。
   *
   * @param result 包含失败信息的消息
   * @param duration 操作持续时间
   * @return 发布结果
   */
  private OutboxPublishResult buildPublishResult(MessagesWithFailures result, Duration duration) {
    if (!result.failures().isEmpty()) {
      return OutboxPublishResult.partial(result.messages().size(), result.failures(), duration);
    }
    return OutboxPublishResult.success(result.messages().size(), duration);
  }

  /**
   * 计算从开始时间到现在的持续时间。
   *
   * @param startTime 开始时间
   * @return 持续时间
   */
  private Duration calculateDuration(Instant startTime) {
    return Duration.between(startTime, Instant.now());
  }

  /**
   * 根据配置限制验证重试批次大小。
   *
   * @param batchSize 当前批次大小
   * @throws IllegalArgumentException 如果批次大小超过最大值
   */
  private void validateRetryBatchSize(int batchSize) {
    int maxBatchSize = properties.getMaxBatchSize();
    if (batchSize > maxBatchSize) {
      throw new IllegalArgumentException(
          String.format(
              "重试批次大小 %d 超过最大值 %d。" + "请考虑拆分批次或增加 patra.outbox.publisher.max-batch-size 配置。",
              batchSize, maxBatchSize));
    }
  }

  /**
   * 用于保存消息和失败信息的内部记录。
   *
   * @param messages 成功构建的消息列表
   * @param failures 失败详情列表
   */
  private record MessagesWithFailures(
      List<OutboxMessage> messages, List<OutboxPublishResult.FailureDetail> failures) {}
}
