package com.patra.ingest.app.usecase.execution.complete;

import com.patra.ingest.app.usecase.execution.execute.ExecuteTaskBatchesUseCase;
import com.patra.ingest.app.usecase.execution.support.ExecutionSession;
import com.patra.ingest.domain.model.vo.ExecutionContext;

/**
 * 完成任务执行用例（Complete阶段）。
 * <p>职责：游标推进 + 状态更新（SUCCEEDED/PARTIAL/CURSOR_PENDING/FAILED）。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface CompleteTaskExecutionUseCase {

    /**
     * 完成执行（游标推进 + 状态更新）。
     *
     * @param session 执行会话
     * @param context 执行上下文
     * @param executeResult 执行结果
     */
    void complete(ExecutionSession session,
                  ExecutionContext context,
                  ExecuteTaskBatchesUseCase.ExecuteResult executeResult);
}
