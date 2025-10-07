package com.patra.ingest.app.usecase.execution.complete;

import com.patra.ingest.app.usecase.execution.execute.ExecuteTaskBatchesUseCase;
import com.patra.ingest.app.usecase.execution.support.ExecutionSession;
import com.patra.ingest.app.usecase.execution.support.LeaseManagementService;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.entity.TaskRun;
import com.patra.ingest.domain.model.vo.ExecutionContext;
import com.patra.ingest.domain.port.TaskRepository;
import com.patra.ingest.domain.port.TaskRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * 完成任务执行用例实现。
 * <p>
 * 职责：游标推进 → 状态判断 → Task/TaskRun 更新 → 资源清理（心跳/租约）。
 * </p>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>状态判断逻辑：
 *     <ul>
 *       <li>全部成功 + 游标推进成功 → SUCCEEDED</li>
 *       <li>全部成功 + 游标推进失败 → CURSOR_PENDING</li>
 *       <li>部分成功（failedBatches > 0 && succeededBatches > 0）→ PARTIAL</li>
 *       <li>全部失败（succeededBatches == 0）→ FAILED</li>
 *     </ul>
 *   </li>
 *   <li>游标推进：仅在全部批次成功时推进，失败时记录原因。</li>
 *   <li>乐观锁冲突：游标推进失败时标记为 CURSOR_PENDING，由后台异步重试。</li>
 *   <li>资源清理：停止心跳、释放租约（无论成功或失败）。</li>
 * </ul>
 * </p>
 * <p>
 * 日志策略：
 * <ul>
 *   <li>INFO：游标推进成功、任务完成（记录最终状态）。</li>
 *   <li>WARN：游标推进失败、部分失败、全部失败。</li>
 * </ul>
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CompleteTaskExecutionUseCaseImpl implements CompleteTaskExecutionUseCase {

    private final TaskRepository taskRepository;
    private final TaskRunRepository taskRunRepository;
    private final CursorAdvancer cursorAdvancer;
    private final LeaseManagementService leaseManagementService;
    private final Clock clock;

    /**
     * 完成执行（游标推进 + 状态更新）。
     *
     * @param session 执行会话
     * @param context 执行上下文
     * @param executeResult 执行结果
     */
    @Override
    public void complete(ExecutionSession session,
                         ExecutionContext context,
                         ExecuteTaskBatchesUseCase.ExecuteResult executeResult) {
        Long taskId = session.taskId();
        Long runId = session.runId();
        Instant now = clock.instant();

        log.info("[INGEST][APP] complete task execution start taskId={} runId={} total={} succeeded={} failed={}",
                 taskId, runId, executeResult.totalBatches(),
                 executeResult.succeededBatches(), executeResult.failedBatches());

        try {
            // 1. 读取 Task 聚合
            TaskAggregate task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("任务不存在 taskId=" + taskId));

            // 2. 读取 TaskRun
            TaskRun taskRun = taskRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalStateException("运行记录不存在 runId=" + runId));

            // 3. 判断执行结果并决定最终状态
            boolean allSucceeded = executeResult.failedBatches() == 0
                && executeResult.succeededBatches() > 0;
            boolean partialSuccess = executeResult.succeededBatches() > 0
                && executeResult.failedBatches() > 0;
            boolean allFailed = executeResult.succeededBatches() == 0;

            if (allSucceeded) {
                // 3.1 全部成功：推进游标
                boolean cursorAdvanced = false;
                try {
                    cursorAdvanced = cursorAdvancer.advance(context, taskId, runId);
                } catch (Exception e) {
                    log.error("[INGEST][APP] cursor advance failed taskId={} runId={}",
                              taskId, runId, e);
                }

                if (cursorAdvanced) {
                    // 游标推进成功 → SUCCEEDED
                    task.markSucceeded(now);
                    taskRun.succeed(now);
                    log.info("[INGEST][APP] task succeeded taskId={} runId={}", taskId, runId);
                } else {
                    // 游标推进失败 → CURSOR_PENDING
                    task.markCursorPending(now);
                    taskRun.markCursorPending(now);
                    log.warn("[INGEST][APP] task marked CURSOR_PENDING taskId={} runId={}", taskId, runId);
                }

            } else if (partialSuccess) {
                // 3.2 部分成功 → PARTIAL
                task.markPartial(now);
                taskRun.markPartial("部分批次失败", now);
                log.warn("[INGEST][APP] task marked PARTIAL taskId={} runId={}", taskId, runId);

            } else if (allFailed) {
                // 3.3 全部失败 → FAILED
                task.markFailed(now);
                taskRun.fail("全部批次失败", now);
                log.warn("[INGEST][APP] task marked FAILED taskId={} runId={}", taskId, runId);

            } else {
                // 3.4 无批次执行（totalBatches == 0）→ FAILED
                task.markFailed(now);
                taskRun.fail("无批次执行", now);
                log.warn("[INGEST][APP] task marked FAILED (no batches) taskId={} runId={}", taskId, runId);
            }

            // 4. 保存 Task 和 TaskRun
            taskRepository.save(task);
            taskRunRepository.save(taskRun);

            log.info("[INGEST][APP] complete task execution finished taskId={} runId={} finalStatus={}",
                     taskId, runId, task.getStatus());

        } finally {
            // 5. 资源清理（无论成功或失败）
            cleanupResources(session);
        }
    }

    /**
     * 清理资源（停止心跳、释放租约）。
     */
    private void cleanupResources(ExecutionSession session) {
        Long taskId = session.taskId();
        String leaseOwner = session.leaseOwner();

        try {
            // 停止心跳
            session.cleanup();
            log.info("[INGEST][APP] heartbeat stopped taskId={} owner={}", taskId, leaseOwner);

            // 释放租约
            leaseManagementService.releaseLease(taskId);
            log.info("[INGEST][APP] lease released taskId={} owner={}", taskId, leaseOwner);

        } catch (Exception e) {
            log.error("[INGEST][APP] cleanup failed taskId={} owner={}", taskId, leaseOwner, e);
            // 清理失败不影响任务完成，仅记录错误
        }
    }
}
