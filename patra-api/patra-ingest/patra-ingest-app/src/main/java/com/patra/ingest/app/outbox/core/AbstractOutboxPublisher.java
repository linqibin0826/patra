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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

/**
 * Abstract base class for Outbox message publishing.
 *
 * <p>Provides template methods for batch publishing and retry logic, with hook points for
 * subclasses to customize payload/headers/partition strategies.
 *
 * <h3>Extension Points (Abstract Methods)</h3>
 *
 * <ul>
 *   <li><b>Must Implement</b>:
 *       <ul>
 *         <li>{@link #getAggregateType()} - Returns {@link OutboxAggregateTypes} enum
 *         <li>{@link #getChannel()} - Returns {@link OutboxChannels} enum
 *         <li>{@link #buildPayload} - Constructs business payload JSON
 *         <li>{@link #buildHeaders} - Constructs message headers JSON
 *         <li>{@link #buildPartitionKey} - Defines partition strategy
 *         <li>{@link #buildDedupKey} - Defines idempotency key
 *         <li>{@link #getOperationType} - Returns {@link OutboxBusinessTags} enum
 *         <li>{@link #getAggregateId} - Extracts aggregate ID from event
 *       </ul>
 *   <li><b>Optional Override</b> (with default behavior):
 *       <ul>
 *         <li>{@link #validateEvent} - Event validation (default: non-null check)
 *         <li>{@link #resolveNotBefore} - Deferred publishing logic (default: Instant.now())
 *       </ul>
 * </ul>
 *
 * <h3>Usage Example</h3>
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
   *
   * <p>Workflow:
   *
   * <ol>
   *   <li>Validate and filter events
   *   <li>Build OutboxMessage instances for each valid event
   *   <li>Batch insert partitioned by batch-size configuration
   *   <li>Record metrics and structured logs
   * </ol>
   *
   * @param events List of domain events to publish
   * @param ctx Publishing context (aggregates, metadata, traceId, etc.)
   * @return Publishing result with success/failure counts and duration
   */
  @Transactional(rollbackFor = Exception.class)
  public OutboxPublishResult publish(List<E> events, OutboxPublishContext ctx) {
    return executePublish(events, ctx, "batch", this::saveBatchMessages);
  }

  /**
   * Retry-publishes events using UPSERT strategy (idempotent batch operation).
   *
   * <p>Workflow:
   *
   * <ol>
   *   <li>Validate batch size (≤ maxBatchSize)
   *   <li>Build OutboxMessage instances with refreshed payload/headers
   *   <li>UPSERT batch: insert new or update existing (by channel + dedupKey)
   *   <li>Record metrics and structured logs
   * </ol>
   *
   * <p>This method is thread-safe and prevents race conditions in concurrent retry scenarios.
   *
   * @param events List of domain events to retry
   * @param ctx Publishing context
   * @return Publishing result
   */
  @Transactional(rollbackFor = Exception.class)
  public OutboxPublishResult publishRetry(List<E> events, OutboxPublishContext ctx) {
    validateRetryBatchSize(events.size());
    return executePublish(events, ctx, "retry", this::upsertMessages);
  }

  // ==================== Abstract Methods (Extension Points) ====================

  /**
   * Returns the aggregate type enum.
   *
   * <p>Used for metrics tagging and database partitioning.
   *
   * @return Aggregate type from {@link OutboxAggregateTypes}
   */
  protected abstract OutboxAggregateTypes getAggregateType();

  /**
   * Returns the messaging channel enum.
   *
   * <p>Used for routing and deduplication scoping (unique constraint: channel + dedupKey).
   *
   * @return Channel from {@link OutboxChannels}
   */
  protected abstract OutboxChannels getChannel();

  /**
   * Builds the message payload from the event.
   *
   * <p>Payload contains business data required by downstream consumers.
   *
   * @param event Domain event
   * @param ctx Publishing context (aggregates, metadata)
   * @return Strongly-typed payload object implementing OutboxPayload
   */
  protected abstract P buildPayload(E event, OutboxPublishContext ctx);

  /**
   * Builds the message headers (metadata).
   *
   * <p>Headers typically include traceId, eventType, timestamp, etc.
   *
   * @param event Domain event
   * @param ctx Publishing context
   * @return Strongly-typed headers object implementing OutboxHeaders
   */
  protected abstract H buildHeaders(E event, OutboxPublishContext ctx);

  /**
   * Builds the partition key for message ordering.
   *
   * <p>Messages with the same partition key are guaranteed to be processed in order.
   *
   * @param event Domain event
   * @param ctx Publishing context
   * @return Partition key string (e.g., taskId, literatureId)
   */
  protected abstract String buildPartitionKey(E event, OutboxPublishContext ctx);

  /**
   * Builds the deduplication key for idempotency.
   *
   * <p>Format: typically {channel}:{aggregateId}:{opType}
   *
   * @param event Domain event
   * @param ctx Publishing context
   * @return Dedup key string
   */
  protected abstract String buildDedupKey(E event, OutboxPublishContext ctx);

  /**
   * Returns the business semantic tag for the event.
   *
   * <p>Represents "what happened" from a business perspective (e.g., TASK_READY, PLAN_CREATED), NOT
   * generic CRUD operations.
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
   *
   * <p>Default implementation checks non-null. Subclasses can override for custom validation.
   *
   * @param event Domain event
   * @return true if valid, false to skip
   */
  protected boolean validateEvent(E event) {
    return event != null;
  }

  /**
   * Resolves the earliest publishable time (for deferred publishing).
   *
   * <p>Default implementation returns Instant.now(). Override to support scheduled publishing.
   *
   * @param event Domain event
   * @param ctx Publishing context
   * @return Not-before timestamp
   */
  protected Instant resolveNotBefore(E event, OutboxPublishContext ctx) {
    return Instant.now();
  }

  // ==================== Private Helper Methods ====================

  /**
   * Template method for publishing events.
   *
   * @param events events to publish
   * @param ctx publishing context
   * @param operationType operation type for logging (batch or retry)
   * @param persistStrategy persistence strategy
   * @return publish result
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
   * Logs successful publish operation.
   *
   * @param aggregateType aggregate type
   * @param operationType operation type
   * @param messageCount number of messages published
   * @param duration operation duration
   */
  private void logPublishSuccess(
      String aggregateType, String operationType, int messageCount, Duration duration) {
    String action = "retry".equals(operationType) ? "Retry-published" : "Published";
    log.info(
        "{} {} Outbox messages for aggregateType={}, duration={}ms",
        action,
        messageCount,
        aggregateType,
        duration.toMillis());
  }

  /**
   * Handles publish error and returns failure result.
   *
   * @param exception exception thrown
   * @param aggregateType aggregate type
   * @param operationType operation type
   * @param startTime operation start time
   * @return failure result
   */
  private OutboxPublishResult handlePublishError(
      Exception exception, String aggregateType, String operationType, Instant startTime) {
    Duration duration = calculateDuration(startTime);
    metrics.recordPublish(aggregateType, operationType, false, duration);

    String action = "retry".equals(operationType) ? "retry-publish" : "publish";
    log.error(
        "Failed to {} Outbox messages for aggregateType={}, error={}",
        action,
        aggregateType,
        exception.getMessage(),
        exception);

    return OutboxPublishResult.failure(exception.getMessage(), duration);
  }

  /**
   * Upserts messages (idempotent insert/update).
   *
   * @param messages messages to upsert
   * @param aggregateType aggregate type for logging
   */
  private void upsertMessages(List<OutboxMessage> messages, String aggregateType) {
    if (!messages.isEmpty()) {
      if (log.isDebugEnabled()) {
        log.debug(
            "Upserting {} outbox messages for aggregateType [{}] (retry operation)",
            messages.size(),
            aggregateType);
      }
      repository.upsertBatch(messages);
    }
  }

  /** Functional interface for message persistence strategies. */
  @FunctionalInterface
  private interface MessagePersistStrategy {
    void persist(List<OutboxMessage> messages, String aggregateType);
  }

  /** Creates an OutboxMessage from the event and context. */
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
      String errorMsg =
          String.format(
              "Failed to create OutboxMessage for event type %s, aggregateId=%s, dedupKey=%s",
              event.getClass().getSimpleName(), getAggregateId(event), buildDedupKey(event, ctx));
      throw new IllegalStateException(errorMsg, e);
    }
  }

  /** Partitions a list into sublists of specified size. */
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
   * @param events Events to filter
   * @param aggregateType Aggregate type for logging
   * @return List of valid events
   */
  private List<E> filterValidEvents(List<E> events, String aggregateType) {
    List<E> validEvents = events.stream().filter(this::validateEvent).collect(Collectors.toList());

    if (validEvents.isEmpty()) {
      log.warn("No valid events to publish for aggregateType={}", aggregateType);
    }

    return validEvents;
  }

  /**
   * Builds OutboxMessage instances from valid events and collects failures.
   *
   * @param validEvents Valid events to convert
   * @param ctx Publishing context
   * @param aggregateType Aggregate type for error logging
   * @return Messages with failures
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
   * Handles failure during OutboxMessage construction.
   *
   * @param event Failed event
   * @param exception Exception thrown
   * @param ctx Publishing context
   * @param aggregateType Aggregate type for metrics
   * @param failures Failure collection
   */
  private void handleBuildFailure(
      E event,
      Exception exception,
      OutboxPublishContext ctx,
      String aggregateType,
      List<OutboxPublishResult.FailureDetail> failures) {
    log.error(
        "Failed to build OutboxMessage for event={}, error={}",
        event,
        exception.getMessage(),
        exception);

    String dedupKey = buildDedupKey(event, ctx);
    failures.add(
        new OutboxPublishResult.FailureDetail(
            null, dedupKey, exception.getMessage(), "BUILD_ERROR"));

    metrics.recordPublish(aggregateType, getOperationType(event).getCode(), false, Duration.ZERO);
  }

  /**
   * Saves messages in batches and records metrics.
   *
   * @param messages Messages to save
   * @param aggregateType Aggregate type for metrics
   */
  private void saveBatchMessages(List<OutboxMessage> messages, String aggregateType) {
    int batchSize = properties.getBatchSize();
    List<List<OutboxMessage>> batches = partition(messages, batchSize);

    if (log.isDebugEnabled()) {
      log.debug(
          "Saving {} outbox messages in {} batches for aggregateType [{}], batchSize [{}]",
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
   * Builds the final publish result based on messages and failures.
   *
   * @param result Messages with failures
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
          String.format(
              "Retry batch size %d exceeds max %d. "
                  + "Consider splitting the batch or increasing papertrace.outbox.publisher.max-batch-size.",
              batchSize, maxBatchSize));
    }
  }

  /**
   * Internal record for holding messages and failures.
   *
   * @param messages List of successfully built messages
   * @param failures List of failure details
   */
  private record MessagesWithFailures(
      List<OutboxMessage> messages, List<OutboxPublishResult.FailureDetail> failures) {}
}
