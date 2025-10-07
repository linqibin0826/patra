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
 * 执行任务批次用例实现。
 * <p>
 * 职责：批次规划 → 批次执行 → 结果持久化 → 统计返回。
 * </p>
 * <p>
 * 设计要点：
 * <ul>
 *   <li>批次规划：通过 BatchPlannerRegistry 获取规划器，生成批次列表。</li>
 *   <li>批次限制检查：检查批次数是否超过上限，超限则抛异常。</li>
 *   <li>批次执行：通过 BatchExecutorRegistry 获取执行器，逐批次执行。</li>
 *   <li>结果持久化：每个批次执行后立即保存到 TaskRunBatchRepository。</li>
 *   <li>租约检查：每批次执行前检查租约是否被撤销，撤销则中断执行。</li>
 *   <li>异常处理：批次执行失败时记录错误，继续执行后续批次（fail-fast 可配置）。</li>
 * </ul>
 * </p>
 * <p>
 * 配置项：
 * <ul>
 *   <li>task.execution.max-batches：最大批次数限制，默认 1000。</li>
 *   <li>task.execution.fail-fast：批次失败是否立即中断，默认 false（继续执行）。</li>
 * </ul>
 * </p>
 * <p>
 * 日志策略：
 * <ul>
 *   <li>INFO：批次规划完成、批次执行开始/完成、统计信息。</li>
 *   <li>WARN：批次超限、租约撤销、批次执行失败。</li>
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
     * 执行批次（规划 + 执行）。
     *
     * @param session 执行会话
     * @param context 执行上下文
     * @return 执行结果（含批次统计）
     */
    @Override
    public ExecuteResult execute(ExecutionSession session, ExecutionContext context) {
        Long taskId = session.taskId();
        Long runId = session.runId();

        log.info("[INGEST][APP] execute batches start taskId={} runId={} provenanceCode={}",
                 taskId, runId, context.provenanceCode());

        // 1. 批次规划
        BatchPlanner planner = plannerRegistry.get(context.provenanceCode());
        BatchPlan plan = planner.plan(context, maxBatches);

        if (plan.exceedsLimit()) {
            throw new BatchLimitExceededException(
                "批次数超过限制 taskId=" + taskId + " totalBatches=" + plan.totalBatches()
                + " maxBatches=" + maxBatches
            );
        }

        if (!plan.hasBatches()) {
            log.warn("[INGEST][APP] no batches planned taskId={} runId={}", taskId, runId);
            return new ExecuteResult(0, 0, 0);
        }

        log.info("[INGEST][APP] batch plan created taskId={} runId={} totalBatches={}",
                 taskId, runId, plan.totalBatches());

        // 2. 批次执行
        BatchExecutor executor = executorRegistry.get(context.provenanceCode());
        int succeededCount = 0;
        int failedCount = 0;

        for (Batch batch : plan.batches()) {
            // 2.1 检查租约是否被撤销
            if (session.heartbeatHandle() != null && session.heartbeatHandle().isLeaseRevoked()) {
                log.warn("[INGEST][APP] lease revoked, abort batch execution taskId={} runId={} batchNo={}",
                         taskId, runId, batch.batchNo());
                break;  // 租约撤销，中断执行
            }

            // 2.2 执行批次
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

            // 2.3 持久化批次结果
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

            // 2.4 统计
            if (result.success()) {
                succeededCount++;
                log.info("[INGEST][APP] batch succeeded taskId={} runId={} batchNo={} fetchedCount={}",
                         taskId, runId, batch.batchNo(), result.fetchedCount());
            } else {
                failedCount++;
                log.warn("[INGEST][APP] batch failed taskId={} runId={} batchNo={} error={}",
                         taskId, runId, batch.batchNo(), result.errorMessage());

                // fail-fast：立即中断
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

    /**
     * 批次限制超限异常。
     */
    public static class BatchLimitExceededException extends RuntimeException {
        public BatchLimitExceededException(String message) {
            super(message);
        }
    }
}
