package com.patra.ingest.app.usecase.execution;

import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;

/**
 * Task execution use case (top-level orchestrator).
 *
 * <p>Coordinates prepare → execute → complete and handles top-level exceptions and cleanup.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface TaskExecutionUseCase {

  /**
   * Executes the task (full flow: prepare → execute → complete).
   *
   * @param command task-ready command
   */
  void execute(TaskReadyCommand command);
}
