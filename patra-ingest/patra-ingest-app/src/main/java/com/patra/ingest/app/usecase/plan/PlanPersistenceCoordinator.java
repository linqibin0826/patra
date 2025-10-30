package com.patra.ingest.app.usecase.plan;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import com.patra.ingest.app.usecase.plan.command.PlanIngestionCommand;
import com.patra.ingest.domain.exception.PlanPersistenceException;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.domain.port.ScheduleInstanceRepository;
import com.patra.ingest.domain.port.TaskRepository;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Coordinator for plan persistence operations.
 *
 * <p>Responsible for safely persisting plan aggregates, slices, tasks, and schedule instances with
 * proper exception handling and logging.
 *
 * <p>Note: This coordinator does NOT use {@code @Transactional}. It relies on the outer transaction
 * boundary from the main orchestrator to ensure atomicity with event publishing.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanPersistenceCoordinator {

  private final PlanRepository planRepository;
  private final PlanSliceRepository planSliceRepository;
  private final TaskRepository taskRepository;
  private final ScheduleInstanceRepository scheduleInstanceRepository;

  /**
   * Saves or updates schedule instance (idempotent).
   *
   * @param request schedule request
   * @return persisted schedule instance
   * @throws PlanPersistenceException if storage fails
   */
  public ScheduleInstanceAggregate persistScheduleInstance(PlanIngestionCommand request) {
    ScheduleInstanceAggregate schedule =
        ScheduleInstanceAggregate.start(
            request.scheduler(),
            request.schedulerJobId(),
            request.schedulerLogId(),
            request.triggerType(),
            request.triggeredAt(),
            request.triggerParams(),
            request.provenanceCode().getCode());
    try {
      return scheduleInstanceRepository.saveOrUpdateInstance(schedule);
    } catch (RuntimeException ex) {
      throw new PlanPersistenceException(
          PlanPersistenceException.Stage.SCHEDULE_INSTANCE,
          "Failed to persist schedule instance",
          ex);
    }
  }

  /**
   * Persists plan aggregate and wraps underlying exceptions.
   *
   * @param draftPlan draft plan aggregate
   * @return persisted plan aggregate
   * @throws PlanPersistenceException if persistence fails
   */
  public PlanAggregate savePlan(PlanAggregate draftPlan) {
    try {
      return planRepository.save(draftPlan);
    } catch (RuntimeException ex) {
      throw new PlanPersistenceException(
          PlanPersistenceException.Stage.PLAN, "Failed to persist plan aggregate", ex);
    }
  }

  /**
   * Batch persists plan slice aggregates.
   *
   * @param plan plan aggregate (already persisted)
   * @param slices slice collection
   * @return persisted slice collection
   * @throws PlanPersistenceException if persistence fails
   */
  public List<PlanSliceAggregate> persistSlices(
      PlanAggregate plan, List<PlanSliceAggregate> slices) {
    if (CollUtil.isEmpty(slices)) {
      return List.of();
    }
    slices.forEach(slice -> slice.bindPlan(plan.getId()));
    try {
      return planSliceRepository.saveAll(slices);
    } catch (RuntimeException ex) {
      throw new PlanPersistenceException(
          PlanPersistenceException.Stage.PLAN_SLICE, "Failed to persist plan slices", ex);
    }
  }

  /**
   * Batch persists task aggregates and binds plan and slice IDs.
   *
   * @param plan plan aggregate
   * @param persistedSlices persisted slices
   * @param tasks task collection
   * @return persisted task collection
   * @throws PlanPersistenceException if persistence fails
   */
  public List<TaskAggregate> persistTasks(
      PlanAggregate plan, List<PlanSliceAggregate> persistedSlices, List<TaskAggregate> tasks) {
    if (CollUtil.isEmpty(tasks)) {
      return List.of();
    }
    Map<Integer, PlanSliceAggregate> sliceBySeq = MapUtil.newHashMap(persistedSlices.size());
    for (PlanSliceAggregate slice : persistedSlices) {
      sliceBySeq.putIfAbsent(slice.getSliceNo(), slice);
    }
    for (TaskAggregate task : tasks) {
      Long placeholderSequence = task.getSliceId();
      PlanSliceAggregate slice =
          ObjectUtil.isNull(placeholderSequence)
              ? null
              : sliceBySeq.get(placeholderSequence.intValue());
      task.bindPlanAndSlice(plan.getId(), slice == null ? null : slice.getId());
    }
    try {
      return taskRepository.saveAll(tasks);
    } catch (RuntimeException ex) {
      throw new PlanPersistenceException(
          PlanPersistenceException.Stage.TASK, "Failed to persist tasks", ex);
    }
  }

  /**
   * Persists task retry state.
   *
   * @param task task aggregate
   * @throws PlanPersistenceException if persistence fails
   */
  public void saveTask(TaskAggregate task) {
    try {
      taskRepository.save(task);
    } catch (RuntimeException ex) {
      throw new PlanPersistenceException(
          PlanPersistenceException.Stage.TASK_RETRY, "Failed to persist task retry state", ex);
    }
  }
}
