package com.patra.ingest.app.usecase.execution.complete;

import com.patra.ingest.app.usecase.execution.cursor.CursorAdvancer;
import com.patra.ingest.app.usecase.execution.lease.LeaseManagementService;
import com.patra.ingest.app.usecase.execution.publisher.PublicationEventPublisher;
import com.patra.ingest.app.usecase.execution.session.ExecutionSession;
import com.patra.ingest.app.usecase.execution.strategy.ExecuteTaskBatchesUseCase;
import com.patra.ingest.domain.event.PublicationDataReadyEvent;
import com.patra.ingest.domain.event.TaskCompletedEvent;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.entity.TaskRun;
import com.patra.ingest.domain.model.entity.TaskRunBatch;
import com.patra.ingest.domain.model.enums.BatchStatus;
import com.patra.ingest.domain.model.vo.batch.BatchStats;
import com.patra.ingest.domain.model.vo.execution.ExecutionContext;
import com.patra.ingest.domain.model.vo.execution.RunStats;
import com.patra.ingest.domain.port.TaskRepository;
import com.patra.ingest.domain.port.TaskRunBatchRepository;
import com.patra.ingest.domain.port.TaskRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/// 完成阶段用例实现
/// 
/// 在六边形架构+DDD中的角色:应用层用例实现,负责任务执行完成阶段的完整流程。
/// 
/// 主要职责:游标推进 → 状态决策 → Task/TaskRun更新 → 资源清理(心跳/租约)
/// 
/// 设计要点:
/// 
/// - 状态决策逻辑:
///       
/// - 全部成功 + 游标推进成功 → Task: SUCCEEDED, TaskRun: SUCCEEDED
///         - 全部成功 + 游标推进失败 → Task: FAILED, TaskRun: PARTIAL (可重试检查点)
///         - 部分成功 (failed > 0 且 succeeded > 0) → Task: FAILED, TaskRun: PARTIAL
///         - 全部失败 (succeeded == 0) → Task: FAILED, TaskRun: FAILED
/// 
///   - 仅当所有批次成功时才推进游标;失败时记录原因
///   - 乐观锁冲突或游标失败时,将TaskRun标记为PARTIAL(可重试)
///   - 清理:无论结果如何都停止心跳并释放租约
/// 
/// 日志策略:
/// 
/// - INFO: 游标推进、任务完成(SUCCEEDED状态)
///   - WARN: 游标失败、部分成功或全部失败状态
/// 
/// @author linqibin
/// @since 0.1.0
@Service
@RequiredArgsConstructor
@Slf4j
public class CompleteTaskExecutionUseCaseImpl implements CompleteTaskExecutionUseCase {

  private final TaskRepository taskRepository;
  private final TaskRunRepository taskRunRepository;
  private final TaskRunBatchRepository taskRunBatchRepository;
  private final CursorAdvancer cursorAdvancer;
  private final LeaseManagementService leaseManagementService;
  private final PublicationEventPublisher publicationEventPublisher;
  private final ApplicationEventPublisher applicationEventPublisher;
  private final Clock clock;

