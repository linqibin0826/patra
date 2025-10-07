package com.patra.ingest.app.usecase.execution.prepare;

import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;
import com.patra.ingest.app.usecase.execution.support.ExecutionSession;
import com.patra.ingest.domain.model.vo.ExecutionContext;

/**
 * 准备任务执行用例（Prepare阶段）。
 * <p>职责：幂等检查、租约抢占、会话初始化、上下文加载。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PrepareTaskExecutionUseCase {

    /**
     * 准备执行（包含幂等检查、租约抢占、会话创建、上下文加载）。
     *
     * @param command 任务就绪命令
     * @return 准备结果（包含session和context）
     */
    PrepareResult prepare(TaskReadyCommand command);

    /**
     * 准备结果。
     *
     * @param session 执行会话（含心跳句柄）
     * @param context 执行上下文（配置快照、编译表达式）
     */
    record PrepareResult(ExecutionSession session, ExecutionContext context) {
    }

    /**
     * 任务已成功执行异常。
     */
    class TaskAlreadySucceededException extends RuntimeException {
        public TaskAlreadySucceededException(String message) {
            super(message);
        }
    }

    /**
     * 租约抢占失败异常。
     */
    class LeaseAcquisitionFailedException extends RuntimeException {
        public LeaseAcquisitionFailedException(String message) {
            super(message);
        }
    }
}
