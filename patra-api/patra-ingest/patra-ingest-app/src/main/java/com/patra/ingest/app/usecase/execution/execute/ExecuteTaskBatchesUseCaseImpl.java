package com.patra.ingest.app.usecase.execution.execute;

import com.patra.ingest.app.usecase.execution.support.ExecutionSession;
import com.patra.ingest.domain.model.entity.TaskRunBatch;
import com.patra.ingest.domain.model.vo.*;
import com.patra.ingest.domain.port.TaskRunBatchRepository;
import com.patra.ingest.domain.port.TaskRunRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Implementation of ExecuteTaskBatches use case.
 *
 * <p>Responsibility: batch planning → batch execution → persist results → return stats.
 *
 * <p>Design notes:
 *
 * <ul>
 *   <li>Batch planning via BatchPlannerRegistry to build batch list.
 *   <li>Enforce batch limits and throw when exceeded.
 *   <li>Batch execution via BatchExecutorRegistry.
 *   <li>Persist each batch result immediately via TaskRunBatchRepository.
 *   <li>Lease check before each batch; abort when revoked.
 *   <li>Error handling: record failures and continue (configurable fail-fast).
 * </ul>
 *
 * <p>Config:
 *
 * <ul>
 *   <li>task.execution.fail-fast: default false (continue).
 * </ul>
 *
 * <p>Logging:
 *
 * <ul>
 *   <li>INFO: plan created, batch start/finish, statistics.
 *   <li>WARN: limit exceeded, lease revoked, batch failures.
 * </ul>
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
  private final TaskRunRepository taskRunRepository;

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

    log.info(
        "execute batches start taskId={} runId={} provenanceCode={}",
        taskId,
        runId,
        context.provenanceCode());

    // 1) Plan batches.
    log.debug(
        "planning batches taskId={} runId={} provenanceCode={}",
        taskId,
        runId,
        context.provenanceCode());
    BatchPlanner planner = plannerRegistry.get(context.provenanceCode());
    BatchPlan plan = planner.plan(context);

    if (plan.exceedsLimit()) {
      throw new BatchLimitExceededException(
          "Batch count exceeds limit taskId=" + taskId + " totalBatches=" + plan.totalBatches());
    }

    if (!plan.hasBatches()) {
      log.warn("no batches planned taskId={} runId={}", taskId, runId);
      return new ExecuteResult(0, 0, 0);
    }

    log.info(
        "batch plan created taskId={} runId={} totalBatches={}",
        taskId,
        runId,
        plan.totalBatches());

    // 2) Execute batches
    BatchExecutor executor = executorRegistry.get(context.provenanceCode());
    int succeededCount = 0;
    int failedCount = 0;

    for (Batch batch : plan.batches()) {
      // 2.1 Check lease revocation
      log.debug(
          "processing batch [{}/{}] taskId={} runId={}",
          batch.batchNo(),
          plan.totalBatches(),
          taskId,
          runId);
      if (session.heartbeatHandle() != null && session.heartbeatHandle().isLeaseRevoked()) {
        log.warn(
            "lease revoked, abort batch execution taskId={} runId={} batchNo={}",
            taskId,
            runId,
            batch.batchNo());
        break; // lease revoked, abort
      }

      // 2.2 Execute single batch
      log.info(
          "execute batch start taskId={} runId={} batchNo={}/{}",
          taskId,
          runId,
          batch.batchNo(),
          plan.totalBatches());

      BatchResult result;
      try {
        result = executor.execute(context, batch);
      } catch (Exception e) {
        log.error(
            "batch execution failed taskId={} runId={} batchNo={}",
            taskId,
            runId,
            batch.batchNo(),
            e);
        result = BatchResult.failure(batch.batchNo(), e.getMessage());
      }

      // 2.3 Persist batch result
      TaskRunBatch batchEntity = TaskRunBatch.create(context, batch, result);
      batchRepository.save(batchEntity);

      // 2.3.1 Update TaskRun heartbeat to reflect batch processing activity
      try {
        boolean updated = taskRunRepository.touchHeartbeat(runId, Instant.now());
        if (log.isDebugEnabled()) {
          log.debug(
              "TaskRun heartbeat updated taskId={} runId={} batchNo={} updated={}",
              taskId,
              runId,
              batch.batchNo(),
              updated);
        }
      } catch (Exception e) {
        log.warn(
            "failed to update TaskRun heartbeat taskId={} runId={} batchNo={}",
            taskId,
            runId,
            batch.batchNo(),
            e);
        // Do not fail batch execution due to heartbeat update failure
      }

      // 2.4 Update statistics
      if (result.success()) {
        succeededCount++;
        log.info(
            "batch succeeded taskId={} runId={} batchNo={} fetchedCount={}",
            taskId,
            runId,
            batch.batchNo(),
            result.fetchedCount());
      } else {
        failedCount++;
        log.warn(
            "batch failed taskId={} runId={} batchNo={} error={}",
            taskId,
            runId,
            batch.batchNo(),
            result.errorMessage());

        // fail-fast: abort immediately
        if (failFast) {
          log.warn("fail-fast enabled, abort remaining batches taskId={} runId={}", taskId, runId);
          break;
        }
      }
    }

    log.info(
        "execute batches completed taskId={} runId={} total={} succeeded={} failed={}",
        taskId,
        runId,
        plan.totalBatches(),
        succeededCount,
        failedCount);

    return new ExecuteResult(plan.totalBatches(), succeededCount, failedCount);
  }

  /** Exception for batch limit exceeded. */
  public static class BatchLimitExceededException extends RuntimeException {
    public BatchLimitExceededException(String message) {
      super(message);
    }
  }
}
