package com.patra.ingest.app.usecase.execution.support;

/**
 * 幂等检查器。
 * <p>检查任务是否已经成功执行（通过idempotentKey + 状态判断）。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface IdempotencyChecker {

    /**
     * 检查任务是否已经成功执行。
     *
     * @param taskId 任务ID
     * @param idempotentKey 幂等键
     * @return true表示已成功执行，无需重复执行
     */
    boolean isAlreadySucceeded(Long taskId, String idempotentKey);
}
