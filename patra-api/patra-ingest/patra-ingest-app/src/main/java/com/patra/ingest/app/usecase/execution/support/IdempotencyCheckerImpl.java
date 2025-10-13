package com.patra.ingest.app.usecase.execution.support;

import com.patra.ingest.domain.port.TaskRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Idempotency checker implementation.
 *
 * <p>Responsibility: query TaskRun to check if a SUCCEEDED run already exists for the task to avoid
 * duplicate execution.
 *
 * <p>Design notes:
 *
 * <ul>
 *   <li>Idempotent key is bound to Task at creation; run layer does not re-validate it.
 *   <li>Only need to check if any TaskRun for the taskId is in SUCCEEDED status.
 *   <li>Use TaskRunRepository.hasSucceededRun() for efficient existence check.
 * </ul>
 *
 * <p>Logging: INFO when skipping execution for auditability.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdempotencyCheckerImpl implements IdempotencyChecker {

  private final TaskRunRepository taskRunRepository;

  /**
   * Checks whether the task has already succeeded.
   *
   * @param taskId task id
   * @param idempotentKey idempotent key (for logging; query relies on taskId only)
   * @return true if already succeeded
   */
  @Override
  public boolean isAlreadySucceeded(Long taskId, String idempotentKey) {
    boolean succeeded = taskRunRepository.hasSucceededRun(taskId);
    if (succeeded) {
      log.info(
          "[INGEST][APP] task already succeeded, skip execution taskId={} idemKey={}",
          taskId,
          idempotentKey);
    }
    return succeeded;
  }
}
