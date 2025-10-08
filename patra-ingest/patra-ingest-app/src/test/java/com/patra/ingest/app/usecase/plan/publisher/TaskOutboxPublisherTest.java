package com.patra.ingest.app.usecase.plan.publisher;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.patra.ingest.app.outbox.config.OutboxPublisherProperties;
import com.patra.ingest.app.outbox.constants.OutboxChannels;
import com.patra.ingest.app.outbox.core.OutboxPublishContext;
import com.patra.ingest.app.outbox.core.OutboxPublishResult;
import com.patra.ingest.app.outbox.metrics.OutboxMetrics;
import com.patra.ingest.domain.event.TaskQueuedEvent;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.domain.model.enums.Scheduler;
import com.patra.ingest.domain.model.enums.TriggerType;
import com.patra.ingest.domain.port.OutboxMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link TaskOutboxPublisher}.
 * <p>Tests business-specific extension point implementations and adapter methods.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TaskOutboxPublisher unit tests")
class TaskOutboxPublisherTest {

    @Mock
    private OutboxMessageRepository repository;

    @Mock
    private OutboxMetrics metrics;

    private OutboxPublisherProperties properties;

    private TaskOutboxPublisher publisher;

    private ObjectMapper objectMapper;

    @Captor
    private ArgumentCaptor<List<OutboxMessage>> messagesCaptor;

    private PlanAggregate plan;
    private ScheduleInstanceAggregate schedule;

    @BeforeEach
    void setUp() {
        properties = new OutboxPublisherProperties();
        properties.setBatchSize(100);
        properties.setMaxBatchSize(500);

        // Use real ObjectMapper for testing JSON serialization
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        // Disable writing dates as timestamps to get ISO-8601 strings
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Do not include null values in JSON output
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        publisher = new TaskOutboxPublisher(repository, metrics, properties, objectMapper);

        // Create test plan aggregate with ID using restore method
        plan = PlanAggregate.restore(
                1L,  // plan ID
                100L,  // schedule instance ID
                "test-plan",
                "PUBMED",
                "HARVEST",
                "expr-hash",
                "{}",
                "{}",
                "provenance-hash",
                Instant.parse("2024-01-01T00:00:00Z"),
                Instant.parse("2024-12-31T23:59:59Z"),
                "DAILY",
                "{}",
                PlanStatus.READY,
                1L  // version
        );

        // Create test schedule aggregate with ID using restore method
        schedule = ScheduleInstanceAggregate.restore(
                100L,  // schedule instance ID
                Scheduler.XXL,
                "job-123",
                "log-456",
                TriggerType.SCHEDULE,
                Instant.parse("2024-01-15T10:00:00Z"),
                Map.of("param1", "value1"),
                ProvenanceCode.PUBMED,
                1L  // version
        );
    }

    // ==================== Adapter Methods Tests ====================

    @Test
    @DisplayName("should publish via adapter method and convert context correctly")
    void shouldPublishViaAdapterMethod() {
        // given
        List<TaskQueuedEvent> events = List.of(createTaskEvent(1L, "idempotent-1"));

        // when
        publisher.publish(events, plan, schedule);

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        List<OutboxMessage> messages = messagesCaptor.getValue();
        assertThat(messages).hasSize(1);

        OutboxMessage message = messages.get(0);
        assertThat(message.getAggregateType()).isEqualTo("Task");
        assertThat(message.getChannel()).isEqualTo(OutboxChannels.INGEST_TASK_READY.getCode());
    }

    @Test
    @DisplayName("should publishRetry via adapter method and use UPSERT")
    void shouldPublishRetryViaAdapterMethod() {
        // given
        List<TaskQueuedEvent> events = List.of(createTaskEvent(1L, "idempotent-1"));

        // when
        publisher.publishRetry(events, plan, schedule);

        // then
        verify(repository).upsertBatch(messagesCaptor.capture());
        List<OutboxMessage> messages = messagesCaptor.getValue();
        assertThat(messages).hasSize(1);
    }

