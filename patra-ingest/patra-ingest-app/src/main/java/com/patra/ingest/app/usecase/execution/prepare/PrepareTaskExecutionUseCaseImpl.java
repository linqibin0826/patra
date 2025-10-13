package com.patra.ingest.app.usecase.execution.prepare;

import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;
import com.patra.ingest.app.usecase.execution.support.*;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.vo.ExecutionContext;
import com.patra.ingest.domain.port.TaskRepository;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Prepare phase implementation.
 *
 * <p>Responsibility: idempotency check → lease acquisition → session initialization → context
 * loading.
 *
 * <p>Design notes:
 *
 * <ul>
 *   <li>Idempotency: call IdempotencyChecker.isAlreadySucceeded(); throw to skip if already done.
 *   <li>Lease: call LeaseManagementService.tryAcquireLease(); throw when acquisition fails.
 *   <li>Session: call ExecutionSessionManager.createSession() to create TaskRun and start
 *       heartbeat.
 *   <li>Context: call ExecutionContextLoader.loadContext() to restore config and compile
 *       expressions.
 * </ul>
 *
 * <p>Error handling:
 *
 * <ul>
 *   <li>TaskAlreadySucceededException for idempotent skip.
 *   <li>LeaseAcquisitionFailedException when lease acquisition fails.
 *   <li>Propagate IllegalStateException for context load failures.
 * </ul>
 *
 * <p>Logging:
 *
 * <ul>
 *   <li>INFO: key steps (idempotency, lease, session, context).
 *   <li>WARN: idempotent skip, lease failure.
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PrepareTaskExecutionUseCaseImpl implements PrepareTaskExecutionUseCase {

  private final TaskRepository taskRepository;
  private final IdempotencyChecker idempotencyChecker;
  private final LeaseManagementService leaseManagementService;
  private final ExecutionSessionManager sessionManager;
  private final ExecutionContextLoader contextLoader;

  @Value("${task.execution.lease.duration:60}")
  private int leaseDurationSeconds;

  /**
   * Performs preparation (idempotency check, lease acquire, session create, context load).
   *
   * <p>Optimizations:
   *
   * <ul>
   *   <li>Load Task once to avoid duplicate reads for createSession/loadContext.
   *   <li>Cleanup on exception to ensure heartbeat stops and lease is released.
   * </ul>
   */
  @Override
  public PrepareResult prepare(TaskReadyCommand command) {
    long taskId = command.taskId();
    String idempotentKey = command.idempotentKey();

    log.info(
        "[INGEST][APP] prepare task execution start taskId={} idemKey={}", taskId, idempotentKey);

    // 1) Idempotency check
    if (idempotencyChecker.isAlreadySucceeded(taskId, idempotentKey)) {
      throw new TaskAlreadySucceededException(
          "Task already succeeded taskId=" + taskId + " idemKey=" + idempotentKey);
    }

    // 2) Generate lease owner id
    String leaseOwner = generateLeaseOwner();

    // 3) Try acquire lease
    Duration leaseDuration = Duration.ofSeconds(leaseDurationSeconds);
    boolean acquired = leaseManagementService.tryAcquireLease(taskId, leaseOwner, leaseDuration);
    if (!acquired) {
      throw new LeaseAcquisitionFailedException(
          "Lease acquisition failed taskId=" + taskId + " owner=" + leaseOwner);
    }

    log.info("[INGEST][APP] lease acquired taskId={} owner={}", taskId, leaseOwner);

    // 4) Load Task (single read to avoid repetition)
    TaskAggregate task =
        taskRepository
            .findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found taskId=" + taskId));

    ExecutionSession session = null;
    try {
      // 5) Initialize session (create TaskRun, start heartbeat)
      String schedulerRunId = command.getSchedulerRunId();
      String correlationId = command.getCorrelationId();
      session =
          sessionManager.createSession(
              task, // Use pre-queried task
              leaseOwner,
              schedulerRunId,
              correlationId);

      log.info(
          "[INGEST][APP] session created taskId={} runId={} owner={}",
          taskId,
          session.runId(),
          leaseOwner);

      // 6) Load execution context (restore config, compile expressions)
      ExecutionContext context = contextLoader.loadContext(task, session.runId());

      log.info(
          "[INGEST][APP] prepare task execution completed taskId={} runId={}",
          taskId,
          session.runId());

      return new PrepareResult(session, context);

    } catch (Exception e) {
      // Resource cleanup on failure
      if (session != null) {
        log.warn(
            "[INGEST][APP] prepare failed, cleaning up resources taskId={} runId={}",
            taskId,
            session.runId(),
            e);
        try {
          // Stop heartbeat
          session.heartbeatHandle().stop();
          // Release lease
          leaseManagementService.releaseLease(taskId);
        } catch (Exception cleanupEx) {
          log.error(
              "[INGEST][APP] resource cleanup failed taskId={} runId={}",
              taskId,
              session.runId(),
              cleanupEx);
        }
      }
      throw e;
    }
  }

  /**
   * Generate a lease owner identifier.
   *
   * <p>Format: hostname:pid:execId
   *
   * <p>Combines machine id (hostname) + process id (PID) + execution id (UUID) for uniqueness and
   * traceability.
   */
  private String generateLeaseOwner() {
    try {
      String hostname = InetAddress.getLocalHost().getHostName();
      String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
      String execId = UUID.randomUUID().toString().substring(0, 8);
      return String.format("%s:%s:%s", hostname, pid, execId);
    } catch (UnknownHostException e) {
      // Fallback: use "unknown" as hostname if unable to resolve
      log.warn("[INGEST][APP] unable to resolve hostname, using fallback", e);
      String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
      String execId = UUID.randomUUID().toString().substring(0, 8);
      return String.format("unknown:%s:%s", pid, execId);
    }
  }
}
