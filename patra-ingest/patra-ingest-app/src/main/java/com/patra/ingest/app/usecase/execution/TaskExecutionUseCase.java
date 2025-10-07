package com.patra.ingest.app.usecase.execution;

import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;

/**
 * 任务执行用例（顶层编排器）。
 * <p>协调prepare→execute→complete三个子用例，处理顶层异常与资源清理。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface TaskExecutionUseCase {

    /**
     * 执行任务（完整流程：准备→执行→收尾）。
     *
     * @param command 任务就绪命令
     */
    void execute(TaskReadyCommand command);
}
