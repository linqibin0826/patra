package com.patra.ingest.app.usecase.execution.support;

/**
 * Idempotency checker.
 *
 * <p>Checks whether a task has already succeeded (by idempotentKey + status).
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface IdempotencyChecker {

  /**
   * Checks whether the task has already been executed successfully.
   *
   * @param taskId task id
   * @param idempotentKey idempotent key
   * @return true if already succeeded, no need to execute again
   */
  boolean isAlreadySucceeded(Long taskId, String idempotentKey);
}
