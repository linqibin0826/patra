package com.patra.ingest.app.outbox.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.app.outbox.config.OutboxPublisherProperties;
import com.patra.ingest.app.outbox.constants.OutboxAggregateTypes;
import com.patra.ingest.app.outbox.constants.OutboxBusinessTags;
import com.patra.ingest.app.outbox.constants.OutboxChannels;
import com.patra.ingest.app.outbox.metrics.OutboxMetrics;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.outbox.OutboxHeaders;
import com.patra.ingest.domain.outbox.OutboxPayload;
import com.patra.ingest.domain.port.OutboxMessageRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Abstract base class for Outbox message publishing.
 * <p>Provides template methods for batch publishing and retry logic,
 * with hook points for subclasses to customize payload/headers/partition strategies.</p>
 *
 * <h3>Extension Points (Abstract Methods)</h3>
 * <ul>
 *   <li><b>Must Implement</b>:
 *     <ul>
 *       <li>{@link #getAggregateType()} - Returns {@link OutboxAggregateTypes} enum</li>
 *       <li>{@link #getChannel()} - Returns {@link OutboxChannels} enum</li>
 *       <li>{@link #buildPayload} - Constructs business payload JSON</li>
 *       <li>{@link #buildHeaders} - Constructs message headers JSON</li>
 *       <li>{@link #buildPartitionKey} - Defines partition strategy</li>
 *       <li>{@link #buildDedupKey} - Defines idempotency key</li>
 *       <li>{@link #getOperationType} - Returns {@link OutboxBusinessTags} enum</li>
 *       <li>{@link #getAggregateId} - Extracts aggregate ID from event</li>
 *     </ul>
 *   </li>
 *   <li><b>Optional Override</b> (with default behavior):
 *     <ul>
 *       <li>{@link #validateEvent} - Event validation (default: non-null check)</li>
 *       <li>{@link #resolveNotBefore} - Deferred publishing logic (default: Instant.now())</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <h3>Usage Example</h3>
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
 *     protected OutboxChannels getChannel() {
 *         return OutboxChannels.INGEST_TASK_READY;
 *     }
 *
 *     @Override
 *     protected OutboxBusinessTags getOperationType(TaskQueuedEvent event) {
 *         return OutboxBusinessTags.TASK_READY;
 *     }
 *
 *     @Override
 *     protected TaskPayload buildPayload(TaskQueuedEvent event, OutboxPublishContext ctx) {
 *         PlanAggregate plan = ctx.get("plan", PlanAggregate.class);
 *         return new TaskPayload(
 *             event.taskId(),
 *             event.planId(),
 *             event.provenanceCode(),
 *             // ... other fields
 *         );
 *     }
 *
 *     // ... other extension point implementations
 * }
 * }</pre>
 *
 * @param <E> Domain event type
 * @param <P> Outbox payload type (must implement OutboxPayload)
 * @param <H> Outbox headers type (must implement OutboxHeaders)
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

    // ==================== Template Methods (Public API) ====================

    /**
     * Publishes events as Outbox messages (batch insert with transaction).
     * <p>Workflow:</p>
     * <ol>
     *   <li>Validate and filter events</li>
     *   <li>Build OutboxMessage instances for each valid event</li>
     *   <li>Batch insert partitioned by batch-size configuration</li>
     *   <li>Record metrics and structured logs</li>
     * </ol>
     *
     * @param events List of domain events to publish
     * @param ctx    Publishing context (aggregates, metadata, traceId, etc.)
     * @return Publishing result with success/failure counts and duration
     */
    @Transactional(rollbackFor = Exception.class)
    public OutboxPublishResult publish(List<E> events, OutboxPublishContext ctx) {
        Instant startTime = Instant.now();
        String aggregateType = getAggregateType().getCode();

        try {
            // Validate and filter events
            List<E> validEvents = filterValidEvents(events, aggregateType);
            if (validEvents.isEmpty()) {
                return OutboxPublishResult.empty(calculateDuration(startTime));
            }

            // Build Outbox messages and collect failures
            MessagesWithFailures result = buildOutboxMessages(validEvents, ctx, aggregateType);

            // Batch save messages
            saveBatchMessages(result.messages(), aggregateType);

            // Record metrics and return result
            Duration duration = calculateDuration(startTime);
            metrics.recordPublish(aggregateType, "batch", true, duration);

            log.info("Published {} Outbox messages for aggregateType={}, duration={}ms",
                    result.messages().size(), aggregateType, duration.toMillis());

            return buildPublishResult(result, duration);

        } catch (Exception e) {
            return handlePublishFailure(e, aggregateType, startTime);
        }
    }

    /**
     * Retry-publishes events using UPSERT strategy (idempotent batch operation).
     * <p>Workflow:</p>
     * <ol>
     *   <li>Validate batch size (≤ maxBatchSize)</li>
     *   <li>Build OutboxMessage instances with refreshed payload/headers</li>
     *   <li>UPSERT batch: insert new or update existing (by channel + dedupKey)</li>
     *   <li>Record metrics and structured logs</li>
     * </ol>
     * <p>This method is thread-safe and prevents race conditions in concurrent retry scenarios.</p>
     *
     * @param events List of domain events to retry
     * @param ctx    Publishing context
     * @return Publishing result
     */
    @Transactional(rollbackFor = Exception.class)
    public OutboxPublishResult publishRetry(List<E> events, OutboxPublishContext ctx) {
        Instant startTime = Instant.now();
        String aggregateType = getAggregateType().getCode();

        try {
            // Validate batch size constraints
            validateRetryBatchSize(events.size());

            // Validate and filter events
            List<E> validEvents = filterValidEvents(events, aggregateType);
            if (validEvents.isEmpty()) {
                return OutboxPublishResult.empty(calculateDuration(startTime));
            }

            // Build messages and collect failures
            MessagesWithFailures result = buildOutboxMessages(validEvents, ctx, aggregateType);

            // UPSERT batch (idempotent insert/update)
            if (!result.messages().isEmpty()) {
                repository.upsertBatch(result.messages());
            }

            // Record metrics and return result
            Duration duration = calculateDuration(startTime);
            metrics.recordPublish(aggregateType, "retry", true, duration);

            log.info("Retry-published {} Outbox messages for aggregateType={}, duration={}ms",
                    result.messages().size(), aggregateType, duration.toMillis());

            return buildPublishResult(result, duration);

        } catch (Exception e) {
            return handleRetryFailure(e, aggregateType, startTime);
        }
    }

    // ==================== Abstract Methods (Extension Points) ====================

    /**
     * Returns the aggregate type enum.
     * <p>Used for metrics tagging and database partitioning.</p>
     *
     * @return Aggregate type from {@link OutboxAggregateTypes}
     */
    protected abstract OutboxAggregateTypes getAggregateType();

    /**
     * Returns the messaging channel enum.
     * <p>Used for routing and deduplication scoping (unique constraint: channel + dedupKey).</p>
     *
     * @return Channel from {@link OutboxChannels}
     */
    protected abstract OutboxChannels getChannel();

    /**
     * Builds the message payload from the event.
     * <p>Payload contains business data required by downstream consumers.</p>
     *
     * @param event Domain event
     * @param ctx   Publishing context (aggregates, metadata)
     * @return Strongly-typed payload object implementing OutboxPayload
     */
    protected abstract P buildPayload(E event, OutboxPublishContext ctx);

    /**
     * Builds the message headers (metadata).
     * <p>Headers typically include traceId, eventType, timestamp, etc.</p>
     *
     * @param event Domain event
     * @param ctx   Publishing context
     * @return Strongly-typed headers object implementing OutboxHeaders
     */
    protected abstract H buildHeaders(E event, OutboxPublishContext ctx);

    /**
     * Builds the partition key for message ordering.
     * <p>Messages with the same partition key are guaranteed to be processed in order.</p>
     *
     * @param event Domain event
     * @param ctx   Publishing context
     * @return Partition key string (e.g., taskId, literatureId)
     */
    protected abstract String buildPartitionKey(E event, OutboxPublishContext ctx);

    /**
     * Builds the deduplication key for idempotency.
     * <p>Format: typically {channel}:{aggregateId}:{opType}</p>
     *
     * @param event Domain event
     * @param ctx   Publishing context
     * @return Dedup key string
     */
    protected abstract String buildDedupKey(E event, OutboxPublishContext ctx);

    /**
     * Returns the business semantic tag for the event.
     * <p>Represents "what happened" from a business perspective (e.g., TASK_READY, PLAN_CREATED),
     * NOT generic CRUD operations.</p>
     *
     * @param event Domain event
     * @return Business tag from {@link OutboxBusinessTags}
     */
    protected abstract OutboxBusinessTags getOperationType(E event);

    /**
     * Extracts aggregate ID from the event.
     *
     * @param event Domain event
     * @return Aggregate ID as Long
     */
    protected abstract Long getAggregateId(E event);

    // ==================== Hook Methods (Optional Overrides) ====================

    /**
     * Validates whether the event should be published.
     * <p>Default implementation checks non-null. Subclasses can override for custom validation.</p>
     *
     * @param event Domain event
     * @return true if valid, false to skip
     */
    protected boolean validateEvent(E event) {
        return event != null;
    }

    /**
     * Resolves the earliest publishable time (for deferred publishing).
     * <p>Default implementation returns Instant.now(). Override to support scheduled publishing.</p>
     *
     * @param event Domain event
     * @param ctx   Publishing context
     * @return Not-before timestamp
     */
    protected Instant resolveNotBefore(E event, OutboxPublishContext ctx) {
        return Instant.now();
    }

    // ==================== Private Helper Methods ====================

    /**
     * Creates an OutboxMessage from the event and context.
     */
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
                    .channel(getChannel().getCode())
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
            String errorMsg = String.format(
                    "Failed to create OutboxMessage for event type %s, aggregateId=%s, dedupKey=%s",
                    event.getClass().getSimpleName(),
                    getAggregateId(event),
                    buildDedupKey(event, ctx));
            throw new IllegalStateException(errorMsg, e);
        }
    }

    /**
     * Partitions a list into sublists of specified size.
     */
    private <T> List<List<T>> partition(List<T> list, int size) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            partitions.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return partitions;
    }

    /**
     * Filters valid events from the input list.
     *
     * @param events        Events to filter
     * @param aggregateType Aggregate type for logging
     * @return List of valid events
     */
    private List<E> filterValidEvents(List<E> events, String aggregateType) {
        List<E> validEvents = events.stream()
                .filter(this::validateEvent)
                .collect(Collectors.toList());

        if (validEvents.isEmpty()) {
            log.warn("No valid events to publish for aggregateType={}", aggregateType);
        }

        return validEvents;
    }

    /**
     * Builds OutboxMessage instances from valid events and collects failures.
     *
     * @param validEvents   Valid events to convert
     * @param ctx           Publishing context
     * @param aggregateType Aggregate type for error logging
     * @return Messages with failures
     */
    private MessagesWithFailures buildOutboxMessages(List<E> validEvents, OutboxPublishContext ctx, String aggregateType) {
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
     * Handles failure during OutboxMessage construction.
     *
     * @param event         Failed event
     * @param exception     Exception thrown
     * @param ctx           Publishing context
     * @param aggregateType Aggregate type for metrics
     * @param failures      Failure collection
     */
    private void handleBuildFailure(E event, Exception exception, OutboxPublishContext ctx,
                                     String aggregateType, List<OutboxPublishResult.FailureDetail> failures) {
        log.error("Failed to build OutboxMessage for event={}, error={}",
                event, exception.getMessage(), exception);

        String dedupKey = buildDedupKey(event, ctx);
        failures.add(new OutboxPublishResult.FailureDetail(
                null, dedupKey, exception.getMessage(), "BUILD_ERROR"));

        metrics.recordPublish(aggregateType, getOperationType(event).getCode(), false, Duration.ZERO);
    }

    /**
     * Saves messages in batches and records metrics.
     *
     * @param messages      Messages to save
     * @param aggregateType Aggregate type for metrics
     */
    private void saveBatchMessages(List<OutboxMessage> messages, String aggregateType) {
        int batchSize = properties.getBatchSize();
        List<List<OutboxMessage>> batches = partition(messages, batchSize);

        for (List<OutboxMessage> batch : batches) {
            repository.saveAll(batch);
            metrics.recordBatchSize(aggregateType, batch.size());
        }
    }

    /**
     * Builds the final publish result based on messages and failures.
     *
     * @param result   Messages with failures
     * @param duration Operation duration
     * @return Publish result
     */
    private OutboxPublishResult buildPublishResult(MessagesWithFailures result, Duration duration) {
        if (!result.failures().isEmpty()) {
            return OutboxPublishResult.partial(result.messages().size(), result.failures(), duration);
        }
        return OutboxPublishResult.success(result.messages().size(), duration);
    }

    /**
     * Handles publish failure and returns failure result.
     *
     * @param exception     Exception thrown
     * @param aggregateType Aggregate type for logging
     * @param startTime     Operation start time
     * @return Failure result
     */
    private OutboxPublishResult handlePublishFailure(Exception exception, String aggregateType, Instant startTime) {
        Duration duration = calculateDuration(startTime);
        metrics.recordPublish(aggregateType, "batch", false, duration);

        log.error("Failed to publish Outbox messages for aggregateType={}, error={}",
                aggregateType, exception.getMessage(), exception);

        return OutboxPublishResult.failure(exception.getMessage(), duration);
    }

    /**
     * Calculates duration from start time to now.
     *
     * @param startTime Start time
     * @return Duration
     */
    private Duration calculateDuration(Instant startTime) {
        return Duration.between(startTime, Instant.now());
    }

    /**
     * Validates retry batch size against configuration limit.
     *
     * @param batchSize Current batch size
     * @throws IllegalArgumentException if batch size exceeds maximum
     */
    private void validateRetryBatchSize(int batchSize) {
        int maxBatchSize = properties.getMaxBatchSize();
        if (batchSize > maxBatchSize) {
            throw new IllegalArgumentException(
                    String.format("Retry batch size %d exceeds max %d. " +
                            "Consider splitting the batch or increasing papertrace.outbox.publisher.max-batch-size.",
                            batchSize, maxBatchSize));
        }
    }

    /**
     * Handles retry-publish failure and returns failure result.
     *
     * @param exception     Exception thrown
     * @param aggregateType Aggregate type for logging
     * @param startTime     Operation start time
     * @return Failure result
     */
    private OutboxPublishResult handleRetryFailure(Exception exception, String aggregateType, Instant startTime) {
        Duration duration = calculateDuration(startTime);
        metrics.recordPublish(aggregateType, "retry", false, duration);

        log.error("Failed to retry-publish Outbox messages for aggregateType={}, error={}",
                aggregateType, exception.getMessage(), exception);

        return OutboxPublishResult.failure(exception.getMessage(), duration);
    }

    /**
     * Internal record for holding messages and failures.
     *
     * @param messages List of successfully built messages
     * @param failures List of failure details
     */
    private record MessagesWithFailures(
            List<OutboxMessage> messages,
            List<OutboxPublishResult.FailureDetail> failures
    ) {
    }
}
