package com.patra.ingest.app.usecase.execution;

import com.patra.ingest.app.usecase.execution.command.TaskReadyCommand;
import com.patra.ingest.app.usecase.execution.complete.CompleteTaskExecutionUseCase;
import com.patra.ingest.app.usecase.execution.execute.ExecuteTaskBatchesUseCase;
import com.patra.ingest.app.usecase.execution.prepare.PrepareTaskExecutionUseCase;
import com.patra.ingest.app.usecase.execution.support.ExecutionSession;
import com.patra.ingest.domain.model.vo.ExecutionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 任务执行用例实现（顶层编排器）。
 * <p>
 * 职责：编排 Prepare → Execute → Complete 三个子用例，处理顶层异常与资源清理。
 * </p>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>三阶段编排：按 ADR-001 模式分离准备、执行、完成三个阶段。</li>
 *   <li>异常处理：捕获所有异常，确保资源清理（心跳/租约）。</li>
 *   <li>幂等跳过：PrepareTaskExecutionUseCase 抛出 TaskAlreadySucceededException 时直接返回。</li>
 *   <li>租约抢占失败：PrepareTaskExecutionUseCase 抛出 LeaseAcquisitionFailedException 时直接返回。</li>
 *   <li>执行失败：ExecuteTaskBatchesUseCase 和 CompleteTaskExecutionUseCase 的异常不影响资源清理。</li>
 * </ul>
 * </p>
 * <p>
 * 日志策略：
 * <ul>
 *   <li>INFO：任务执行开始、各阶段完成、任务执行结束。</li>
 *   <li>WARN：幂等跳过、租约抢占失败。</li>
 *   <li>ERROR：执行失败、资源清理失败。</li>
 * </ul>
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TaskExecutionUseCaseImpl implements TaskExecutionUseCase {

    private final PrepareTaskExecutionUseCase prepareUseCase;
    private final ExecuteTaskBatchesUseCase executeUseCase;
    private final CompleteTaskExecutionUseCase completeUseCase;

    /**
     * 执行任务（完整流程：准备→执行→收尾）。
     *
     * @param command 任务就绪命令
     */
    @Override
    public void execute(TaskReadyCommand command) {
        long taskId = command.taskId();
        String idempotentKey = command.idempotentKey();

        log.info("[INGEST][APP] task execution start taskId={} idemKey={}",
                 taskId, idempotentKey);

        ExecutionSession session = null;
        ExecutionContext context = null;

        try {
            // ========== 阶段 0：准备 ==========
            PrepareTaskExecutionUseCase.PrepareResult prepareResult;
            try {
                prepareResult = prepareUseCase.prepare(command);
                session = prepareResult.session();
                context = prepareResult.context();

                log.info("[INGEST][APP] prepare phase completed taskId={} runId={} provenance={} operation={}",
                         taskId, session.runId(), context.provenanceCode(), context.operationCode());

            } catch (PrepareTaskExecutionUseCase.TaskAlreadySucceededException e) {
                // 幂等跳过：任务已成功执行
                log.warn("[INGEST][APP] task already succeeded, skip execution taskId={} idemKey={}",
                         taskId, idempotentKey);
                return;

            } catch (PrepareTaskExecutionUseCase.LeaseAcquisitionFailedException e) {
                // 租约抢占失败：他人持有
                log.warn("[INGEST][APP] lease acquisition failed, skip execution taskId={}",
                         taskId);
                return;
            }

            // ========== 阶段 1：执行 ==========
            ExecuteTaskBatchesUseCase.ExecuteResult executeResult = executeUseCase.execute(session, context);

            log.info("[INGEST][APP] execute phase completed taskId={} runId={} total={} succeeded={} failed={}",
                     taskId, session.runId(), executeResult.totalBatches(),
                     executeResult.succeededBatches(), executeResult.failedBatches());

            // ========== 阶段 2：完成 ==========
            completeUseCase.complete(session, context, executeResult);

            log.info("[INGEST][APP] task execution completed taskId={} runId={}",
                     taskId, session.runId());

        } catch (Exception e) {
            log.error("[INGEST][APP] task execution failed taskId={} idemKey={}",
                      taskId, idempotentKey, e);

            // 执行失败时，尝试清理资源（如果 session 已创建）
            if (session != null) {
                try {
                    session.cleanup();
                    log.info("[INGEST][APP] session cleanup on failure taskId={}", taskId);
                } catch (Exception cleanupEx) {
                    log.error("[INGEST][APP] session cleanup failed taskId={}", taskId, cleanupEx);
                }
            }

            // 重新抛出异常，由上层（Adapter/MQ Consumer）决定是否重试
            throw new TaskExecutionException("任务执行失败 taskId=" + taskId, e);
        }
    }

    /**
     * 任务执行异常（顶层异常）。
     */
    public static class TaskExecutionException extends RuntimeException {
        public TaskExecutionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