    @Test
    @DisplayName("should throw NullPointerException when plan is null")
    void shouldThrowNullPointerException_whenPlanIsNull() {
        // given
        List<TaskQueuedEvent> events = List.of(createTaskEvent(1L, "idempotent-1"));

        // when & then
        assertThatThrownBy(() -> publisher.publish(events, null, schedule))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("plan must not be null");
    }

    @Test
    @DisplayName("should throw NullPointerException when schedule is null")
    void shouldThrowNullPointerException_whenScheduleIsNull() {
        // given
        List<TaskQueuedEvent> events = List.of(createTaskEvent(1L, "idempotent-1"));

        // when & then
        assertThatThrownBy(() -> publisher.publish(events, plan, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("schedule must not be null");
    }

    // ==================== Extension Point: buildPayload() ====================

    @Test
    @DisplayName("buildPayload should return TaskPayload with all required fields")
    void shouldBuildPayloadWithAllRequiredFields() throws Exception {
        // given
        TaskQueuedEvent event = createTaskEvent(123L, "idempotent-xyz");
        OutboxPublishContext ctx = OutboxPublishContext.builder()
                .put("plan", plan)
                .put("schedule", schedule)
                .build();

        // when
        publisher.publish(List.of(event), plan, schedule);

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        OutboxMessage message = messagesCaptor.getValue().get(0);

        // Verify the payload is serialized correctly
        JsonNode payload = objectMapper.readTree(message.getPayloadJson());
        assertThat(payload.get("taskId").asLong()).isEqualTo(123L);
        assertThat(payload.get("planId").asLong()).isEqualTo(1L);
        assertThat(payload.get("provenance").asText()).isEqualTo("PUBMED");
        assertThat(payload.get("operation").asText()).isEqualTo("HARVEST");
        assertThat(payload.get("idempotentKey").asText()).isEqualTo("idempotent-xyz");
    }

    @Test
    @DisplayName("should include params JSON in payload when present")
    void shouldIncludeParamsJsonInPayload_whenPresent() throws Exception {
        // given
        String paramsJson = "{\"maxResults\":100,\"pageSize\":50}";
        TaskQueuedEvent event = TaskQueuedEvent.of(
                1L, 1L, 2L, schedule.getId(),
                "PUBMED", "HARVEST", "idempotent-1",
                paramsJson, 10, Instant.now()
        );

        // when
        publisher.publish(List.of(event), plan, schedule);

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        OutboxMessage message = messagesCaptor.getValue().get(0);

        JsonNode payload = objectMapper.readTree(message.getPayloadJson());
        assertThat(payload.has("params")).isTrue();
        assertThat(payload.get("params").get("maxResults").asInt()).isEqualTo(100);
        assertThat(payload.get("params").get("pageSize").asInt()).isEqualTo(50);
    }

    @Test
    @DisplayName("should not include sliceId in payload when null")
    void shouldNotIncludeSliceIdInPayload_whenNull() throws Exception {
        // given
        TaskQueuedEvent event = TaskQueuedEvent.of(
                1L, 1L, null, schedule.getId(),
                "PUBMED", "HARVEST", "idempotent-1",
                null, null, null
        );

        // when
        publisher.publish(List.of(event), plan, schedule);

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        OutboxMessage message = messagesCaptor.getValue().get(0);

        JsonNode payload = objectMapper.readTree(message.getPayloadJson());
        assertThat(payload.has("sliceId")).isFalse();
    }

    @Test
    @DisplayName("should not include priority in payload when null")
    void shouldNotIncludePriorityInPayload_whenNull() throws Exception {
        // given
        TaskQueuedEvent event = createTaskEventWithPriority(1L, null);

        // when
        publisher.publish(List.of(event), plan, schedule);

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        OutboxMessage message = messagesCaptor.getValue().get(0);

        JsonNode payload = objectMapper.readTree(message.getPayloadJson());
        assertThat(payload.has("priority")).isFalse();
    }

    @Test
    @DisplayName("should include scheduledAt in payload when present")
    void shouldIncludeScheduledAtInPayload_whenPresent() throws Exception {
        // given
        Instant scheduledAt = Instant.parse("2024-02-01T12:00:00Z");
        TaskQueuedEvent event = createTaskEventWithScheduledAt(1L, scheduledAt);

        // when
        publisher.publish(List.of(event), plan, schedule);

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        OutboxMessage message = messagesCaptor.getValue().get(0);

        JsonNode payload = objectMapper.readTree(message.getPayloadJson());
        assertThat(payload.has("scheduledAt")).isTrue();
        assertThat(payload.get("scheduledAt").asText()).isEqualTo("2024-02-01T12:00:00Z");
    }

    @Test
    @DisplayName("buildPayload should parse paramsJson to JsonNode when present")
    void shouldParseParamsJsonToJsonNodeWhenPresent() throws Exception {
        // given
        String paramsJson = "{\"maxResults\":100,\"pageSize\":50}";
        TaskQueuedEvent event = TaskQueuedEvent.of(
                1L, 1L, 2L, schedule.getId(),
                "PUBMED", "HARVEST", "idempotent-1",
                paramsJson, 10, Instant.now()
        );

        // when
        publisher.publish(List.of(event), plan, schedule);

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        OutboxMessage message = messagesCaptor.getValue().get(0);

        JsonNode payload = objectMapper.readTree(message.getPayloadJson());
        assertThat(payload.has("params")).isTrue();
        JsonNode params = payload.get("params");
        assertThat(params.get("maxResults").asInt()).isEqualTo(100);
        assertThat(params.get("pageSize").asInt()).isEqualTo(50);
    }

    @Test
    @DisplayName("buildPayload should not include params when paramsJson is invalid")
    void shouldNotIncludeParamsWhenParamsJsonIsInvalid() throws Exception {
        // given
        String invalidJson = "{invalid}";
        TaskQueuedEvent event = TaskQueuedEvent.of(
                1L, 1L, 2L, schedule.getId(),
                "PUBMED", "HARVEST", "idempotent-1",
                invalidJson, 10, Instant.now()
        );

        // when
        publisher.publish(List.of(event), plan, schedule);

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        OutboxMessage message = messagesCaptor.getValue().get(0);

        JsonNode payload = objectMapper.readTree(message.getPayloadJson());
        assertThat(payload.has("params")).isFalse();
    }

    @Test
    @DisplayName("buildPayload should not include params when paramsJson is blank")
    void shouldNotIncludeParamsWhenParamsJsonIsBlank() throws Exception {
        // given
        TaskQueuedEvent event = TaskQueuedEvent.of(
                1L, 1L, 2L, schedule.getId(),
                "PUBMED", "HARVEST", "idempotent-1",
                "   ", 10, Instant.now()
        );

        // when
        publisher.publish(List.of(event), plan, schedule);

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        OutboxMessage message = messagesCaptor.getValue().get(0);

        JsonNode payload = objectMapper.readTree(message.getPayloadJson());
        assertThat(payload.has("params")).isFalse();
    }

    // ==================== Extension Point: buildHeaders() ====================

    @Test
    @DisplayName("buildHeaders should return TaskHeaders with schedule tracing information")
    void shouldBuildHeadersWithScheduleTracingInfo() throws Exception {
        // given
        TaskQueuedEvent event = createTaskEvent(1L, "idempotent-1");

        // when
        publisher.publish(List.of(event), plan, schedule);

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        OutboxMessage message = messagesCaptor.getValue().get(0);

        // Verify the headers are serialized correctly
        JsonNode headers = objectMapper.readTree(message.getHeadersJson());
        assertThat(headers.get("scheduleInstanceId").asLong()).isEqualTo(schedule.getId());
        assertThat(headers.get("scheduler").asText()).isEqualTo("XXL");
        assertThat(headers.get("schedulerJobId").asText()).isEqualTo("job-123");
        assertThat(headers.get("triggeredAt").asText()).isEqualTo("2024-01-15T10:00:00Z");
        assertThat(headers.has("occurredAt")).isTrue();
    }

    @Test
    @DisplayName("should not include schedulerJobId in headers when null")
    void shouldNotIncludeSchedulerJobIdInHeaders_whenNull() throws Exception {
        // given
        ScheduleInstanceAggregate scheduleWithoutJobId = ScheduleInstanceAggregate.start(
                Scheduler.XXL, null, "log-456",
                TriggerType.MANUAL, Instant.now(),
                Map.of(), ProvenanceCode.PUBMED
        );
        TaskQueuedEvent event = createTaskEvent(1L, "idempotent-1");

        // when
        publisher.publish(List.of(event), plan, scheduleWithoutJobId);

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        OutboxMessage message = messagesCaptor.getValue().get(0);

        JsonNode headers = objectMapper.readTree(message.getHeadersJson());
        assertThat(headers.has("schedulerJobId")).isFalse();
    }

    // ==================== Extension Point: buildPartitionKey() ====================

    @Test
    @DisplayName("should build partition key as provenance:operation")
    void shouldBuildPartitionKeyAsProvenanceOperation() {
        // given
        TaskQueuedEvent event = createTaskEvent(1L, "idempotent-1");

        // when
        publisher.publish(List.of(event), plan, schedule);

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        OutboxMessage message = messagesCaptor.getValue().get(0);

        assertThat(message.getPartitionKey()).isEqualTo("PUBMED:HARVEST");
    }

    @Test
    @DisplayName("should use operation as partition key when provenance is empty")
    void shouldUseOperationAsPartitionKey_whenProvenanceIsEmpty() {
        // given
        TaskQueuedEvent event = TaskQueuedEvent.of(
                1L, 1L, null, schedule.getId(),
                "", "HARVEST", "idempotent-1",
                null, null, null
        );

        // when
        publisher.publish(List.of(event), plan, schedule);

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        OutboxMessage message = messagesCaptor.getValue().get(0);

        assertThat(message.getPartitionKey()).isEqualTo("HARVEST");
    }

    @Test
    @DisplayName("should use provenance as partition key when operation is empty")
    void shouldUseProvenanceAsPartitionKey_whenOperationIsEmpty() {
        // given
        TaskQueuedEvent event = TaskQueuedEvent.of(
                1L, 1L, null, schedule.getId(),
                "PUBMED", "", "idempotent-1",
                null, null, null
        );

        // when
        publisher.publish(List.of(event), plan, schedule);

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        OutboxMessage message = messagesCaptor.getValue().get(0);

        assertThat(message.getPartitionKey()).isEqualTo("PUBMED");
    }

    @Test
    @DisplayName("should use TASK as partition key when both provenance and operation are empty")
    void shouldUseTaskAsPartitionKey_whenBothAreEmpty() {
        // given
        TaskQueuedEvent event = TaskQueuedEvent.of(
                1L, 1L, null, schedule.getId(),
                "", "", "idempotent-1",
                null, null, null
        );

        // when
        publisher.publish(List.of(event), plan, schedule);

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        OutboxMessage message = messagesCaptor.getValue().get(0);

        assertThat(message.getPartitionKey()).isEqualTo("TASK");
    }

    @Test
    @DisplayName("should handle null provenance and operation gracefully")
    void shouldHandleNullProvenanceAndOperation() {
        // given
        TaskQueuedEvent event = TaskQueuedEvent.of(
                1L, 1L, null, schedule.getId(),
                null, null, "idempotent-1",
                null, null, null
        );

        // when
        publisher.publish(List.of(event), plan, schedule);

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        OutboxMessage message = messagesCaptor.getValue().get(0);

        assertThat(message.getPartitionKey()).isEqualTo("TASK");
    }

    // ==================== Extension Point: buildDedupKey() ====================

    @Test
    @DisplayName("should use idempotentKey as dedupKey")
    void shouldUseIdempotentKeyAsDedupKey() {
        // given
        TaskQueuedEvent event = createTaskEvent(1L, "unique-idempotent-key-123");

        // when
        publisher.publish(List.of(event), plan, schedule);

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        OutboxMessage message = messagesCaptor.getValue().get(0);

        assertThat(message.getDedupKey()).isEqualTo("unique-idempotent-key-123");
    }

    // ==================== Extension Point: getOperationType() ====================

    @Test
    @DisplayName("should return TASK_READY as operation type")
    void shouldReturnTaskReadyAsOperationType() {
        // given
        TaskQueuedEvent event = createTaskEvent(1L, "idempotent-1");

        // when
        publisher.publish(List.of(event), plan, schedule);

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        OutboxMessage message = messagesCaptor.getValue().get(0);

        assertThat(message.getOpType()).isEqualTo("TASK_READY");
    }

    // ==================== Extension Point: getAggregateId() ====================

    @Test
    @DisplayName("should use taskId as aggregateId")
    void shouldUseTaskIdAsAggregateId() {
        // given
        TaskQueuedEvent event = createTaskEvent(999L, "idempotent-1");

        // when
        publisher.publish(List.of(event), plan, schedule);

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        OutboxMessage message = messagesCaptor.getValue().get(0);

        assertThat(message.getAggregateId()).isEqualTo(999L);
    }

    // ==================== Hook: validateEvent() ====================

    @Test
    @DisplayName("should skip events without taskId")
    void shouldSkipEvents_whenTaskIdIsNull() {
        // given
        List<TaskQueuedEvent> events = List.of(
                createTaskEvent(null, "idempotent-1"),  // Should be filtered
                createTaskEvent(1L, "idempotent-2")
        );

        // when
        publisher.publish(events, plan, schedule);

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        List<OutboxMessage> messages = messagesCaptor.getValue();
        assertThat(messages).hasSize(1);
        assertThat(messages.get(0).getAggregateId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("should skip null events")
    void shouldSkipNullEvents() {
        // given
        List<TaskQueuedEvent> events = new ArrayList<>();
        events.add(null);
        events.add(createTaskEvent(1L, "idempotent-1"));

        // when
        publisher.publish(events, plan, schedule);

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        List<OutboxMessage> messages = messagesCaptor.getValue();
        assertThat(messages).hasSize(1);
    }

    // ==================== Hook: resolveNotBefore() ====================

    @Test
    @DisplayName("should use scheduledAt as notBefore when present")
    void shouldUseScheduledAtAsNotBefore_whenPresent() {
        // given
        Instant scheduledAt = Instant.parse("2024-03-01T08:00:00Z");
        TaskQueuedEvent event = createTaskEventWithScheduledAt(1L, scheduledAt);

        // when
        publisher.publish(List.of(event), plan, schedule);

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        OutboxMessage message = messagesCaptor.getValue().get(0);

        assertThat(message.getNotBefore()).isEqualTo(scheduledAt);
    }

    @Test
    @DisplayName("should use current time as notBefore when scheduledAt is null")
    void shouldUseCurrentTimeAsNotBefore_whenScheduledAtIsNull() {
        // given
        TaskQueuedEvent event = createTaskEventWithScheduledAt(1L, null);
        Instant before = Instant.now();

        // when
        publisher.publish(List.of(event), plan, schedule);

        Instant after = Instant.now();

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        OutboxMessage message = messagesCaptor.getValue().get(0);

        assertThat(message.getNotBefore())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    // ==================== Helper Methods ====================

    private TaskQueuedEvent createTaskEvent(Long taskId, String idempotentKey) {
        return TaskQueuedEvent.of(
                taskId,
                plan.getId(),
                2L,
                schedule.getId(),
                "PUBMED",
                "HARVEST",
                idempotentKey,
                null,
                10,
                Instant.now()
        );
    }

    private TaskQueuedEvent createTaskEventWithPriority(Long taskId, Integer priority) {
        return TaskQueuedEvent.of(
                taskId,
                plan.getId(),
                null,
                schedule.getId(),
                "PUBMED",
                "HARVEST",
                "idempotent-" + taskId,
                null,
                priority,
                null
        );
    }

    private TaskQueuedEvent createTaskEventWithScheduledAt(Long taskId, Instant scheduledAt) {
        return TaskQueuedEvent.of(
                taskId,
                plan.getId(),
                null,
                schedule.getId(),
                "PUBMED",
                "HARVEST",
                "idempotent-" + taskId,
                null,
                10,
                scheduledAt
        );
    }
}
