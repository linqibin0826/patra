package com.patra.ingest.app.outbox.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.app.outbox.config.OutboxPublisherProperties;
import com.patra.ingest.app.outbox.metrics.OutboxMetrics;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.outbox.OutboxHeaders;
import com.patra.ingest.domain.outbox.OutboxPayload;
import com.patra.ingest.domain.port.OutboxMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link AbstractOutboxPublisher}.
 * <p>Tests template method pattern, hook points, and error handling.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AbstractOutboxPublisher unit tests")
class AbstractOutboxPublisherTest {

    @Mock
    private OutboxMessageRepository repository;

    @Mock
    private OutboxMetrics metrics;

    @Mock
    private ObjectMapper objectMapper;

    private OutboxPublisherProperties properties;

    @Captor
    private ArgumentCaptor<List<OutboxMessage>> messagesCaptor;

    private TestOutboxPublisher publisher;

    private OutboxPublishContext context;

    @BeforeEach
    void setUp() throws Exception {
        properties = new OutboxPublisherProperties();
        properties.setBatchSize(100);
        properties.setMaxBatchSize(500);

        // Mock ObjectMapper to return valid JSON strings (lenient stubbing for tests that don't call serialization)
        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");

        publisher = new TestOutboxPublisher(repository, metrics, properties, objectMapper);

        context = OutboxPublishContext.builder()
                .put("testKey", "testValue")
                .build();
    }

    // ==================== publish() Tests ====================

    @Test
    @DisplayName("should publish successfully when events are valid")
    void shouldPublishSuccessfully_whenEventsAreValid() {
        // given
        List<TestEvent> events = List.of(
                new TestEvent(1L, "event-1"),
                new TestEvent(2L, "event-2")
        );

        // when
        OutboxPublishResult result = publisher.publish(events, context);

        // then
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailureCount()).isEqualTo(0);
        assertThat(result.hasFailures()).isFalse();

        verify(repository).saveAll(messagesCaptor.capture());
        List<OutboxMessage> savedMessages = messagesCaptor.getValue();
        assertThat(savedMessages).hasSize(2);
        assertThat(savedMessages.get(0).getAggregateId()).isEqualTo(1L);
        assertThat(savedMessages.get(1).getAggregateId()).isEqualTo(2L);

