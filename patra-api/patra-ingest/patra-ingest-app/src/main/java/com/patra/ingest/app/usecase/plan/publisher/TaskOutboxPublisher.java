package com.patra.ingest.app.usecase.plan.publisher;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.app.outbox.config.OutboxPublisherProperties;
import com.patra.ingest.app.outbox.constants.OutboxAggregateTypes;
import com.patra.ingest.app.outbox.constants.OutboxBusinessTags;
import com.patra.ingest.app.outbox.constants.OutboxChannels;
import com.patra.ingest.app.outbox.core.AbstractOutboxPublisher;
import com.patra.ingest.app.outbox.core.OutboxPublishContext;
import com.patra.ingest.app.outbox.metrics.OutboxMetrics;
import com.patra.ingest.domain.event.TaskQueuedEvent;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.port.OutboxMessageRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Task Outbox 发布器(重构为使用统一框架)
 *
 * <p>将任务排队事件发布为 Outbox 消息,确保可靠的 MQ 投递。
 *
 * <h3>幂等性策略</h3>
 *
 * <ul>
 *   <li><b>首次发布</b>: 跳过没有 taskId 的事件(尚未持久化)
 *   <li><b>重试发布</b>: 使用 UPSERT 处理并发重试场景
 * </ul>
 *
 * <h3>分区策略</h3>
 *
 * <p>partitionKey = provenance:operation (降级为拼接)
 *
 * <h3>延迟发布</h3>
 *
 * <p>notBefore = scheduledAt (若为 null 则使用 Instant.now())
 *
 * <h3>适配器方法</h3>
 *
 * <p>提供向后兼容的适配器方法:
 *
 * <ul>
 *   <li>{@link #publish(List, PlanAggregate, ScheduleInstanceAggregate)}: 首次发布
 *   <li>{@link #publishRetry(List, PlanAggregate, ScheduleInstanceAggregate)}: 重试发布
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class TaskOutboxPublisher
    extends AbstractOutboxPublisher<TaskQueuedEvent, TaskPayload, TaskHeaders> {

  private final ObjectMapper objectMapper;

  public TaskOutboxPublisher(
      OutboxMessageRepository repository,
      OutboxMetrics metrics,
      OutboxPublisherProperties properties,
      ObjectMapper objectMapper) {
    super(repository, metrics, properties, objectMapper);
    this.objectMapper = objectMapper;
  }

  // ==================== 适配器方法(向后兼容) ====================

  /**
   * 首次发布(适配器方法,向后兼容)
   *
   * <p>将聚合转换为上下文并委托给框架。
   *
   * @param events 任务排队事件
   * @param plan Plan 聚合(不能为 null)
   * @param schedule 调度实例聚合(不能为 null)
   */
  public void publish(
      List<TaskQueuedEvent> events, PlanAggregate plan, ScheduleInstanceAggregate schedule) {
    Objects.requireNonNull(plan, "plan must not be null");
    Objects.requireNonNull(schedule, "schedule must not be null");

    log.debug(
        "正在发布 {} 个任务就绪事件到 Outbox,plan [{}], Provenance [{}] Operation [{}]",
        events.size(),
        plan.getId(),
        plan.getProvenanceCode(),
        plan.getOperationCode());

    OutboxPublishContext ctx =
        OutboxPublishContext.builder().put("plan", plan).put("schedule", schedule).build();

    super.publish(events, ctx);

    log.debug("已成功发布 {} 个任务就绪事件到 Outbox", events.size());
  }

  /**
   * 重试发布(适配器方法,向后兼容)
   *
   * <p>将聚合转换为上下文并委托给框架。
   *
   * @param events 任务排队事件
   * @param plan Plan 聚合(不能为 null)
   * @param schedule 调度实例聚合(不能为 null)
   */
  public void publishRetry(
      List<TaskQueuedEvent> events, PlanAggregate plan, ScheduleInstanceAggregate schedule) {
    Objects.requireNonNull(plan, "plan must not be null");
    Objects.requireNonNull(schedule, "schedule must not be null");

    log.debug(
        "正在发布 {} 个重试任务事件到 Outbox,plan [{}], Provenance [{}] Operation [{}]",
        events.size(),
        plan.getId(),
        plan.getProvenanceCode(),
        plan.getOperationCode());

    OutboxPublishContext ctx =
        OutboxPublishContext.builder().put("plan", plan).put("schedule", schedule).build();

    super.publishRetry(events, ctx);

    log.debug("已成功发布 {} 个重试任务事件到 Outbox", events.size());
  }

  // ==================== 扩展点实现 ====================

  @Override
  protected OutboxAggregateTypes getAggregateType() {
    return OutboxAggregateTypes.TASK;
  }

  @Override
  protected OutboxChannels getChannel() {
    return OutboxChannels.INGEST_TASK_READY;
  }

  @Override
  protected TaskPayload buildPayload(TaskQueuedEvent event, OutboxPublishContext ctx) {
    // 简化载荷: 仅包含 taskId 和 idempotentKey
    // 消费者将查询数据库获取所有其他数据(provenance, operation, params 等)
    return new TaskPayload(event.taskId(), event.idempotentKey());
  }

  @Override
  protected TaskHeaders buildHeaders(TaskQueuedEvent event, OutboxPublishContext ctx) {
    ScheduleInstanceAggregate schedule = ctx.get("schedule", ScheduleInstanceAggregate.class);

    return new TaskHeaders(
        schedule.getId(),
        schedule.getScheduler().name(),
        schedule.getSchedulerJobId(),
        schedule.getTriggeredAt(),
        event.occurredAt());
  }

  @Override
  protected String buildPartitionKey(TaskQueuedEvent event, OutboxPublishContext ctx) {
    // 确保相同 provenance + operation 在下游保持顺序
    String provenance = StrUtil.nullToEmpty(event.provenanceCode());
    String operation = StrUtil.nullToEmpty(event.operationCode());

    if (StrUtil.isEmpty(provenance) && StrUtil.isEmpty(operation)) {
      return "TASK";
    }
    if (StrUtil.isEmpty(provenance)) {
      return operation;
    }
    if (StrUtil.isEmpty(operation)) {
      return provenance;
    }
    return provenance + ':' + operation;
  }

  @Override
  protected String buildDedupKey(TaskQueuedEvent event, OutboxPublishContext ctx) {
    // 使用事件的幂等键进行去重
    return event.idempotentKey();
  }

  @Override
  protected OutboxBusinessTags getOperationType(TaskQueuedEvent event) {
    return OutboxBusinessTags.TASK_READY;
  }

  @Override
  protected Long getAggregateId(TaskQueuedEvent event) {
    return event.taskId();
  }

  // ==================== 可选钩子重写 ====================

  @Override
  protected boolean validateEvent(TaskQueuedEvent event) {
    // 跳过没有 taskId 的事件(任务未成功持久化)
    if (event == null || event.taskId() == null) {
      if (event != null) {
        log.warn("跳过未持久化的任务事件,planId={}", event.planId());
      }
      return false;
    }
    return true;
  }

  @Override
  protected Instant resolveNotBefore(TaskQueuedEvent event, OutboxPublishContext ctx) {
    // 若存在 scheduledAt 则使用,否则立即发布
    return event.scheduledAt() != null ? event.scheduledAt() : Instant.now();
  }
}
