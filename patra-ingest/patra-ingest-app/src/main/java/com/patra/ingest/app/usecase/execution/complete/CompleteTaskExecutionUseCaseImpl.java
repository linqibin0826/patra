package com.patra.ingest.app.usecase.execution.complete;

import com.patra.ingest.app.usecase.execution.batch.ExecuteTaskBatchesUseCase;
import com.patra.ingest.app.usecase.execution.cursor.CursorAdvancer;
import com.patra.ingest.app.usecase.execution.lease.LeaseManagementService;
import com.patra.ingest.app.usecase.execution.publisher.LiteratureEventPublisher;
import com.patra.ingest.app.usecase.execution.session.ExecutionSession;
import com.patra.ingest.domain.event.LiteratureDataReadyEvent;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.entity.TaskRun;
import com.patra.ingest.domain.model.entity.TaskRunBatch;
import com.patra.ingest.domain.model.enums.BatchStatus;
import com.patra.ingest.domain.model.vo.BatchStats;
import com.patra.ingest.domain.model.vo.ExecutionContext;
import com.patra.ingest.domain.port.TaskRepository;
import com.patra.ingest.domain.port.TaskRunBatchRepository;
import com.patra.ingest.domain.port.TaskRunRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Complete phase use case implementation.
 *
 * <p>Responsibility: cursor advancement → status decision → Task/TaskRun update → resource cleanup
 * (heartbeat/lease).
 *
 * <p>Design notes:
 *
 * <ul>
 *   <li>Status decision:
 *       <ul>
 *         <li>all succeeded + cursor advanced → SUCCEEDED
 *         <li>all succeeded + cursor failed → CURSOR_PENDING
 *         <li>partial success (failed > 0 && succeeded > 0) → PARTIAL
 *         <li>all failed (succeeded == 0) → FAILED
 *       </ul>
 *   <li>Advance cursor only when all batches succeeded; record reason on failure.
 *   <li>On optimistic conflict, mark CURSOR_PENDING for async retry.
 *   <li>Cleanup: stop heartbeat and release lease regardless of outcome.
 * </ul>
 *
 * <p>Logging:
 *
 * <ul>
 *   <li>INFO: cursor advanced, task completed (final status).
 *   <li>WARN: cursor failed, partial/failed states.
 * </ul>
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
  private final TaskRunBatchRepository taskRunBatchRepository;
  private final CursorAdvancer cursorAdvancer;
  private final LeaseManagementService leaseManagementService;
  private final LiteratureEventPublisher literatureEventPublisher;
  private final Clock clock;

  /** Completes execution (advance cursor + update status). */
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
          // Cursor failed → CURSOR_PENDING
          task.markCursorPending(now);
          taskRun.markCursorPending(now);
          log.warn("task marked CURSOR_PENDING taskId={} runId={}", taskId, runId);
        }

      } else if (partialSuccess) {
        // 3.2 Partial → PARTIAL
        task.markPartial(now);
        taskRun.markPartial("Some batches failed", now);
        log.warn("task marked PARTIAL taskId={} runId={}", taskId, runId);

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

      // 4) Persist Task and TaskRun
      taskRepository.save(task);
      taskRunRepository.save(taskRun);

      if (executeResult.succeededBatches() > 0) {
        log.debug("publishing literature ready event taskId={} runId={}", taskId, runId);
        publishLiteratureReadyEvent(taskId, runId, context);
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

  private void publishLiteratureReadyEvent(Long taskId, Long runId, ExecutionContext context) {
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

    int totalLiteratureCount =
        succeededBatches.stream()
            .map(TaskRunBatch::getStats)
            .filter(Objects::nonNull)
            .mapToInt(BatchStats::recordCount)
            .sum();

    int failedBatchCount =
        (int) batches.stream().filter(batch -> batch.getStatus() == BatchStatus.FAILED).count();

    LiteratureDataReadyEvent event =
        LiteratureDataReadyEvent.builder()
            .taskId(taskId)
            .runId(runId)
            .provenanceCode(context.provenanceCode())
            .storageKeys(List.copyOf(storageKeys))
            .totalLiteratureCount(totalLiteratureCount)
            .successBatchCount(succeededBatches.size())
            .failedBatchCount(failedBatchCount)
            .timestamp(clock.instant().toEpochMilli())
            .build();

    literatureEventPublisher.publish(event);

    log.info(
        "literature data ready event queued taskId={} runId={} storageKeyCount={} totalCount={}",
        taskId,
        runId,
        storageKeys.size(),
        totalLiteratureCount);
  }

  /** Cleanup resources (stop heartbeat, release lease). */
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
