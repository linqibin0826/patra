package com.patra.ingest.app.usecase.execution.session;

/**
 * Execution session (wraps lease, heartbeat, and revocation flag).
 *
 * @param taskId task id
 * @param runId run id
 * @param leaseOwner lease owner
 * @param heartbeatHandle heartbeat handle (to stop heartbeat)
 * @param leaseRevoked whether the lease has been revoked (volatile flag)
 * @author linqibin
 * @since 0.1.0
 */
public record ExecutionSession(
    Long taskId,
    Long runId,
    String leaseOwner,
    HeartbeatHandle heartbeatHandle,
    boolean leaseRevoked) {
  /** Cleans up resources (stop heartbeat + release lease). */
  public void cleanup() {
    if (heartbeatHandle != null) {
      heartbeatHandle.stop();
    }
  }

  /** Heartbeat handle (to stop heartbeat). */
  public interface HeartbeatHandle {
    /** Stops the heartbeat. */
    void stop();

    /** Returns whether the lease has been revoked. */
    boolean isLeaseRevoked();
  }
}