  /// Completes execution (advance cursor + update status).
  @Override
  @Transactional(rollbackFor = Exception.class)
  public void complete(
      ExecutionSession session,
      ExecutionContext context,
      ExecuteTaskBatchesUseCase.ExecuteResult executeResult) {
    Long taskId = session.taskId();
    Long runId = session.runId();
    Instant now = clock.instant();

    log.info(
        "complete task execution start taskId={} runId={} total={} succeeded={} failed={}",
        taskId,
        runId,
        executeResult.totalBatches(),
        executeResult.succeededBatches(),
        executeResult.failedBatches());

    try {
      // 1) Load Task aggregate
      TaskAggregate task =
          taskRepository
              .findById(taskId)
              .orElseThrow(() -> new IllegalStateException("Task not found taskId=" + taskId));

      // 2) Load TaskRun
      TaskRun taskRun =
          taskRunRepository
              .findById(runId)
              .orElseThrow(() -> new IllegalStateException("Run record not found runId=" + runId));

      // 3) Decide final status
      boolean allSucceeded =
          executeResult.failedBatches() == 0 && executeResult.succeededBatches() > 0;
      boolean partialSuccess =
          executeResult.succeededBatches() > 0 && executeResult.failedBatches() > 0;
      boolean allFailed = executeResult.succeededBatches() == 0;

      if (allSucceeded) {
        // 3.1 All succeeded: advance cursor
        log.debug(
            "all batches succeeded, attempting cursor advancement taskId={} runId={}",
            taskId,
            runId);
        boolean cursorAdvanced = false;
        // Query last succeeded batch ID for lineage tracking
        Long lastBatchId = taskRunBatchRepository.findLastSucceededBatchId(runId).orElse(null);

        try {
          cursorAdvanced = cursorAdvancer.advance(context, taskId, runId, lastBatchId);
        } catch (org.springframework.dao.OptimisticLockingFailureException e) {
          // Optimistic lock conflict - cursor version mismatch (retryable)
          log.warn("cursor advancement conflict (will retry) taskId={} runId={}", taskId, runId, e);
        }
        // Other exceptions (e.g., event save failure) propagate → transaction rollback

        if (cursorAdvanced) {
          // Cursor ok → SUCCEEDED
          task.markSucceeded(now);
          taskRun.succeed(now);
          log.info("task succeeded taskId={} runId={}", taskId, runId);
        } else {
          // Cursor failed → Task: FAILED, TaskRun: PARTIAL (retryable with checkpoint)
          task.markFailed(now);
          taskRun.markPartial("Cursor advancement failed", now);
          log.warn(
              "task marked FAILED (cursor failed), run marked PARTIAL taskId={} runId={}",
              taskId,
              runId);
        }

      } else if (partialSuccess) {
        // 3.2 Partial success → Task: FAILED, TaskRun: PARTIAL
        task.markFailed(now);
        taskRun.markPartial("Some batches failed", now);
        log.warn(
            "task marked FAILED (partial success), run marked PARTIAL taskId={} runId={}",
            taskId,
            runId);

      } else if (allFailed) {
        // 3.3 All failed → FAILED
        task.markFailed(now);
        taskRun.fail("All batches failed", now);
        log.warn("task marked FAILED taskId={} runId={}", taskId, runId);

      } else {
        // 3.4 No batches executed (totalBatches == 0) → FAILED
        task.markFailed(now);
        taskRun.fail("No batches executed", now);
        log.warn("task marked FAILED (no batches) taskId={} runId={}", taskId, runId);
      }

      // 4) Aggregate batch stats to TaskRun
      if (executeResult.succeededBatches() > 0 || executeResult.failedBatches() > 0) {
        List<TaskRunBatch> batches = taskRunBatchRepository.findByRunId(runId);
        int totalPages = batches.size();
        long totalFetched =
            batches.stream()
                .map(TaskRunBatch::getStats)
                .filter(Objects::nonNull)
                .mapToLong(BatchStats::recordCount)
                .sum();

        long failedCount =
            batches.stream().filter(batch -> batch.getStatus() == BatchStatus.FAILED).count();

        RunStats totalStats =
            new RunStats(
                totalFetched, // fetched: total record count
                totalFetched, // upserted: same as fetched (for now)
                failedCount, // failed: failed batch count
                (long) totalPages); // pages: total batch count

        taskRun.appendStats(totalStats);

        if (log.isDebugEnabled()) {
          log.debug(
              "Aggregated stats from {} batches: fetched={} failed={} taskId={} runId={}",
              totalPages,
              totalFetched,
              failedCount,
              taskId,
              runId);
        }
      }

      // 5) Persist Task and TaskRun
      taskRepository.save(task);
      taskRunRepository.save(taskRun);

      // Pull domain events and publish as Spring events
      task.pullDomainEvents()
          .forEach(
              domainEvent -> {
                if (domainEvent instanceof TaskCompletedEvent event) {
                  applicationEventPublisher.publishEvent(event);
                  log.debug(
                      "Published TaskCompletedEvent for taskId={} sliceId={} planId={}",
                      event.taskId(),
                      event.sliceId(),
                      event.planId());
                }
              });

      if (executeResult.succeededBatches() > 0) {
        log.debug("publishing publication ready event taskId={} runId={}", taskId, runId);
        publishPublicationReadyEvent(taskId, runId, context);
      }

      log.info(
          "complete task execution finished taskId={} runId={} finalStatus={}",
          taskId,
          runId,
          task.getStatus());

    } finally {
      // 6) Cleanup regardless of outcome
      cleanupResources(session);
    }
  }

  private void publishPublicationReadyEvent(Long taskId, Long runId, ExecutionContext context) {
    List<TaskRunBatch> batches = taskRunBatchRepository.findByRunId(runId);
    if (batches == null || batches.isEmpty()) {
      return;
    }

    List<TaskRunBatch> succeededBatches =
        batches.stream()
            .filter(batch -> batch.getStatus() == BatchStatus.SUCCEEDED)
            .filter(batch -> StringUtils.hasText(batch.getStorageKey()))
            .toList();

    if (succeededBatches.isEmpty()) {
      return;
    }

    List<String> storageKeys =
        succeededBatches.stream()
            .map(TaskRunBatch::getStorageKey)
            .filter(StringUtils::hasText)
            .distinct()
            .toList();

    if (storageKeys.isEmpty()) {
      return;
    }

    int totalPublicationCount =
        succeededBatches.stream()
            .map(TaskRunBatch::getStats)
            .filter(Objects::nonNull)
            .mapToInt(BatchStats::recordCount)
            .sum();

    int failedBatchCount =
        (int) batches.stream().filter(batch -> batch.getStatus() == BatchStatus.FAILED).count();

    PublicationDataReadyEvent event =
        PublicationDataReadyEvent.builder()
            .taskId(taskId)
            .runId(runId)
            .provenanceCode(context.provenanceCode())
            .storageKeys(List.copyOf(storageKeys))
            .totalPublicationCount(totalPublicationCount)
            .successBatchCount(succeededBatches.size())
            .failedBatchCount(failedBatchCount)
            .timestamp(clock.instant().toEpochMilli())
            .build();

    publicationEventPublisher.publish(event);

    log.info(
        "publication data ready event queued taskId={} runId={} storageKeyCount={} totalCount={}",
        taskId,
        runId,
        storageKeys.size(),
        totalPublicationCount);
  }

  /// Cleanup resources (stop heartbeat, release lease).
  private void cleanupResources(ExecutionSession session) {
    Long taskId = session.taskId();
    String leaseOwner = session.leaseOwner();

    log.debug("cleaning up execution resources taskId={} owner={}", taskId, leaseOwner);
    try {
      // Stop heartbeat
      session.cleanup();
      log.info("heartbeat stopped taskId={} owner={}", taskId, leaseOwner);

      // Release lease
      leaseManagementService.releaseLease(taskId);
      log.info("lease released taskId={} owner={}", taskId, leaseOwner);

    } catch (Exception e) {
      log.error("cleanup failed taskId={} owner={}", taskId, leaseOwner, e);
      // Cleanup failure does not affect completion; log only
    }
  }
}
