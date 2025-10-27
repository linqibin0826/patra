package com.patra.ingest.app.usecase.execution.lease;

import com.patra.ingest.app.usecase.execution.session.ExecutionSession;
import java.time.Duration;

/**
 * Heartbeat renewal service.
 *
 * <p>Uses a ScheduledExecutorService to renew leases periodically; after consecutive failures,
 * validates lease.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface HeartbeatRenewalService {

  /**
   * Starts heartbeat-based lease renewal.
   *
   * @param taskId task id
   * @param leaseOwner lease owner
   * @param leaseDuration lease duration
   * @param renewalInterval renewal interval
   * @return heartbeat handle (to stop the heartbeat)
   */
  ExecutionSession.HeartbeatHandle startHeartbeat(
      Long taskId, String leaseOwner, Duration leaseDuration, Duration renewalInterval);
}
