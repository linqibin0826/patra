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
 * Complete phase use case implementation.
 * <p>
 * Responsibility: cursor advancement → status decision → Task/TaskRun update → resource cleanup (heartbeat/lease).
 * </p>
 * <p>
 * Design notes:
 * <ul>
 *   <li>Status decision:
 *     <ul>
 *       <li>all succeeded + cursor advanced → SUCCEEDED</li>
 *       <li>all succeeded + cursor failed → CURSOR_PENDING</li>
 *       <li>partial success (failed > 0 && succeeded > 0) → PARTIAL</li>
 *       <li>all failed (succeeded == 0) → FAILED</li>
 *     </ul>
 *   </li>
 *   <li>Advance cursor only when all batches succeeded; record reason on failure.</li>
 *   <li>On optimistic conflict, mark CURSOR_PENDING for async retry.</li>
 *   <li>Cleanup: stop heartbeat and release lease regardless of outcome.</li>
 * </ul>
 * </p>
 * <p>
 * Logging:
 * <ul>
 *   <li>INFO: cursor advanced, task completed (final status).</li>
 *   <li>WARN: cursor failed, partial/failed states.</li>
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

    /** Completes execution (advance cursor + update status). */
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
            // 1) Load Task aggregate
            TaskAggregate task = taskRepository.findById(taskId)
                .orElseThrow(() -> new IllegalStateException("Task not found taskId=" + taskId));

            // 2) Load TaskRun
            TaskRun taskRun = taskRunRepository.findById(runId)
                .orElseThrow(() -> new IllegalStateException("Run record not found runId=" + runId));

            // 3) Decide final status
            boolean allSucceeded = executeResult.failedBatches() == 0
                && executeResult.succeededBatches() > 0;
            boolean partialSuccess = executeResult.succeededBatches() > 0
                && executeResult.failedBatches() > 0;
            boolean allFailed = executeResult.succeededBatches() == 0;

            if (allSucceeded) {
                // 3.1 All succeeded: advance cursor
                boolean cursorAdvanced = false;
                try {
                    cursorAdvanced = cursorAdvancer.advance(context, taskId, runId);
                } catch (Exception e) {
                    log.error("[INGEST][APP] cursor advance failed taskId={} runId={}",
                              taskId, runId, e);
                }

                if (cursorAdvanced) {
                    // Cursor ok → SUCCEEDED
                    task.markSucceeded(now);
                    taskRun.succeed(now);
                    log.info("[INGEST][APP] task succeeded taskId={} runId={}", taskId, runId);
                } else {
                    // Cursor failed → CURSOR_PENDING
                    task.markCursorPending(now);
                    taskRun.markCursorPending(now);
                    log.warn("[INGEST][APP] task marked CURSOR_PENDING taskId={} runId={}", taskId, runId);
                }

            } else if (partialSuccess) {
                // 3.2 Partial → PARTIAL
                task.markPartial(now);
                taskRun.markPartial("Some batches failed", now);
                log.warn("[INGEST][APP] task marked PARTIAL taskId={} runId={}", taskId, runId);

            } else if (allFailed) {
                // 3.3 All failed → FAILED
                task.markFailed(now);
                taskRun.fail("All batches failed", now);
                log.warn("[INGEST][APP] task marked FAILED taskId={} runId={}", taskId, runId);

            } else {
                // 3.4 No batches executed (totalBatches == 0) → FAILED
                task.markFailed(now);
                taskRun.fail("No batches executed", now);
                log.warn("[INGEST][APP] task marked FAILED (no batches) taskId={} runId={}", taskId, runId);
            }

            // 4) Persist Task and TaskRun
            taskRepository.save(task);
            taskRunRepository.save(taskRun);

            log.info("[INGEST][APP] complete task execution finished taskId={} runId={} finalStatus={}",
                     taskId, runId, task.getStatus());

        } finally {
            // 5) Cleanup regardless of outcome
            cleanupResources(session);
        }
    }

    /**
     * Cleanup resources (stop heartbeat, release lease).
     */
    private void cleanupResources(ExecutionSession session) {
        Long taskId = session.taskId();
        String leaseOwner = session.leaseOwner();

        try {
            // Stop heartbeat
            session.cleanup();
            log.info("[INGEST][APP] heartbeat stopped taskId={} owner={}", taskId, leaseOwner);

            // Release lease
            leaseManagementService.releaseLease(taskId);
            log.info("[INGEST][APP] lease released taskId={} owner={}", taskId, leaseOwner);

        } catch (Exception e) {
            log.error("[INGEST][APP] cleanup failed taskId={} owner={}", taskId, leaseOwner, e);
            // Cleanup failure does not affect completion; log only
        }
    }
}
