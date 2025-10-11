package com.patra.ingest.app.usecase.execution.execute;

import com.patra.ingest.app.usecase.execution.support.ExecutionSession;
import com.patra.ingest.domain.model.vo.ExecutionContext;

/**
 * Use case for executing task batches (Execute phase).
 * <p>Responsibility: batch planning + batch execution (with concurrency/idempotency).</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ExecuteTaskBatchesUseCase {

    /**
     * Executes batches (plan + execute).
     *
     * @param session execution session
     * @param context execution context
     * @return execution result (with batch stats)
     */
    ExecuteResult execute(ExecutionSession session, ExecutionContext context);

    /**
     * Execution result.
     *
     * @param totalBatches total number of batches
     * @param succeededBatches number of succeeded batches
     * @param failedBatches number of failed batches
     */
    record ExecuteResult(int totalBatches, int succeededBatches, int failedBatches) {
    }
}
