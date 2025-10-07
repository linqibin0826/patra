package com.patra.ingest.app.usecase.execution.execute;

import com.patra.ingest.app.usecase.execution.support.ExecutionSession;
import com.patra.ingest.domain.model.vo.ExecutionContext;

/**
 * 执行任务批次用例（Execute阶段）。
 * <p>职责：批次规划 + 批次执行（含并发/幂等）。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ExecuteTaskBatchesUseCase {

    /**
     * 执行批次（规划 + 执行）。
     *
     * @param session 执行会话
     * @param context 执行上下文
     * @return 执行结果（含批次统计）
     */
    ExecuteResult execute(ExecutionSession session, ExecutionContext context);

    /**
     * 执行结果。
     *
     * @param totalBatches 总批次数
     * @param succeededBatches 成功批次数
     * @param failedBatches 失败批次数
     */
    record ExecuteResult(int totalBatches, int succeededBatches, int failedBatches) {
    }
}
