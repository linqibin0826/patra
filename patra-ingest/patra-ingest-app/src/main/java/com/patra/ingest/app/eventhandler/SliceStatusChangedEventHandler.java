package com.patra.ingest.app.eventhandler;

import com.patra.ingest.domain.event.SliceStatusChangedEvent;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.domain.service.PlanStatusCalculator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Handles SliceStatusChangedEvent to update Plan status.
 *
 * <p>When a Slice status changes, this handler aggregates the statuses of all Slices in the Plan
 * and updates the Plan status accordingly.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SliceStatusChangedEventHandler {

  private final PlanSliceRepository sliceRepository;
  private final PlanRepository planRepository;

  /**
   * Handles SliceStatusChangedEvent after the transaction commits.
   *
   * @param event the slice status changed event
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(SliceStatusChangedEvent event) {
    try {
      log.debug(
          "Handling SliceStatusChangedEvent for sliceId={} planId={} {} -> {}",
          event.sliceId(),
          event.planId(),
          event.oldStatus(),
          event.newStatus());

      // 1. Query all slices in the plan
      List<PlanSliceAggregate> slices = sliceRepository.findByPlanId(event.planId());
      List<SliceStatus> sliceStatuses = slices.stream().map(PlanSliceAggregate::getStatus).toList();

      // 2. Load plan aggregate
      PlanAggregate plan =
          planRepository
              .findById(event.planId())
              .orElseThrow(
                  () -> new IllegalStateException("Plan not found: planId=" + event.planId()));

      PlanStatus oldStatus = plan.getStatus();

      // 3. Aggregate plan status using Domain Service
      PlanStatus newStatus = PlanStatusCalculator.calculate(sliceStatuses, oldStatus);

      // 4. Check if status changed (idempotency)
      if (oldStatus == newStatus) {
        log.debug("Plan status unchanged, skip update planId={}", event.planId());
        return;
      }

      // 5. Update and save
      plan.updateStatus(newStatus);
      planRepository.save(plan);

      log.info("Plan status updated planId={} {} -> {}", event.planId(), oldStatus, newStatus);

    } catch (OptimisticLockingFailureException e) {
      // Optimistic lock conflict: another thread already updated, skip this one
      log.warn("Optimistic lock conflict on Plan update, skip planId={}", event.planId());
    } catch (Exception e) {
      // Other exceptions: log error, rely on reconciliation task to fix
      log.error(
          "Failed to handle SliceStatusChangedEvent for sliceId={} planId={}",
          event.sliceId(),
          event.planId(),
          e);
    }
  }
}
