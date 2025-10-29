package com.patra.ingest.app.eventhandler;

import com.patra.ingest.domain.event.SliceStatusChangedEvent;
import com.patra.ingest.domain.event.TaskCompletedEvent;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.domain.port.TaskRepository;
import com.patra.ingest.domain.service.SliceStatusCalculator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Handles TaskCompletedEvent to update Slice status.
 *
 * <p>When a Task completes (SUCCEEDED/FAILED/PARTIAL/CURSOR_PENDING), this handler aggregates the
 * statuses of all Tasks in the Slice and updates the Slice status accordingly.
 *
 * <p>If the Slice status changes, it publishes a SliceStatusChangedEvent to trigger Plan status
 * update.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskCompletedEventHandler {

  private final TaskRepository taskRepository;
  private final PlanSliceRepository sliceRepository;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * Handles TaskCompletedEvent after the transaction commits.
   *
   * @param event the task completed event
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(TaskCompletedEvent event) {
    try {
      log.debug(
          "Handling TaskCompletedEvent for taskId={} sliceId={} planId={} status={}",
          event.taskId(),
          event.sliceId(),
          event.planId(),
          event.status());

      // 1. Query all tasks in the slice
      List<TaskAggregate> tasks = taskRepository.findBySliceId(event.sliceId());
      List<TaskStatus> taskStatuses = tasks.stream().map(TaskAggregate::getStatus).toList();

      // 2. Aggregate slice status using Domain Service
      SliceStatus newStatus = SliceStatusCalculator.calculate(taskStatuses);

      // 3. Update slice status
      PlanSliceAggregate slice =
          sliceRepository
              .findById(event.sliceId())
              .orElseThrow(
                  () -> new IllegalStateException("Slice not found: sliceId=" + event.sliceId()));

      SliceStatus oldStatus = slice.getStatus();

      // 4. Check if status changed (idempotency)
      if (oldStatus == newStatus) {
        log.debug("Slice status unchanged, skip update sliceId={}", event.sliceId());
        return;
      }

      // 5. Update and save
      slice.updateStatus(newStatus);
      sliceRepository.save(slice);

      log.info("Slice status updated sliceId={} {} -> {}", event.sliceId(), oldStatus, newStatus);

      // 6. Publish SliceStatusChangedEvent if status changed
      SliceStatusChangedEvent sliceEvent =
          SliceStatusChangedEvent.of(
              event.sliceId(), event.planId(), oldStatus.getCode(), newStatus.getCode());
      eventPublisher.publishEvent(sliceEvent);

      log.debug(
          "Published SliceStatusChangedEvent for sliceId={} planId={}",
          event.sliceId(),
          event.planId());

    } catch (OptimisticLockingFailureException e) {
      // Optimistic lock conflict: another thread already updated, skip this one
      log.warn("Optimistic lock conflict on Slice update, skip sliceId={}", event.sliceId());
    } catch (Exception e) {
      // Other exceptions: log error, rely on reconciliation task to fix
      log.error(
          "Failed to handle TaskCompletedEvent for taskId={} sliceId={} planId={}",
          event.taskId(),
          event.sliceId(),
          event.planId(),
          e);
    }
  }
}
