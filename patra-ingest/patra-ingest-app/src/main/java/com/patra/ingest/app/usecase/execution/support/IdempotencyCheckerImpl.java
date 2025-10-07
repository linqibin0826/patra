package com.patra.ingest.app.usecase.execution.support;

import com.patra.ingest.domain.port.TaskRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 幂等检查器实现。
 * <p>
 * 职责：查询 TaskRun 表，检查指定任务是否已有 SUCCEEDED 状态的运行记录，避免重复执行。
 * </p>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>幂等键（idempotentKey）已在任务创建时绑定到 Task，无需在运行层重复校验。</li>
 *   <li>仅需判断 taskId 对应的 TaskRun 中是否存在 SUCCEEDED 状态。</li>
 *   <li>调用 TaskRunRepository.hasSucceededRun() 进行高效存在性查询。</li>
 * </ul>
 * </p>
 * <p>
 * 日志策略：INFO 记录跳过执行的情况，便于审计与排障。
 * </p>
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
     * 检查任务是否已经成功执行。
     *
     * @param taskId 任务ID
     * @param idempotentKey 幂等键（用于日志记录，实际查询仅依赖 taskId）
     * @return true表示已成功执行，无需重复执行
     */
    @Override
    public boolean isAlreadySucceeded(Long taskId, String idempotentKey) {
        boolean succeeded = taskRunRepository.hasSucceededRun(taskId);
        if (succeeded) {
            log.info("[INGEST][APP] task already succeeded, skip execution taskId={} idemKey={}",
                     taskId, idempotentKey);
        }
        return succeeded;
    }
}
