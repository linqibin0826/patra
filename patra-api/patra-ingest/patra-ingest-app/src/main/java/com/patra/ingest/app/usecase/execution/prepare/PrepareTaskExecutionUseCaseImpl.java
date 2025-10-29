package com.patra.ingest.app.usecase.execution.prepare;

import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;
import com.patra.ingest.app.usecase.execution.idempotency.IdempotencyChecker;
import com.patra.ingest.app.usecase.execution.lease.LeaseManagementService;
import com.patra.ingest.app.usecase.execution.session.ExecutionContextLoader;
import com.patra.ingest.app.usecase.execution.session.ExecutionSession;
import com.patra.ingest.app.usecase.execution.session.ExecutionSessionManager;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.entity.TaskRun;
import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.ingest.domain.model.vo.ExecutionContext;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.domain.port.TaskRepository;
import com.patra.ingest.domain.port.TaskRunRepository;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
  private final PlanSliceRepository planSliceRepository;
  private final IdempotencyChecker idempotencyChecker;
  private final LeaseManagementService leaseManagementService;
  private final ExecutionSessionManager sessionManager;
  private final ExecutionContextLoader contextLoader;
  private final TaskRunRepository taskRunRepository;
  private final Clock clock;

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

    log.info("prepare task execution start taskId={} idemKey={}", taskId, idempotentKey);

    // 1) Idempotency check
    log.debug("checking idempotency taskId={} idemKey={}", taskId, idempotentKey);
    if (idempotencyChecker.isAlreadySucceeded(taskId, idempotentKey)) {
      throw new TaskAlreadySucceededException(
          "Task already succeeded taskId=" + taskId + " idemKey=" + idempotentKey);
    }

    // 2) Generate lease owner id
    String leaseOwner = generateLeaseOwner();

    // 3) Try acquire lease
    log.debug(
        "attempting to acquire lease taskId={} owner={} duration={}s",
        taskId,
        leaseOwner,
        leaseDurationSeconds);
    Duration leaseDuration = Duration.ofSeconds(leaseDurationSeconds);
    boolean acquired = leaseManagementService.tryAcquireLease(taskId, leaseOwner, leaseDuration);
    if (!acquired) {
      throw new LeaseAcquisitionFailedException(
          "Lease acquisition failed taskId=" + taskId + " owner=" + leaseOwner);
    }

    log.info("lease acquired taskId={} owner={}", taskId, leaseOwner);

    // 4) Load Task (single read to avoid repetition)
    TaskAggregate task =
        taskRepository
            .findById(taskId)
            .orElseThrow(() -> new IllegalArgumentException("Task not found taskId=" + taskId));

    // Mark Slice as EXECUTING (if still PENDING)
    PlanSliceAggregate slice =
        planSliceRepository
            .findById(task.getSliceId())
            .orElseThrow(
                () -> new IllegalStateException("Slice not found: sliceId=" + task.getSliceId()));

    if (slice.getStatus() == SliceStatus.PENDING) {
      slice.markExecuting();
      planSliceRepository.save(slice);
      log.info("Slice marked as EXECUTING sliceId={}", slice.getId());
    }

    ExecutionSession session = null;
    try {
      // 5) Initialize session (create TaskRun, start heartbeat)
      String correlationId = command.getCorrelationId();
      session =
          sessionManager.createSession(
              task, // Use pre-queried task
              leaseOwner,
              correlationId);

      Long runId = session.runId();
      log.info("session created taskId={} runId={} owner={}", taskId, runId, leaseOwner);

      // 6) Load execution context (restore config, compile expressions)
      log.debug("loading execution context taskId={} runId={}", taskId, runId);
      ExecutionContext context = contextLoader.loadContext(task, runId);

      // 7) Mark task/run as RUNNING
      TaskRun taskRun =
          taskRunRepository
              .findById(runId)
              .orElseThrow(() -> new IllegalStateException("TaskRun not found runId=" + runId));
      Instant now = clock.instant();
      taskRun.bindRunContext(correlationId);
      taskRun.start(now);
      taskRunRepository.save(taskRun);
      task.markRunning(now, correlationId);
      taskRepository.save(task);

      log.info("prepare task execution completed taskId={} runId={}", taskId, runId);

      return new PrepareResult(session, context);

    } catch (Exception e) {
      // Resource cleanup on failure
      if (session != null) {
        log.warn(
            "prepare failed, cleaning up resources taskId={} runId={}", taskId, session.runId(), e);
        try {
          // Stop heartbeat
          session.heartbeatHandle().stop();
          // Release lease
          leaseManagementService.releaseLease(taskId);
        } catch (Exception cleanupEx) {
          log.error(
              "resource cleanup failed taskId={} runId={}", taskId, session.runId(), cleanupEx);
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
      log.warn("unable to resolve hostname, using fallback", e);
      String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
      String execId = UUID.randomUUID().toString().substring(0, 8);
      return String.format("unknown:%s:%s", pid, execId);
    }
  }
}
