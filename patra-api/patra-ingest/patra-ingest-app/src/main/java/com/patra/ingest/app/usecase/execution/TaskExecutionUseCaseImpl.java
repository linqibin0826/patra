package com.patra.ingest.app.usecase.execution;

import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;
import com.patra.ingest.app.usecase.execution.complete.CompleteTaskExecutionUseCase;
import com.patra.ingest.app.usecase.execution.execute.ExecuteTaskBatchesUseCase;
import com.patra.ingest.app.usecase.execution.prepare.PrepareTaskExecutionUseCase;
import com.patra.ingest.app.usecase.execution.support.ExecutionSession;
import com.patra.ingest.domain.model.vo.ExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Task execution use case implementation (top-level orchestrator).
 *
 * <p>Responsibility: orchestrate Prepare → Execute → Complete sub-use-cases and handle top-level
 * exceptions and cleanup.
 *
 * <p>Design notes:
 *
 * <ul>
 *   <li>Three-phase orchestration per ADR-001.
 *   <li>Catch all exceptions and ensure cleanup (heartbeat/lease).
 *   <li>Idempotent skip: return immediately when TaskAlreadySucceededException is thrown by Prepare
 *       use case.
 *   <li>Lease acquisition failure: return immediately when LeaseAcquisitionFailedException is
 *       thrown by Prepare use case.
 *   <li>Failures in Execute/Complete do not prevent resource cleanup.
 * </ul>
 *
 * <p>Logging:
 *
 * <ul>
 *   <li>INFO: start, per-phase completion, end.
 *   <li>WARN: idempotent skip, lease acquisition failure.
 *   <li>ERROR: execution failure, cleanup failure.
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskExecutionUseCaseImpl implements TaskExecutionUseCase {

  private final PrepareTaskExecutionUseCase prepareUseCase;
  private final ExecuteTaskBatchesUseCase executeUseCase;
  private final CompleteTaskExecutionUseCase completeUseCase;

  /** Executes the task (prepare → execute → complete). */
  @Override
  public void execute(TaskReadyCommand command) {
    long taskId = command.taskId();
    String idempotentKey = command.idempotentKey();

    log.info("task execution start taskId={} idemKey={}", taskId, idempotentKey);

    ExecutionSession session = null;
    ExecutionContext context = null;

    try {
      // ========== Phase 0: Prepare ==========
      PrepareTaskExecutionUseCase.PrepareResult prepareResult;
      try {
        prepareResult = prepareUseCase.prepare(command);
        session = prepareResult.session();
        context = prepareResult.context();

        log.info(
            "prepare phase completed taskId={} runId={} provenance={} operation={}",
            taskId,
            session.runId(),
            context.provenanceCode(),
            context.operationCode());

      } catch (PrepareTaskExecutionUseCase.TaskAlreadySucceededException e) {
        // Idempotent skip: already succeeded
        log.warn(
            "task already succeeded, skip execution taskId={} idemKey={}", taskId, idempotentKey);
        return;

      } catch (PrepareTaskExecutionUseCase.LeaseAcquisitionFailedException e) {
        // Lease acquisition failed (owned by another worker)
        log.warn("lease acquisition failed, skip execution taskId={}", taskId);
        return;
      }

      // ========== Phase 1: Execute ==========
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult =
          executeUseCase.execute(session, context);

      log.info(
          "execute phase completed taskId={} runId={} total={} succeeded={} failed={}",
          taskId,
          session.runId(),
          executeResult.totalBatches(),
          executeResult.succeededBatches(),
          executeResult.failedBatches());

      // ========== Phase 2: Complete ==========
      completeUseCase.complete(session, context, executeResult);

      log.info("task execution completed taskId={} runId={}", taskId, session.runId());

    } catch (Exception e) {
      log.error("task execution failed taskId={} idemKey={}", taskId, idempotentKey, e);

      // On failure, try to cleanup resources (if session was created)
      if (session != null) {
        try {
          session.cleanup();
          log.info("session cleanup on failure taskId={}", taskId);
        } catch (Exception cleanupEx) {
          log.error("session cleanup failed taskId={}", taskId, cleanupEx);
        }
      }

      // Re-throw; upstream (adapter/MQ consumer) decides whether to retry
      throw new TaskExecutionException("Task execution failed taskId=" + taskId, e);
    }
  }

  /** Task execution exception (top-level). */
  public static class TaskExecutionException extends RuntimeException {
    public TaskExecutionException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
