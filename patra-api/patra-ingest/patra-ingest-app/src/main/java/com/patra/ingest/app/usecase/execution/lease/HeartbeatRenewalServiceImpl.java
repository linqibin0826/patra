package com.patra.ingest.app.usecase.execution.lease;

import com.patra.ingest.app.usecase.execution.session.ExecutionSession;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Heartbeat renewal service implementation.
 *
 * <p>Responsibility: periodically renew the lease using ScheduledExecutorService. After reaching
 * the consecutive failure threshold, validate the lease to detect revocation.
 *
 * <p>Design notes:
 *
 * <ul>
 *   <li>Use a small ScheduledExecutorService for periodic renewal tasks.
 *   <li>Each renewal invokes LeaseManagementService.renewLease().
 *   <li>After N consecutive failures (default 3), call validateLease() to confirm revocation.
 *   <li>If revoked, set leaseRevoked flag so executors can abort.
 *   <li>Return a HeartbeatHandle to stop heartbeat and query lease status.
 * </ul>
 *
 * <p>Config:
 *
 * <ul>
 *   <li>task.execution.heartbeat.failure-threshold: consecutive failure threshold (default 3)
 * </ul>
 *
 * <p>Logging:
 *
 * <ul>
 *   <li>DEBUG: each renewal
 *   <li>WARN: renewal failure, lease revoked
 *   <li>INFO: heartbeat start/stop
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HeartbeatRenewalServiceImpl implements HeartbeatRenewalService {

  private final LeaseManagementService leaseManagementService;

  /** Consecutive failure threshold (default 3). */
  @Value("${task.execution.heartbeat.failure-threshold:3}")
  private int failureThreshold;

  /** Global scheduler (small pool; sufficient for heartbeat tasks). */
  private final ScheduledExecutorService scheduler =
      Executors.newScheduledThreadPool(
          2, // Use 2 threads to avoid single-thread starvation
          r -> {
            Thread t = new Thread(r, "heartbeat-renewal");
            t.setDaemon(true); // Daemon thread; exits on JVM shutdown
            return t;
          });

  /** Starts heartbeat-based lease renewal. */
  @Override
  public ExecutionSession.HeartbeatHandle startHeartbeat(
      Long taskId, String leaseOwner, Duration leaseDuration, Duration renewalInterval) {
    AtomicBoolean stopped = new AtomicBoolean(false);
    AtomicBoolean leaseRevoked = new AtomicBoolean(false);
    AtomicInteger consecutiveFailures = new AtomicInteger(0);

    // Periodic renewal task
    ScheduledFuture<?> future =
        scheduler.scheduleAtFixedRate(
            () -> {
              if (stopped.get()) {
                return;
              }

              try {
                boolean renewed =
                    leaseManagementService.renewLease(taskId, leaseOwner, leaseDuration);

                if (renewed) {
                  consecutiveFailures.set(0); // reset failures
                  if (log.isDebugEnabled()) {
                    log.debug("heartbeat renewed taskId={} owner={}", taskId, leaseOwner);
                  }
                } else {
                  int failures = consecutiveFailures.incrementAndGet();
                  log.warn(
                      "heartbeat renewal failed taskId={} owner={} consecutiveFailures={}",
                      taskId,
                      leaseOwner,
                      failures);

                  // Upon reaching threshold, validate lease proactively
                  if (failures >= failureThreshold) {
                    boolean valid = leaseManagementService.validateLease(taskId, leaseOwner);
                    if (!valid) {
                      leaseRevoked.set(true);
                      log.warn("lease revoked detected taskId={} owner={}", taskId, leaseOwner);
                      stopped.set(true); // stop heartbeat
                    }
                  }
                }
              } catch (Exception e) {
                log.error("heartbeat renewal error taskId={} owner={}", taskId, leaseOwner, e);
                int failures = consecutiveFailures.incrementAndGet();
                if (failures >= failureThreshold) {
                  leaseRevoked.set(true);
                  stopped.set(true);
                }
              }
            },
            renewalInterval.toMillis(), // initial delay
            renewalInterval.toMillis(), // period
            TimeUnit.MILLISECONDS);

    log.info(
        "heartbeat started taskId={} owner={} interval={}ms",
        taskId,
        leaseOwner,
        renewalInterval.toMillis());

    return new HeartbeatHandleImpl(taskId, leaseOwner, future, stopped, leaseRevoked);
  }

  /** Heartbeat handle implementation. */
  private static class HeartbeatHandleImpl implements ExecutionSession.HeartbeatHandle {
    private final Long taskId;
    private final String leaseOwner;
    private final ScheduledFuture<?> future;
    private final AtomicBoolean stopped;
    private final AtomicBoolean leaseRevoked;

    HeartbeatHandleImpl(
        Long taskId,
        String leaseOwner,
        ScheduledFuture<?> future,
        AtomicBoolean stopped,
        AtomicBoolean leaseRevoked) {
      this.taskId = taskId;
      this.leaseOwner = leaseOwner;
      this.future = future;
      this.stopped = stopped;
      this.leaseRevoked = leaseRevoked;
    }

    @Override
    public void stop() {
      if (stopped.compareAndSet(false, true)) {
        future.cancel(false); // do not interrupt running task
        log.info("heartbeat stopped taskId={} owner={}", taskId, leaseOwner);
      }
    }

    @Override
    public boolean isLeaseRevoked() {
      return leaseRevoked.get();
    }
  }
}
