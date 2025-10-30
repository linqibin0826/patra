package com.patra.ingest.app.usecase.plan;

import cn.hutool.core.collection.CollUtil;
import com.patra.ingest.app.usecase.plan.dto.PlanIngestionResult;
import com.patra.ingest.app.usecase.plan.publisher.TaskOutboxPublisher;
import com.patra.ingest.domain.event.TaskQueuedEvent;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Coordinator for plan publishing operations.
 *
 * <p>Responsible for collecting domain events from task aggregates and publishing them via the
 * Outbox pattern.
 *
 * <p>Note: This coordinator does NOT use {@code @Transactional}. It relies on the outer transaction
 * boundary from the main orchestrator to ensure atomicity with persistence.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanPublishingCoordinator {

  private final TaskOutboxPublisher taskOutboxPublisher;

  /**
   * Publishes task queued events for new plans.
   *
   * @param queuedEvents task queued events
   * @param plan plan aggregate
   * @param schedule schedule instance
   */
  public void publishNewPlanEvents(
      List<TaskQueuedEvent> queuedEvents, PlanAggregate plan, ScheduleInstanceAggregate schedule) {
    log.debug(
        "Publishing {} task-ready events to outbox for plan [{}]",
        queuedEvents.size(),
        plan.getId());
    taskOutboxPublisher.publish(queuedEvents, plan, schedule);
  }

  /**
   * Publishes retry events for existing plans.
   *
   * @param retryEvents retry events
   * @param plan plan aggregate
   * @param schedule schedule instance
   */
  public void publishRetryEvents(
      List<TaskQueuedEvent> retryEvents, PlanAggregate plan, ScheduleInstanceAggregate schedule) {
    taskOutboxPublisher.publishRetry(retryEvents, plan, schedule);
    log.info("Re-queued {} failed tasks for existing plan [{}]", retryEvents.size(), plan.getId());
  }

  /**
   * Collects TaskQueuedEvent instances from task aggregates.
   *
   * <p>Explicitly triggers {@link TaskAggregate#raiseQueuedEvent()} to ensure events exist.
   *
   * @param tasks task collection
   * @return list of queued events (empty list if no tasks)
   */
  public List<TaskQueuedEvent> collectQueuedEvents(List<TaskAggregate> tasks) {
    if (CollUtil.isEmpty(tasks)) {
      return List.of();
    }
    List<TaskQueuedEvent> events = new ArrayList<>(tasks.size());
    for (TaskAggregate task : tasks) {
      task.raiseQueuedEvent();
      task.pullDomainEvents().stream()
          .filter(TaskQueuedEvent.class::isInstance)
          .map(TaskQueuedEvent.class::cast)
          .forEach(events::add);
    }
    return events;
  }

  /**
   * Builds plan ingestion result from plan data.
   *
   * @param schedule schedule instance
   * @param plan plan aggregate
   * @param slices plan slices
   * @param tasks tasks
   * @return plan ingestion result
   */
  public PlanIngestionResult buildIngestionResult(
      ScheduleInstanceAggregate schedule,
      PlanAggregate plan,
      List<PlanSliceAggregate> slices,
      List<TaskAggregate> tasks) {
    return new PlanIngestionResult(
        schedule.getId(),
        plan.getId(),
        slices.stream().map(PlanSliceAggregate::getId).collect(Collectors.toList()),
        tasks.size(),
        plan.getStatus().name());
  }

  /**
   * Builds plan ingestion result from assembly status.
   *
   * @param schedule schedule instance
   * @param plan plan aggregate
   * @param slices plan slices
   * @param tasksCount task count
   * @param statusName status name
   * @return plan ingestion result
   */
  public PlanIngestionResult buildIngestionResult(
      ScheduleInstanceAggregate schedule,
      PlanAggregate plan,
      List<PlanSliceAggregate> slices,
      int tasksCount,
      String statusName) {
    return new PlanIngestionResult(
        schedule.getId(),
        plan.getId(),
        slices.stream().map(PlanSliceAggregate::getId).collect(Collectors.toList()),
        tasksCount,
        statusName);
  }
}
