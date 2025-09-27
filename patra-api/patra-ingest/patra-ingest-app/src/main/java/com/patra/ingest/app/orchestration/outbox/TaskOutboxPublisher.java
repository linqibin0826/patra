package com.patra.ingest.app.orchestration.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.ingest.app.outbox.support.OutboxChannels;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.enums.OutboxStatus;
import com.patra.ingest.domain.model.event.TaskQueuedEvent;
import com.patra.ingest.domain.port.OutboxMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 将任务入队事件转换为 Outbox 消息并持久化。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskOutboxPublisher {

    private static final String AGGREGATE_TYPE_TASK = "TASK";
    /** 默认通道，可进一步下沉到配置中心。 */
    private static final String DEFAULT_CHANNEL = OutboxChannels.INGEST_TASK_READY;
    /** 默认业务操作类型标识。 */
    private static final String DEFAULT_OP_TYPE = "TASK_READY";

    private final OutboxMessageRepository outboxMessageRepository;
    private final ObjectMapper objectMapper;

    public void publish(List<TaskQueuedEvent> events,
                        PlanAggregate plan,
                        ScheduleInstanceAggregate schedule) {
        if (events == null || events.isEmpty()) {
            return;
        }
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(schedule, "schedule must not be null");

        List<OutboxMessage> messages = new ArrayList<>(events.size());
        for (TaskQueuedEvent event : events) {
            if (event.taskId() == null) {
                log.warn("skip task event without persistence, planId={}", event.planId());
                continue;
            }
            ObjectNode payloadNode = buildPayload(event, plan);
            ObjectNode headersNode = buildHeaders(event, schedule, plan);
            OutboxMessage message = OutboxMessage.builder()
                    .aggregateType(AGGREGATE_TYPE_TASK)
                    .aggregateId(event.taskId())
                    .channel(DEFAULT_CHANNEL)
                    .opType(DEFAULT_OP_TYPE)
                    .partitionKey(buildPartitionKey(event))
                    .dedupKey(event.idempotentKey())
                    .payloadJson(writeJson(payloadNode))
                    .headersJson(writeJson(headersNode))
                    .notBefore(resolveNotBefore(event.scheduledAt()))
                    .retryCount(0)
                    .build();
            messages.add(message);
        }

        if (!messages.isEmpty()) {
            outboxMessageRepository.saveAll(messages);
        }
    }

    public void publishRetry(List<TaskQueuedEvent> events,
                             PlanAggregate plan,
                             ScheduleInstanceAggregate schedule) {
        if (events == null || events.isEmpty()) {
            return;
        }
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(schedule, "schedule must not be null");

        for (TaskQueuedEvent event : events) {
            ObjectNode payloadNode = buildPayload(event, plan);
            ObjectNode headersNode = buildHeaders(event, schedule, plan);
            String payloadJson = writeJson(payloadNode);
            String headersJson = writeJson(headersNode);
            Instant notBefore = resolveNotBefore(event.scheduledAt());

            Optional<OutboxMessage> existing = outboxMessageRepository.findByChannelAndDedup(DEFAULT_CHANNEL, event.idempotentKey());
            if (existing.isPresent()) {
                OutboxMessage refreshed = existing.get().toBuilder()
                        .statusCode(OutboxStatus.PENDING.name())
                        .retryCount(0)
                        .nextRetryAt(null)
                        .errorCode(null)
                        .errorMsg(null)
                        .payloadJson(payloadJson)
                        .headersJson(headersJson)
                        .notBefore(notBefore)
                        .leaseOwner(null)
                        .leaseExpireAt(null)
                        .msgId(null)
                        .build();
                outboxMessageRepository.saveOrUpdate(refreshed);
            } else {
                OutboxMessage message = OutboxMessage.builder()
                        .aggregateType(AGGREGATE_TYPE_TASK)
                        .aggregateId(event.taskId())
                        .channel(DEFAULT_CHANNEL)
                        .opType(DEFAULT_OP_TYPE)
                        .partitionKey(buildPartitionKey(event))
                        .dedupKey(event.idempotentKey())
                        .payloadJson(payloadJson)
                        .headersJson(headersJson)
                        .notBefore(notBefore)
                        .retryCount(0)
                        .build();
                outboxMessageRepository.saveOrUpdate(message);
            }
        }
    }

    private ObjectNode buildPayload(TaskQueuedEvent event, PlanAggregate plan) {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        payload.put("taskId", event.taskId());
        payload.put("planId", event.planId());
        if (event.sliceId() == null) {
            payload.putNull("sliceId");
        } else {
            payload.put("sliceId", event.sliceId());
        }
        payload.put("provenance", event.provenanceCode());
        payload.put("operation", event.operationCode());
        payload.put("idempotentKey", event.idempotentKey());
        if (event.priority() != null) {
            payload.put("priority", event.priority());
        } else {
            payload.putNull("priority");
        }
        if (event.scheduledAt() != null) {
            payload.put("scheduledAt", event.scheduledAt().toString());
        }
        if (event.paramsJson() != null) {
            payload.set("params", readJsonNode(event.paramsJson()));
        }
        payload.put("planKey", plan.getPlanKey());
        if (plan.getWindowFrom() != null) {
            payload.put("planWindowFrom", plan.getWindowFrom().toString());
        }
        if (plan.getWindowTo() != null) {
            payload.put("planWindowTo", plan.getWindowTo().toString());
        }
        payload.put("planSliceStrategy", plan.getSliceStrategyCode());
        if (plan.getSliceParamsJson() != null) {
            payload.put("planSliceParams", plan.getSliceParamsJson());
        }
        return payload;
    }

    private ObjectNode buildHeaders(TaskQueuedEvent event,
                                    ScheduleInstanceAggregate schedule,
                                    PlanAggregate plan) {
        ObjectNode headers = JsonNodeFactory.instance.objectNode();
        headers.put("scheduleInstanceId", schedule.getId());
        headers.put("scheduler", schedule.getScheduler().name());
        if (schedule.getSchedulerJobId() != null) {
            headers.put("schedulerJobId", schedule.getSchedulerJobId());
        }
        if (schedule.getSchedulerLogId() != null) {
            headers.put("schedulerLogId", schedule.getSchedulerLogId());
        }
        headers.put("triggerType", schedule.getTriggerType().name());
        headers.put("triggeredAt", schedule.getTriggeredAt().toString());
        headers.put("occurredAt", event.occurredAt().toString());
        headers.put("planKey", plan.getPlanKey());
        if (plan.getOperationCode() != null) {
            headers.put("planOperation", plan.getOperationCode());
        }
        if (plan.getEndpointName() != null) {
            headers.put("planEndpoint", plan.getEndpointName());
        }
        return headers;
    }

    private String writeJson(ObjectNode node) {
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize outbox message", e);
        }
    }

    private JsonNode readJsonNode(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to parse task params json", e);
        }
    }

    private Instant resolveNotBefore(Instant scheduledAt) {
        return scheduledAt == null ? Instant.now() : scheduledAt;
    }

    private String buildPartitionKey(TaskQueuedEvent event) {
        String provenance = event.provenanceCode() == null ? "" : event.provenanceCode();
        String operation = event.operationCode() == null ? "" : event.operationCode();
        if (provenance.isEmpty() && operation.isEmpty()) {
            return "TASK";
        }
        if (operation.isEmpty()) {
            return provenance;
        }
        if (provenance.isEmpty()) {
            return operation;
        }
        return provenance + ":" + operation;
    }
}
