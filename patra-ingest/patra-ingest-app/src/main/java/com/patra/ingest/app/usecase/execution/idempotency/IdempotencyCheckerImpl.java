package com.patra.ingest.app.usecase.execution.idempotency;

import com.patra.ingest.domain.port.TaskRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 幂等性检查器实现。
 *
 * <p>职责:查询 TaskRun 检查任务是否已有 SUCCEEDED 运行记录,避免重复执行。
 *
 * <p>设计要点:
 *
 * <ul>
 *   <li>幂等键在 Task 创建时绑定;运行层不重新验证它
 *   <li>只需检查 taskId 的任何 TaskRun 是否处于 SUCCEEDED 状态
 *   <li>使用 TaskRunRepository.hasSucceededRun() 进行高效的存在性检查
 * </ul>
 *
 * <p>日志记录:跳过执行时记录 INFO 级别日志以便审计。
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
   * 检查任务是否已成功。
   *
   * @param taskId 任务 ID
   * @param idempotentKey 幂等键(用于日志;查询仅依赖 taskId)
   * @return true 如果已成功
   */
  @Override
  public boolean isAlreadySucceeded(Long taskId, String idempotentKey) {
    log.debug("检查任务是否已成功 taskId={} idemKey={}", taskId, idempotentKey);
    boolean succeeded = taskRunRepository.hasSucceededRun(taskId);
    if (succeeded) {
      log.info("任务已成功,跳过执行 taskId={} idemKey={}", taskId, idempotentKey);
    } else {
      log.debug("任务尚未成功 taskId={} idemKey={}", taskId, idempotentKey);
    }
    return succeeded;
  }
}
