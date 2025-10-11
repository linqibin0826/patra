package com.patra.ingest.app.usecase.execution.complete;

import com.patra.ingest.app.usecase.execution.execute.ExecuteTaskBatchesUseCase;
import com.patra.ingest.app.usecase.execution.support.ExecutionSession;
import com.patra.ingest.domain.model.vo.ExecutionContext;

/**
 * Complete phase use case.
 * <p>Responsibility: cursor advancement + status update (SUCCEEDED/PARTIAL/CURSOR_PENDING/FAILED).</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface CompleteTaskExecutionUseCase {

    /**
     * Completes execution (advance cursor + update status).
     *
     * @param session execution session
     * @param context execution context
     * @param executeResult execute results
     */
    void complete(ExecutionSession session,
                  ExecutionContext context,
                  ExecuteTaskBatchesUseCase.ExecuteResult executeResult);
}
