package com.patra.ingest.app.usecase.relay.coordinator;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.vo.relay.RelayPlan;
import com.patra.ingest.domain.port.OutboxRelayStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Relay lease coordinator - manages distributed lease acquisition for outbox messages.
 *
 * <h3>Responsibilities</h3>
 *
 * <ul>
 *   <li>Attempt lease acquisition for outbox messages using optimistic locking
 *   <li>Prevent concurrent relay processing across multiple instances
 *   <li>Track lease acquisition success/failure for monitoring
 * </ul>
 *
 * <h3>Concurrency Control</h3>
 *
 * <p>Uses database-level optimistic locking via version field:
 *
 * <ul>
 *   <li>UPDATE succeeds (affectedRows=1) → Lease acquired successfully
 *   <li>UPDATE fails (affectedRows=0) → Another instance acquired the lease
 * </ul>
 *
 * <h3>Logging Strategy</h3>
 *
 * <ul>
 *   <li>DEBUG: Lease acquisition details (messageId, leaseOwner, result)
 *   <li>No INFO/WARN logging (high-frequency operation, avoid log noise)
 * </ul>
 *
 * @author Papertrace Team
 * @since 2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelayLeaseCoordinator {

  private final OutboxRelayStore relayStore;

  /**
   * Attempts to acquire lease for a single outbox message.
   *
   * <p>Lease acquisition ensures only one instance can relay this message within the lease window.
   *
   * <p>Implementation: Updates database row with:
   *
   * <ul>
   *   <li>status_code: 'PENDING' → 'PUBLISHING'
   *   <li>pub_lease_owner: set to current instance identifier
   *   <li>pub_leased_until: set to lease expiration time
   *   <li>version: incremented via optimistic lock
   * </ul>
   *
   * @param message outbox message to acquire lease for
   * @param plan relay plan containing leaseOwner and leaseExpireAt
   * @return true if lease acquired successfully, false if another instance owns the lease
   */
  public boolean tryAcquire(OutboxMessage message, RelayPlan plan) {
    boolean acquired =
        relayStore.acquireLease(
            message.getId(), message.getVersion(), plan.leaseOwner(), plan.leaseExpireAt());

    if (log.isDebugEnabled()) {
      if (acquired) {
        log.debug(
            "Lease acquired: messageId={}, channel={}, leaseOwner={}, leaseExpireAt={}",
            message.getId(),
            message.getChannel(),
            plan.leaseOwner(),
            plan.leaseExpireAt());
      } else {
        log.debug(
            "Lease missed: messageId={}, channel={}, existingLeaseOwner={}, requestedBy={}",
            message.getId(),
            message.getChannel(),
            message.getLeaseOwner(),
            plan.leaseOwner());
      }
    }

    return acquired;
  }

  /**
   * Computes lease expiration timestamp based on plan's triggered time and lease duration.
   *
   * <p>Lease duration is typically configured to 30-60 seconds, sufficient for:
   *
   * <ul>
   *   <li>Publishing to downstream broker (usually < 100ms)
   *   <li>State update in database (< 10ms)
   *   <li>Retry buffer for transient network issues
   * </ul>
   *
   * <p>Note: This is a stateless computation delegated from RelayPlan construction.
   *
   * @param plan relay plan with triggeredAt and leaseDuration
   * @return lease expiration instant
   */
  public static java.time.Instant computeLeaseExpireAt(RelayPlan plan) {
    return plan.triggeredAt().plus(plan.leaseDuration());
  }
}
