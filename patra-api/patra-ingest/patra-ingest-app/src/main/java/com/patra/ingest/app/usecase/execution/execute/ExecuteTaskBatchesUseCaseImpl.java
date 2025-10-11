package com.patra.ingest.app.usecase.execution.execute;

import com.patra.ingest.app.usecase.execution.support.ExecutionSession;
import com.patra.ingest.domain.model.entity.TaskRunBatch;
import com.patra.ingest.domain.model.vo.*;
import com.patra.ingest.domain.port.TaskRunBatchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Implementation of ExecuteTaskBatches use case.
 * <p>
 * Responsibility: batch planning → batch execution → persist results → return stats.
 * </p>
 * <p>
 * Design notes:
 * <ul>
 *   <li>Batch planning via BatchPlannerRegistry to build batch list.</li>
 *   <li>Enforce batch limits and throw when exceeded.</li>
 *   <li>Batch execution via BatchExecutorRegistry.</li>
 *   <li>Persist each batch result immediately via TaskRunBatchRepository.</li>
 *   <li>Lease check before each batch; abort when revoked.</li>
 *   <li>Error handling: record failures and continue (configurable fail-fast).</li>
 * </ul>
 * </p>
 * <p>
 * Config:
 * <ul>
 *   <li>task.execution.max-batches: default 1000.</li>
 *   <li>task.execution.fail-fast: default false (continue).</li>
 * </ul>
 * </p>
 * <p>
 * Logging:
 * <ul>
 *   <li>INFO: plan created, batch start/finish, statistics.</li>
 *   <li>WARN: limit exceeded, lease revoked, batch failures.</li>
 * </ul>
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExecuteTaskBatchesUseCaseImpl implements ExecuteTaskBatchesUseCase {

    private final BatchPlannerRegistry plannerRegistry;
    private final BatchExecutorRegistry executorRegistry;
    private final TaskRunBatchRepository batchRepository;

    @Value("${task.execution.max-batches:1000}")
    private int maxBatches;

    @Value("${task.execution.fail-fast:false}")
    private boolean failFast;

    /**
     * Executes batches (plan + execute).
     *
     * @param session execution session
     * @param context execution context
     * @return result with batch statistics
     */
    @Override
    public ExecuteResult execute(ExecutionSession session, ExecutionContext context) {
        Long taskId = session.taskId();
        Long runId = session.runId();

        log.info("[INGEST][APP] execute batches start taskId={} runId={} provenanceCode={}",
                 taskId, runId, context.provenanceCode());

        // 1) Plan batches. TODO: implement concrete BatchPlanner(s)
        BatchPlanner planner = plannerRegistry.get(context.provenanceCode());
        BatchPlan plan = planner.plan(context, maxBatches);

        if (plan.exceedsLimit()) {
            throw new BatchLimitExceededException(
                "Batch count exceeds limit taskId=" + taskId + 
                " totalBatches=" + plan.totalBatches() +
                " maxBatches=" + maxBatches
            );
        }

        if (!plan.hasBatches()) {
            log.warn("[INGEST][APP] no batches planned taskId={} runId={}", taskId, runId);
            return new ExecuteResult(0, 0, 0);
        }

        log.info("[INGEST][APP] batch plan created taskId={} runId={} totalBatches={}",
                 taskId, runId, plan.totalBatches());

        // 2) Execute batches
        BatchExecutor executor = executorRegistry.get(context.provenanceCode());
        int succeededCount = 0;
        int failedCount = 0;

        for (Batch batch : plan.batches()) {
            // 2.1 Check lease revocation
            if (session.heartbeatHandle() != null && session.heartbeatHandle().isLeaseRevoked()) {
                log.warn("[INGEST][APP] lease revoked, abort batch execution taskId={} runId={} batchNo={}",
                         taskId, runId, batch.batchNo());
                break;  // lease revoked, abort
            }

            // 2.2 Execute single batch
            log.info("[INGEST][APP] execute batch start taskId={} runId={} batchNo={}/{}",
                     taskId, runId, batch.batchNo(), plan.totalBatches());

            BatchResult result;
            try {
                result = executor.execute(context, batch);
            } catch (Exception e) {
                log.error("[INGEST][APP] batch execution failed taskId={} runId={} batchNo={}",
                          taskId, runId, batch.batchNo(), e);
                result = BatchResult.failure(batch.batchNo(), e.getMessage());
            }

            // 2.3 Persist batch result
            TaskRunBatch batchEntity = TaskRunBatch.create(
                runId,
                batch.batchNo(),
                result.success(),
                result.fetchedCount(),
                result.nextCursorToken(),
                result.errorMessage(),
                result.storageKey()
            );
            batchRepository.save(batchEntity);

            // 2.4 Update statistics
            if (result.success()) {
                succeededCount++;
                log.info("[INGEST][APP] batch succeeded taskId={} runId={} batchNo={} fetchedCount={}",
                         taskId, runId, batch.batchNo(), result.fetchedCount());
            } else {
                failedCount++;
                log.warn("[INGEST][APP] batch failed taskId={} runId={} batchNo={} error={}",
                         taskId, runId, batch.batchNo(), result.errorMessage());

                // fail-fast: abort immediately
                if (failFast) {
                    log.warn("[INGEST][APP] fail-fast enabled, abort remaining batches taskId={} runId={}",
                             taskId, runId);
                    break;
                }
            }
        }

        log.info("[INGEST][APP] execute batches completed taskId={} runId={} total={} succeeded={} failed={}",
                 taskId, runId, plan.totalBatches(), succeededCount, failedCount);

        return new ExecuteResult(plan.totalBatches(), succeededCount, failedCount);
    }

    /** Exception for batch limit exceeded. */
    public static class BatchLimitExceededException extends RuntimeException {
        public BatchLimitExceededException(String message) {
            super(message);
        }
    }
}
