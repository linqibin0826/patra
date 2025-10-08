package com.patra.ingest.app.usecase.plan.publisher;

import cn.hutool.core.text.CharSequenceUtil;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.app.outbox.config.OutboxPublisherProperties;
import com.patra.ingest.app.outbox.core.AbstractOutboxPublisher;
import com.patra.ingest.app.outbox.core.OutboxPublishContext;
import com.patra.ingest.app.outbox.metrics.OutboxMetrics;
import com.patra.ingest.app.usecase.relay.support.OutboxChannels;
import com.patra.ingest.domain.event.TaskQueuedEvent;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.port.OutboxMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Task Outbox publisher (refactored to use framework).
 * <p>Publishes task queued events as Outbox messages for reliable MQ delivery.</p>
 *
 * <h3>Idempotency Strategy</h3>
 * <ul>
 *   <li><b>First publish</b>: Skips events without taskId (not persisted yet)</li>
 *   <li><b>Retry publish</b>: Uses UPSERT to handle concurrent retry scenarios</li>
 * </ul>
 *
 * <h3>Partition Strategy</h3>
 * <p>partitionKey = provenance:operation (fallback to degraded concatenation)</p>
 *
 * <h3>Deferred Publishing</h3>
 * <p>notBefore = scheduledAt (or Instant.now() if null)</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class TaskOutboxPublisher extends AbstractOutboxPublisher<TaskQueuedEvent, TaskPayload, TaskHeaders> {

    private static final String AGGREGATE_TYPE_TASK = "Task";
    private static final String OUTBOX_CHANNEL = OutboxChannels.INGEST_TASK_READY;
    private static final String OPERATION_TYPE_TASK_READY = "TASK_READY";

    private final ObjectMapper objectMapper;

    public TaskOutboxPublisher(
            OutboxMessageRepository repository,
            OutboxMetrics metrics,
            OutboxPublisherProperties properties,
            ObjectMapper objectMapper) {
        super(repository, metrics, properties, objectMapper);
        this.objectMapper = objectMapper;
    }

    // ==================== Adapter Methods (Backward Compatibility) ====================

    /**
     * First-time publish (adapter method for backward compatibility).
     * <p>Converts aggregates to context and delegates to framework.</p>
     *
     * @param events   Task queued events
     * @param plan     Plan aggregate (must not be null)
     * @param schedule Schedule instance aggregate (must not be null)
     */
    public void publish(List<TaskQueuedEvent> events,
                        PlanAggregate plan,
                        ScheduleInstanceAggregate schedule) {
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(schedule, "schedule must not be null");

        OutboxPublishContext ctx = OutboxPublishContext.builder()
                .put("plan", plan)
                .put("schedule", schedule)
                .build();

        super.publish(events, ctx);
    }

    /**
     * Retry publish (adapter method for backward compatibility).
     * <p>Converts aggregates to context and delegates to framework.</p>
     *
     * @param events   Task queued events
     * @param plan     Plan aggregate (must not be null)
     * @param schedule Schedule instance aggregate (must not be null)
     */
    public void publishRetry(List<TaskQueuedEvent> events,
                             PlanAggregate plan,
                             ScheduleInstanceAggregate schedule) {
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(schedule, "schedule must not be null");

        OutboxPublishContext ctx = OutboxPublishContext.builder()
                .put("plan", plan)
                .put("schedule", schedule)
                .build();

        super.publishRetry(events, ctx);
    }

    // ==================== Extension Point Implementations ====================

    @Override
    protected String getAggregateType() {
        return AGGREGATE_TYPE_TASK;
    }

    @Override
    protected String getChannel() {
        return OUTBOX_CHANNEL;
    }

    @Override
    protected TaskPayload buildPayload(TaskQueuedEvent event, OutboxPublishContext ctx) {
        PlanAggregate plan = ctx.get("plan", PlanAggregate.class);

        // Parse params JSON to JsonNode if present
        JsonNode paramsNode = null;
        if (CharSequenceUtil.isNotBlank(event.paramsJson())) {
            try {
                paramsNode = objectMapper.readTree(event.paramsJson());
            } catch (Exception e) {
                log.warn("[INGEST][APP] Failed to parse params JSON for taskId={}, will set to null",
                        event.taskId(), e);
            }
        }

        return new TaskPayload(
                event.taskId(),
                event.planId(),
                event.sliceId(),
                event.provenanceCode(),
                event.operationCode(),
                event.idempotentKey(),
                event.priority(),
                event.scheduledAt(),
                paramsNode
        );
    }

    @Override
    protected TaskHeaders buildHeaders(TaskQueuedEvent event, OutboxPublishContext ctx) {
        ScheduleInstanceAggregate schedule = ctx.get("schedule", ScheduleInstanceAggregate.class);

        return new TaskHeaders(
                schedule.getId(),
                schedule.getScheduler().name(),
                schedule.getSchedulerJobId(),
                schedule.getTriggeredAt(),
                event.occurredAt()
        );
    }

    @Override
    protected String buildPartitionKey(TaskQueuedEvent event, OutboxPublishContext ctx) {
        // Ensure same provenance + operation maintains order downstream
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
        // Use event's idempotent key for deduplication
        return event.idempotentKey();
    }

    @Override
    protected String getOperationType(TaskQueuedEvent event) {
        return OPERATION_TYPE_TASK_READY;
    }

    @Override
    protected Long getAggregateId(TaskQueuedEvent event) {
        return event.taskId();
    }

    // ==================== Optional Hook Overrides ====================

    @Override
    protected boolean validateEvent(TaskQueuedEvent event) {
        // Skip events without taskId (task not persisted successfully)
        if (event == null || event.taskId() == null) {
            if (event != null) {
                log.warn("[INGEST][APP] Skip task event without persistence, planId={}", event.planId());
            }
            return false;
        }
        return true;
    }

    @Override
    protected Instant resolveNotBefore(TaskQueuedEvent event, OutboxPublishContext ctx) {
        // Use scheduledAt if present, otherwise immediate
        return event.scheduledAt() != null ? event.scheduledAt() : Instant.now();
    }
}