        verify(metrics).recordPublish(eq("TestAggregate"), eq("batch"), eq(true), any(Duration.class));
        verify(metrics).recordBatchSize("TestAggregate", 2);
    }

    @Test
    @DisplayName("should return empty result when event list is empty")
    void shouldReturnEmptyResult_whenEventListIsEmpty() {
        // when
        OutboxPublishResult result = publisher.publish(Collections.emptyList(), context);

        // then
        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.getFailureCount()).isEqualTo(0);
        assertThat(result.hasFailures()).isFalse();

        verify(repository, never()).saveAll(any());
        verify(metrics, never()).recordPublish(anyString(), anyString(), anyBoolean(), any(Duration.class));
    }

    @Test
    @DisplayName("should filter out invalid events using validateEvent()")
    void shouldFilterOutInvalidEvents() {
        // given
        publisher.shouldValidate = true;
        List<TestEvent> events = List.of(
                new TestEvent(null, "invalid-no-id"),  // Should be filtered
                new TestEvent(1L, "valid-event")
        );

        // when
        OutboxPublishResult result = publisher.publish(events, context);

        // then
        assertThat(result.getSuccessCount()).isEqualTo(1);
        verify(repository).saveAll(messagesCaptor.capture());
        assertThat(messagesCaptor.getValue()).hasSize(1);
        assertThat(messagesCaptor.getValue().get(0).getAggregateId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("should handle partial failures when some events fail to build")
    void shouldHandlePartialFailures_whenSomeEventsFail() {
        // given
        publisher.shouldThrowOnBuildPayload = true;
        List<TestEvent> events = List.of(
                new TestEvent(1L, "will-fail"),
                new TestEvent(2L, "will-succeed")
        );

        // when
        OutboxPublishResult result = publisher.publish(events, context);

        // then
        assertThat(result.getSuccessCount()).isEqualTo(1);
        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.hasFailures()).isTrue();
        assertThat(result.getFailures()).hasSize(1);
        assertThat(result.getFailures().get(0).errorType()).isEqualTo("BUILD_ERROR");

        verify(repository).saveAll(messagesCaptor.capture());
        assertThat(messagesCaptor.getValue()).hasSize(1);

        verify(metrics).recordPublish(eq("TestAggregate"), eq("TEST_OPERATION"), eq(false), any(Duration.class));
    }

    @Test
    @DisplayName("should return failure result when repository throws exception")
    void shouldReturnFailureResult_whenRepositoryFails() {
        // given
        List<TestEvent> events = List.of(new TestEvent(1L, "event-1"));
        doThrow(new RuntimeException("Database connection failed"))
                .when(repository).saveAll(any());

        // when
        OutboxPublishResult result = publisher.publish(events, context);

        // then
        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.hasFailures()).isTrue();
        assertThat(result.getFailures()).hasSize(1);
        assertThat(result.getFailures().get(0).errorMessage()).contains("Database connection failed");

        verify(metrics).recordPublish(eq("TestAggregate"), eq("batch"), eq(false), any(Duration.class));
    }

    @Test
    @DisplayName("should batch save messages according to batchSize configuration")
    void shouldBatchSaveMessages_accordingToConfiguration() {
        // given
        properties.setBatchSize(2);
        List<TestEvent> events = List.of(
                new TestEvent(1L, "event-1"),
                new TestEvent(2L, "event-2"),
                new TestEvent(3L, "event-3"),
                new TestEvent(4L, "event-4"),
                new TestEvent(5L, "event-5")
        );

        // when
        OutboxPublishResult result = publisher.publish(events, context);

        // then
        assertThat(result.getSuccessCount()).isEqualTo(5);

        // Should call saveAll 3 times (2, 2, 1)
        verify(repository, times(3)).saveAll(any());

        // Should record batch size metrics 3 times: twice with size=2, once with size=1
        verify(metrics, times(2)).recordBatchSize("TestAggregate", 2);
        verify(metrics).recordBatchSize("TestAggregate", 1);
    }

    @Test
    @DisplayName("should call resolveNotBefore() for each event")
    void shouldCallResolveNotBefore_forEachEvent() {
        // given
        Instant scheduledTime = Instant.parse("2024-01-01T12:00:00Z");
        publisher.notBeforeOverride = scheduledTime;
        List<TestEvent> events = List.of(new TestEvent(1L, "event-1"));

        // when
        publisher.publish(events, context);

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        OutboxMessage message = messagesCaptor.getValue().get(0);
        assertThat(message.getNotBefore()).isEqualTo(scheduledTime);
    }

    // ==================== publishRetry() Tests ====================

    @Test
    @DisplayName("should publishRetry successfully with UPSERT")
    void shouldPublishRetrySuccessfully() {
        // given
        List<TestEvent> events = List.of(
                new TestEvent(1L, "event-1"),
                new TestEvent(2L, "event-2")
        );

        // when
        OutboxPublishResult result = publisher.publishRetry(events, context);

        // then
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailureCount()).isEqualTo(0);

        verify(repository).upsertBatch(messagesCaptor.capture());
        assertThat(messagesCaptor.getValue()).hasSize(2);

        verify(metrics).recordPublish(eq("TestAggregate"), eq("retry"), eq(true), any(Duration.class));
    }

    @Test
    @DisplayName("should throw exception when retry batch size exceeds max")
    void shouldThrowException_whenRetryBatchSizeExceedsMax() {
        // given
        properties.setMaxBatchSize(2);
        List<TestEvent> events = List.of(
                new TestEvent(1L, "event-1"),
                new TestEvent(2L, "event-2"),
                new TestEvent(3L, "event-3")
        );

        // when
        OutboxPublishResult result = publisher.publishRetry(events, context);

        // then
        assertThat(result.hasFailures()).isTrue();
        assertThat(result.getFailures().get(0).errorMessage())
                .contains("Retry batch size 3 exceeds max 2");

        verify(repository, never()).upsertBatch(any());
        verify(metrics).recordPublish(eq("TestAggregate"), eq("retry"), eq(false), any(Duration.class));
    }

    @Test
    @DisplayName("should return empty result when retry event list is empty")
    void shouldReturnEmptyResult_whenRetryEventListIsEmpty() {
        // when
        OutboxPublishResult result = publisher.publishRetry(Collections.emptyList(), context);

        // then
        assertThat(result.getSuccessCount()).isEqualTo(0);
        assertThat(result.getFailureCount()).isEqualTo(0);

        verify(repository, never()).upsertBatch(any());
    }

    @Test
    @DisplayName("should filter invalid events in publishRetry")
    void shouldFilterInvalidEvents_inPublishRetry() {
        // given
        publisher.shouldValidate = true;
        List<TestEvent> events = List.of(
                new TestEvent(null, "invalid"),
                new TestEvent(1L, "valid")
        );

        // when
        OutboxPublishResult result = publisher.publishRetry(events, context);

        // then
        assertThat(result.getSuccessCount()).isEqualTo(1);
        verify(repository).upsertBatch(messagesCaptor.capture());
        assertThat(messagesCaptor.getValue()).hasSize(1);
    }

    @Test
    @DisplayName("should return failure when upsertBatch throws exception")
    void shouldReturnFailure_whenUpsertBatchFails() {
        // given
        List<TestEvent> events = List.of(new TestEvent(1L, "event-1"));
        doThrow(new RuntimeException("Upsert failed"))
                .when(repository).upsertBatch(any());

        // when
        OutboxPublishResult result = publisher.publishRetry(events, context);

        // then
        assertThat(result.hasFailures()).isTrue();
        assertThat(result.getFailures().get(0).errorMessage()).contains("Upsert failed");

        verify(metrics).recordPublish(eq("TestAggregate"), eq("retry"), eq(false), any(Duration.class));
    }

    // ==================== Extension Point Tests ====================

    @Test
    @DisplayName("should call all extension point methods when building message")
    void shouldCallAllExtensionPointMethods() {
        // given
        TestEvent event = new TestEvent(1L, "event-1");
        List<TestEvent> events = List.of(event);

        // when
        publisher.publish(events, context);

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        OutboxMessage message = messagesCaptor.getValue().get(0);

        // Verify all extension points were called correctly
        assertThat(message.getAggregateType()).isEqualTo("TestAggregate");
        assertThat(message.getChannel()).isEqualTo("test-channel");
        assertThat(message.getOpType()).isEqualTo("TEST_OPERATION");
        assertThat(message.getPartitionKey()).isEqualTo("partition-1");
        assertThat(message.getDedupKey()).isEqualTo("dedup-1");
        assertThat(message.getAggregateId()).isEqualTo(1L);
        assertThat(message.getPayloadJson()).isNotNull();
        assertThat(message.getHeadersJson()).isNotNull();
    }

    @Test
    @DisplayName("should use default validateEvent() when not overridden")
    void shouldUseDefaultValidateEvent() {
        // given
        List<TestEvent> events = new ArrayList<>();
        events.add(null);  // Should be filtered by default validation
        events.add(new TestEvent(1L, "valid"));

        // when
        OutboxPublishResult result = publisher.publish(events, context);

        // then
        assertThat(result.getSuccessCount()).isEqualTo(1);
        verify(repository).saveAll(messagesCaptor.capture());
        assertThat(messagesCaptor.getValue()).hasSize(1);
    }

    @Test
    @DisplayName("should use default resolveNotBefore() when not overridden")
    void shouldUseDefaultResolveNotBefore() {
        // given
        publisher.notBeforeOverride = null;  // Use default behavior
        List<TestEvent> events = List.of(new TestEvent(1L, "event-1"));

        Instant before = Instant.now();

        // when
        publisher.publish(events, context);

        Instant after = Instant.now();

        // then
        verify(repository).saveAll(messagesCaptor.capture());
        OutboxMessage message = messagesCaptor.getValue().get(0);
        assertThat(message.getNotBefore())
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    // ==================== Helper: Test Implementation ====================

    /**
     * Test payload implementing OutboxPayload.
     */
    record TestPayload(String eventId, String data) implements OutboxPayload {
    }

    /**
     * Test headers implementing OutboxHeaders.
     */
    record TestHeaders(String traceId, Instant timestamp) implements OutboxHeaders {
    }

    /**
     * Concrete test implementation of AbstractOutboxPublisher.
     */
    private static class TestOutboxPublisher extends AbstractOutboxPublisher<TestEvent, TestPayload, TestHeaders> {

        boolean shouldThrowOnBuildPayload = false;
        boolean shouldValidate = false;
        Instant notBeforeOverride = null;

        TestOutboxPublisher(OutboxMessageRepository repository,
                            OutboxMetrics metrics,
                            OutboxPublisherProperties properties,
                            ObjectMapper objectMapper) {
            super(repository, metrics, properties, objectMapper);
        }

        @Override
        protected String getAggregateType() {
            return "TestAggregate";
        }

        @Override
        protected String getChannel() {
            return "test-channel";
        }

        @Override
        protected TestPayload buildPayload(TestEvent event, OutboxPublishContext ctx) {
            if (shouldThrowOnBuildPayload && event.id != null && event.id == 1L) {
                throw new RuntimeException("Payload build failed");
            }
            return new TestPayload("event-" + event.id, event.name);
        }

        @Override
        protected TestHeaders buildHeaders(TestEvent event, OutboxPublishContext ctx) {
            return new TestHeaders("test-trace-id", Instant.now());
        }

        @Override
        protected String buildPartitionKey(TestEvent event, OutboxPublishContext ctx) {
            return "partition-" + event.id;
        }

        @Override
        protected String buildDedupKey(TestEvent event, OutboxPublishContext ctx) {
            return "dedup-" + event.id;
        }

        @Override
        protected String getOperationType(TestEvent event) {
            return "TEST_OPERATION";
        }

        @Override
        protected Long getAggregateId(TestEvent event) {
            return event.id;
        }

        @Override
        protected boolean validateEvent(TestEvent event) {
            if (!shouldValidate) {
                return super.validateEvent(event);
            }
            return event != null && event.id != null;
        }

        @Override
        protected Instant resolveNotBefore(TestEvent event, OutboxPublishContext ctx) {
            if (notBeforeOverride != null) {
                return notBeforeOverride;
            }
            return super.resolveNotBefore(event, ctx);
        }
    }

    /**
     * Test event record.
     */
    private record TestEvent(Long id, String name) {
    }
}
