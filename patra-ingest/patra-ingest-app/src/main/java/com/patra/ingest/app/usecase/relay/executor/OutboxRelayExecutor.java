package com.patra.ingest.app.usecase.relay.executor;

import cn.hutool.core.util.StrUtil;
import com.patra.ingest.domain.event.OutboxLeaseMissedEvent;
import com.patra.ingest.domain.event.OutboxMessageDeferredEvent;
import com.patra.ingest.domain.event.OutboxMessageFailedEvent;
import com.patra.ingest.domain.event.OutboxMessagePublishedEvent;
import com.patra.ingest.domain.event.OutboxRelayDomainEvent;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.vo.RelayBatchResult;
import com.patra.ingest.domain.model.vo.RelayPlan;
import com.patra.ingest.domain.policy.RelayErrorClassifier;
import com.patra.ingest.domain.policy.RelayErrorClassifier.RelayErrorKind;
import com.patra.ingest.domain.policy.RelayRetryPolicy;
import com.patra.ingest.domain.port.OutboxPublisherPort;
import com.patra.ingest.domain.port.OutboxRelayStore;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Outbox Relay executor: completes message retrieval, lease validation, publishing, and state
 * updates within a single trigger cycle.
 *
 * <p>Idempotency: relies on lease acquisition plus version increments; failures persist durable
 * error information to prevent reprocessing; deferred retries are controlled via {@code
 * nextRetryAt}.
 *
 * <p>Error classification: delegates to {@link RelayErrorClassifier} to distinguish FATAL and
 * TRANSIENT cases and route them to {@code markFailed} or {@code markDeferred}.
 *
 * <p>Logging strategy: DEBUG for diagnostics (opt-in), WARN for retryable failures, and ERROR for
 * permanent failures.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class OutboxRelayExecutor {

  private static final int ERROR_MSG_LIMIT = 512;

  private final OutboxRelayStore relayStore;
  private final OutboxPublisherPort publisherPort;
  private final RelayRetryPolicy retryPolicy;
  private final RelayErrorClassifier errorClassifier;

  public OutboxRelayExecutor(
      OutboxRelayStore relayStore,
      OutboxPublisherPort publisherPort,
      RelayRetryPolicy retryPolicy,
      RelayErrorClassifier errorClassifier) {
    this.relayStore = relayStore;
    this.publisherPort = publisherPort;
    this.retryPolicy = retryPolicy;
    this.errorClassifier = errorClassifier;
  }

  /**
   * Execute a single batch publication.
   *
   * <ol>
   *   <li>{@code fetchPending} fetches candidate messages according to the plan (single channel or
   *       all channels).
   *   <li>Attempt {@code acquireLease} per message (optimistic concurrency control).
   *   <li>Publish -> {@code markPublished}; on exception classify and invoke {@code markFailed} or
   *       {@code markDeferred}.
   *   <li>Accumulate domain events and batch statistics.
   * </ol>
   *
   * @param plan publication plan
   * @return batch-level metrics
   */
  public RelayBatchResult execute(RelayPlan plan) {
    // Fetch pending messages according to the plan, subject to lease and batch limits
    // When plan.channel() is null we fetch messages across every channel
    List<OutboxMessage> messages = fetchMessages(plan);
    if (messages.isEmpty()) {
      if (log.isDebugEnabled()) {
        String channelDesc = plan.channel() != null ? plan.channel().channel() : "ALL_CHANNELS";
        log.debug(
            "relay executor no-pending channel={} triggeredAt={}", channelDesc, plan.triggeredAt());
      }
      return RelayBatchResult.empty(plan.channel());
    }
    RelayContext context = new RelayContext(plan);
    for (OutboxMessage message : messages) {
      processMessage(message, context);
    }
    return context.toBatchResult(messages.size());
  }

  /**
   * Fetch pending messages from the persistence layer as instructed by the plan.
   *
   * @param plan relay plan with channel filter, availability window, and batch size
   * @return pending messages; empty collection indicates nothing eligible
   */
  private List<OutboxMessage> fetchMessages(RelayPlan plan) {
    // Null channel means all channels are eligible
    String channel = plan.channel() != null ? plan.channel().channel() : null;
    return relayStore.fetchPending(channel, plan.triggeredAt(), plan.batchSize());
  }

  /**
   * Execute the relay pipeline for a single message: lease acquisition, publish attempt, and state
   * update according to the outcome.
   *
   * @param message Outbox message to process
   * @param context batch context that accumulates statistics and domain events
   */
  private void processMessage(OutboxMessage message, RelayContext context) {
    if (!tryAcquireLease(message, context)) {
      return;
    }
    long publishingVersion = nextVersionOf(message);
    try {
      OutboxPublisherPort.PublishResult publishResult =
          publisherPort.publish(message, context.plan());
      relayStore.markPublished(message.getId(), publishingVersion, publishResult.messageId());
      context.onPublished(message, publishResult);
    } catch (Exception ex) {
      // Delegate exception classification to the failure decision module (retry vs fail)
      handleFailure(message, context, publishingVersion, ex);
    }
  }

  /**
   * Attempt to acquire a lease; record the miss and skip processing when another worker already
   * owns it.
   *
   * @param message message whose lease is requested
   * @param context batch context
   * @return {@code true} when the lease is acquired successfully
   */
  private boolean tryAcquireLease(OutboxMessage message, RelayContext context) {
    RelayPlan plan = context.plan();
    boolean leased =
        relayStore.acquireLease(
            message.getId(), message.getVersion(), plan.leaseOwner(), plan.leaseExpireAt());
    if (!leased) {
      context.onLeaseMissed(message);
    }
    return leased;
  }

  /**
   * Route failure handling based on the classified exception: permanent failures persist
   * immediately, while transient ones are rescheduled according to the retry policy.
   *
   * @param message current message
   * @param context batch context
   * @param publishingVersion version written alongside the status update
   * @param exception exception thrown during publish
   */
  private void handleFailure(
      OutboxMessage message, RelayContext context, long publishingVersion, Exception exception) {
    RelayPlan plan = context.plan();
    FailureDecision decision = decideFailure(message, plan, exception);
    if (decision.handling() == FailureHandling.FAIL) {
      relayStore.markFailed(
          message.getId(),
          publishingVersion,
          decision.nextRetry(),
          decision.errorCode(),
          decision.errorMessage());
      context.onFailed(
          message, decision.nextRetry(), decision.errorCode(), decision.errorMessage(), exception);
      return;
    }
    Duration delay = retryPolicy.computeDelay(decision.nextRetry());
    Instant nextRetryAt = plan.triggeredAt().plus(delay);
    relayStore.markDeferred(
        message.getId(),
        publishingVersion,
        decision.nextRetry(),
        nextRetryAt,
        decision.errorCode(),
        decision.errorMessage());
    context.onDeferred(
        message,
        decision.nextRetry(),
        nextRetryAt,
        decision.errorCode(),
        decision.errorMessage(),
        exception);
  }

  /**
   * Aggregate exception information, determine the handling strategy, and wrap the decision for the
   * main flow.
   */
  private FailureDecision decideFailure(
      OutboxMessage message, RelayPlan plan, Exception exception) {
    RelayErrorKind kind = errorClassifier.classify(exception);
    int nextRetry = nextRetryCount(message);
    FailureHandling handling = determineFailureHandling(plan, kind, nextRetry);
    String errorCode = errorCode(exception);
    String errorMessage = errorMessage(exception);
    return new FailureDecision(handling, nextRetry, errorCode, errorMessage);
  }

  /** Decide whether the failure should terminate processing or re-enter the retry flow. */
  private FailureHandling determineFailureHandling(
      RelayPlan plan, RelayErrorKind kind, int nextRetry) {
    if (kind == RelayErrorKind.FATAL) {
      return FailureHandling.FAIL;
    }
    if (nextRetry >= plan.maxAttempts()) {
      return FailureHandling.FAIL;
    }
    return FailureHandling.RETRY;
  }

  /** Calculate the next persisted version (treat {@code null} as zero). */
  private long nextVersionOf(OutboxMessage message) {
    long currentVersion = message.getVersion() == null ? 0L : message.getVersion();
    return currentVersion + 1;
  }

  /** Calculate the next retry count; later persisted as {@code retryCount}. */
  private int nextRetryCount(OutboxMessage message) {
    int currentRetry = message.getRetryCount() == null ? 0 : message.getRetryCount();
    return currentRetry + 1;
  }

  /** Use the exception type name as the error code field. */
  private String errorCode(Exception exception) {
    return exception.getClass().getSimpleName();
  }

  /** Trim the exception message to keep stored fields within length limits. */
  private String errorMessage(Exception exception) {
    String message = exception.getMessage();
    if (message == null) {
      return null;
    }
    return StrUtil.maxLength(message, ERROR_MSG_LIMIT);
  }

  /** Snapshot of the failure decision, carrying the handling mode and key metadata. */
  private record FailureDecision(
      FailureHandling handling, int nextRetry, String errorCode, String errorMessage) {}

  private enum FailureHandling {
    RETRY,
    FAIL
  }

  /**
   * Batch context: encapsulates statistics, domain events, and log output to keep the loop
   * readable.
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

    /** Expose the current plan to outer layers. */
    private RelayPlan plan() {
      return plan;
    }

    /** Lease acquisition failed: log and register the domain event. */
    private void onLeaseMissed(OutboxMessage message) {
      leaseMissed++;
      if (log.isDebugEnabled()) {
        log.debug(
            "relay lease-missed messageId={} channel={} existingLeaseOwner={}",
            message.getId(),
            message.getChannel(),
            message.getLeaseOwner());
      }
      events.add(
          new OutboxLeaseMissedEvent(
              message.getId(),
              message.getChannel(),
              plan.leaseOwner(),
              message.getLeaseOwner(),
              plan.triggeredAt()));
    }

    /** Publish succeeded: record the external message identifier and increment counters. */
    private void onPublished(
        OutboxMessage message, OutboxPublisherPort.PublishResult publishResult) {
      if (log.isDebugEnabled()) {
        log.debug(
            "relay published messageId={} channel={} externalMsgId={}",
            message.getId(),
            message.getChannel(),
            publishResult.messageId());
      }
      published++;
      events.add(
          new OutboxMessagePublishedEvent(
              message.getId(),
              message.getChannel(),
              message.getPartitionKey(),
              publishResult.messageId(),
              plan.triggeredAt()));
    }

    /** Permanent failure: emit an error log and register the domain event. */
    private void onFailed(
        OutboxMessage message,
        int nextRetry,
        String errorCode,
        String errorMessage,
        Exception exception) {
      failed++;
      log.error(
          "Relay publish failed permanently, messageId={} channel={} retryCount={} errorCode={}",
          message.getId(),
          message.getChannel(),
          nextRetry,
          errorCode,
          exception);
      events.add(
          new OutboxMessageFailedEvent(
              message.getId(),
              message.getChannel(),
              nextRetry,
              errorCode,
              errorMessage,
              plan.triggeredAt()));
    }

    /** Deferred retry: log at WARN level and register the retry schedule. */
    private void onDeferred(
        OutboxMessage message,
        int nextRetry,
        Instant nextRetryAt,
        String errorCode,
        String errorMessage,
        Exception exception) {
      retried++;
      log.warn(
          "Relay publish deferred, messageId={} channel={} retryCount={} nextRetryAt={} errorCode={}",
          message.getId(),
          message.getChannel(),
          nextRetry,
          nextRetryAt,
          errorCode,
          exception);
      events.add(
          new OutboxMessageDeferredEvent(
              message.getId(),
              message.getChannel(),
              nextRetry,
              nextRetryAt,
              errorCode,
              errorMessage,
              plan.triggeredAt()));
    }

    /** Assemble the final batch result with statistics and domain events. */
    private RelayBatchResult toBatchResult(int totalMessages) {
      return new RelayBatchResult(
          plan.channel(), totalMessages, published, retried, failed, leaseMissed, events);
    }
  }
}
