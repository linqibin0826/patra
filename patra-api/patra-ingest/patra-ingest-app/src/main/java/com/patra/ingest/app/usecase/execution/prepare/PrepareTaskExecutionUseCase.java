package com.patra.ingest.app.usecase.execution.prepare;

import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;
import com.patra.ingest.app.usecase.execution.session.ExecutionSession;
import com.patra.ingest.domain.model.vo.ExecutionContext;

/**
 * Prepare phase use case.
 *
 * <p>Responsibility: idempotency check, lease acquisition, session initialization, context loading.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PrepareTaskExecutionUseCase {

  /**
   * Performs preparation (idempotency check, lease acquisition, session creation, context loading).
   *
   * @param command task-ready command
   * @return preparation result (session + context)
   */
  PrepareResult prepare(TaskReadyCommand command);

  /**
   * Preparation result.
   *
   * @param session execution session (with heartbeat handle)
   * @param context execution context (config snapshot, compiled expression)
   */
  record PrepareResult(ExecutionSession session, ExecutionContext context) {}

  /** Raised when the task has already succeeded. */
  class TaskAlreadySucceededException extends RuntimeException {
    public TaskAlreadySucceededException(String message) {
      super(message);
    }
  }

  /** Raised when lease acquisition failed. */
  class LeaseAcquisitionFailedException extends RuntimeException {
    public LeaseAcquisitionFailedException(String message) {
      super(message);
    }
  }
}
