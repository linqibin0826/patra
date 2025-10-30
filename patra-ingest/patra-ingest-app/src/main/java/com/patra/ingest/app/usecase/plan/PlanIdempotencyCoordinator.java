package com.patra.ingest.app.usecase.plan;

import com.patra.ingest.app.usecase.plan.dto.PlanIngestionResult;
import com.patra.ingest.domain.event.TaskQueuedEvent;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.domain.port.TaskRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Coordinator for plan idempotency and retry logic.
 *
 * <p>Responsible for handling duplicate plan detection, identifying tasks eligible for retry, and
 * coordinating retry operations.
 *
 * <p>Note: This coordinator does NOT use {@code @Transactional}. It relies on the outer transaction
 * boundary from the main orchestrator to ensure atomicity with persistence and publishing.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanIdempotencyCoordinator {

  private final PlanSliceRepository planSliceRepository;
  private final TaskRepository taskRepository;
  private final PlanPersistenceCoordinator persistenceCoordinator;
  private final PlanPublishingCoordinator publishingCoordinator;

  /**
   * Handles idempotent plan reuse when an existing plan with the same planKey is found.
   *
   * <p>This method:
   *
   * <ol>
   *   <li>Loads existing slices and tasks
   *   <li>Identifies tasks eligible for retry (FAILED status)
   *   <li>Resets and re-queues retry tasks
   *   <li>Publishes retry events via outbox
   *   <li>Returns result based on existing plan
   * </ol>
   *
   * @param existingPlan the existing plan aggregate
   * @param schedule the current schedule instance
   * @param planKey the idempotency key for logging
   * @return ingestion result based on existing plan
   */
  public PlanIngestionResult handleIdempotentPlanReuse(
      PlanAggregate existingPlan, ScheduleInstanceAggregate schedule, String planKey) {
    logDuplicatePlanDetection(existingPlan, planKey);

    List<PlanSliceAggregate> existingSlices =
        planSliceRepository.findByPlanId(existingPlan.getId());
    List<TaskAggregate> existingTasks = taskRepository.findByPlanId(existingPlan.getId());
    List<TaskAggregate> retryTasks = prepareTasksForRetry(existingTasks);

    if (!retryTasks.isEmpty()) {
      processRetryTasks(existingPlan, schedule, retryTasks);
    } else {
      log.info(
          "No tasks require retry for existing plan [{}], returning existing state",
          existingPlan.getId());
    }

    return publishingCoordinator.buildIngestionResult(
        schedule, existingPlan, existingSlices, existingTasks);
  }

  /**
   * Logs duplicate plan detection.
   *
   * @param existingPlan existing plan aggregate
   * @param planKey plan idempotency key
   */
  private void logDuplicatePlanDetection(PlanAggregate existingPlan, String planKey) {
    log.info(
        "Detected duplicate plan ingestion for provenance [{}] operation [{}]: reusing existing plan [{}] with planKey={}",
        existingPlan.getProvenanceCode(),
        existingPlan.getOperationCode(),
        existingPlan.getId(),
        planKey);
  }

  /**
   * Processes retry tasks by publishing retry events.
   *
   * <p>Note: After refactoring, Plan status is not changed during retry. Plan remains in its
   * current state (typically READY) while failed tasks are re-queued.
   *
   * @param existingPlan existing plan aggregate
   * @param schedule schedule instance
   * @param retryTasks tasks to retry
   */
  private void processRetryTasks(
      PlanAggregate existingPlan,
      ScheduleInstanceAggregate schedule,
      List<TaskAggregate> retryTasks) {
    // Plan status unchanged - tasks are simply re-queued for execution
    List<TaskQueuedEvent> retryEvents = publishingCoordinator.collectQueuedEvents(retryTasks);
    publishingCoordinator.publishRetryEvents(retryEvents, existingPlan, schedule);
  }

  /**
   * Prepares tasks for retry by resetting failed tasks.
   *
   * @param tasks the list of tasks to check
   * @return list of tasks that were prepared for retry
   */
  private List<TaskAggregate> prepareTasksForRetry(List<TaskAggregate> tasks) {
    List<TaskAggregate> retryTasks = new ArrayList<>();
    for (TaskAggregate task : tasks) {
      if (shouldRetry(task)) {
        task.prepareForRetry();
        persistenceCoordinator.saveTask(task);
        retryTasks.add(task);
      }
    }
    return retryTasks;
  }

  /**
   * Determines if task is eligible for compensation retry.
   *
   * <p>Note: After refactoring, only FAILED status is checked.
   *
   * @param task task aggregate
   * @return true if retry is needed (FAILED status only)
   */
  private boolean shouldRetry(TaskAggregate task) {
    TaskStatus status = task.getStatus();
    return status == TaskStatus.FAILED;
  }
}
