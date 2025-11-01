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
 * Outbox relay executor - orchestrates relay batch execution via specialized coordinators.
 *
 * <h3>Responsibilities</h3>
 *
 * <ul>
 *   <li>Fetch pending messages from persistence store
 *   <li>Delegate lease acquisition to {@link RelayLeaseCoordinator}
 *   <li>Delegate publishing and state transitions to {@link RelayPublishCoordinator}
 *   <li>Delegate relay logging to {@link RelayLogCoordinator}
 *   <li>Accumulate batch statistics and domain events
 * </ul>
 *
 * <h3>Architecture: Orchestrator + Coordinators Pattern</h3>
 *
 * <p>This executor is a slim orchestrator (~250 lines) that delegates all concerns to specialized
 * coordinators:
 *
 * <ul>
 *   <li><strong>RelayLeaseCoordinator</strong>: Distributed lease acquisition with optimistic
 *       locking
 *   <li><strong>RelayPublishCoordinator</strong>: Message publishing, error classification, retry
 *       scheduling
 *   <li><strong>RelayLogCoordinator</strong>: Complete audit trail creation and batch persistence
 * </ul>
 *
 * <h3>Execution Flow</h3>
 *
 * <pre>
 * 1. Fetch pending messages (respecting channel filter and batch size)
 * 2. Create relay batch ID and log accumulator
 * 3. For each message:
 *    a. Try acquire lease → record LEASE_MISSED if failed
 *    b. Publish message → handle SUCCESS/DEFERRED/FAILED outcome
 *    c. Record relay log for audit trail
 * 4. Batch-persist all relay logs (single INSERT statement)
 * 5. Return batch result with statistics and domain events
 * </pre>
 *
 * <h3>Domain Events</h3>
 *
 * <p>Emits events for:
 *
 * <ul>
 *   <li>{@link OutboxLeaseMissedEvent}: Lease acquisition failed
 *   <li>{@link OutboxMessagePublishedEvent}: Message published successfully
 *   <li>{@link OutboxMessageDeferredEvent}: Transient error, scheduled for retry
 *   <li>{@link OutboxMessageFailedEvent}: Permanent failure after max retries
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
   * Executes a single relay batch according to the plan.
   *
   * <p>Fetches pending messages, processes each through the relay pipeline (lease → publish → log),
   * and returns batch-level statistics.
   *
   * @param plan relay plan specifying channel, batch size, retry policy, and lease parameters
   * @return batch result with counts and domain events
   */
  public RelayBatchResult execute(RelayPlan plan) {
    // Fetch pending messages (null channel means all channels)
    String channel = plan.channel() != null ? plan.channel().channel() : null;
    List<OutboxMessage> messages =
        relayStore.fetchPending(channel, plan.triggeredAt(), plan.batchSize());

    if (messages.isEmpty()) {
      if (log.isDebugEnabled()) {
        String channelDesc = channel != null ? channel : "ALL_CHANNELS";
        log.debug(
            "No pending messages for channel [{}], triggeredAt={}",
            channelDesc,
            plan.triggeredAt());
      }
      return RelayBatchResult.empty(plan.channel());
    }

    if (log.isDebugEnabled()) {
      String channelDesc = channel != null ? channel : "ALL_CHANNELS";
      log.debug(
          "Processing relay batch for channel [{}]: {} messages, leaseOwner={}",
          channelDesc,
          messages.size(),
          plan.leaseOwner());
    }

    // Create batch ID and log accumulator
    RelayBatchId batchId = RelayBatchId.generate(plan.triggeredAt());
    LogAccumulator logAcc = logCoordinator.createAccumulator(batchId);

    // Process messages and accumulate statistics
    RelayContext context = new RelayContext(plan);
    for (OutboxMessage message : messages) {
      processMessage(message, context, logAcc);
    }

    // Persist all relay logs in batch (single INSERT with N rows)
    logCoordinator.persistBatch(logAcc);

    return context.toBatchResult(messages.size());
  }

  /**
   * Processes a single message through the relay pipeline: lease → publish → log.
   *
   * @param message outbox message to relay
   * @param context batch context for statistics and events
   * @param logAcc log accumulator for audit trail
   */
  private void processMessage(OutboxMessage message, RelayContext context, LogAccumulator logAcc) {
    Instant startTime = Instant.now();

    // Attempt lease acquisition
    if (!leaseCoordinator.tryAcquire(message, context.plan())) {
      context.onLeaseMissed(message);
      logAcc.recordLeaseMissed(message, context.plan().leaseOwner(), startTime);
      return;
    }

    // Publish message and handle result
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
          "TRANSIENT"); // Deferred always means transient error
    } else {
      context.onFailed(message, result);
      logAcc.recordFailed(
          message,
          context.plan().leaseOwner(),
          startTime,
          result.errorCode(),
          result.errorMessage(),
          "FATAL"); // Failed can be FATAL or max retries (treat as FATAL)
    }
  }

  /**
   * Batch context for accumulating statistics and domain events during relay execution.
   *
   * <p>Thread-safety: NOT thread-safe, used within single-threaded relay job execution.
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
            "Relay batch completed for channel [{}]: {} messages processed ({} published, {} retried, {} failed, {} leaseMissed)",
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
