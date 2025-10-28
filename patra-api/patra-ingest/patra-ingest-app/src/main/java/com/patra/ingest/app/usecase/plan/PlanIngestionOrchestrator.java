package com.patra.ingest.app.usecase.plan;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.app.usecase.plan.assembler.PlanAssembler;
import com.patra.ingest.app.usecase.plan.assembler.PlanAssemblyRequest;
import com.patra.ingest.app.usecase.plan.command.PlanIngestionCommand;
import com.patra.ingest.app.usecase.plan.dto.PlanAssemblyResult;
import com.patra.ingest.app.usecase.plan.dto.PlanIngestionResult;
import com.patra.ingest.app.usecase.plan.expression.PlanExpressionBuilder;
import com.patra.ingest.app.usecase.plan.expression.PlanExpressionDescriptor;
import com.patra.ingest.app.usecase.plan.publisher.TaskOutboxPublisher;
import com.patra.ingest.app.usecase.plan.validator.PlannerValidator;
import com.patra.ingest.app.usecase.plan.window.PlanningWindowResolver;
import com.patra.ingest.domain.event.TaskQueuedEvent;
import com.patra.ingest.domain.exception.PlanAssemblyException;
import com.patra.ingest.domain.exception.PlanPersistenceException;
import com.patra.ingest.domain.exception.PlanValidationException;
import com.patra.ingest.domain.model.aggregate.*;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.PlanTriggerNorm;
import com.patra.ingest.domain.model.vo.PlannerWindow;
import com.patra.ingest.domain.port.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core application service for plan orchestration. Handles calls from the scheduling layer
 * (cron/manual triggers) and performs:
 *
 * <ul>
 *   <li>Persist schedule instance and load provenance config snapshot
 *   <li>Query cursor watermark and resolve execution window (time-window strategy)
 *   <li>Build plan expression and run pre-validations
 *   <li>Assemble and persist plan/slices/tasks (with idempotency and compensation)
 *   <li>Collect task enqueued events and publish via the Outbox pattern
 * </ul>
 *
 * This orchestrator (Application Service) focuses on flow orchestration, exception mapping,
 * idempotency, and observability. Domain rules are enforced by domain objects and validators.
 *
 * <p>Logging: INFO for one-shot results and key branch hits; DEBUG for window/expression
 * diagnostics.
 *
 * <p>Thread-safety: no shared mutable state; relies on thread-safe Spring beans
 * (repositories/builders) and is safe for concurrent use.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator implements PlanIngestionUseCase {

  /** Port for querying provenance configuration from the upstream registry service. */
  private final PatraRegistryPort patraRegistryPort;

  /** Cursor repository: retrieves the latest global time watermark (window starting point). */
  private final CursorRepository cursorRepository;

  /** Task repository: supports deduplication statistics and batch persistence. */
  private final TaskRepository taskRepository;

  /**
   * Planning window resolver: computes this run's window from trigger spec, config, and cursor
   * watermark.
   */
  private final PlanningWindowResolver planningWindowResolver;

  /**
   * Pre-orchestration validator: validates window sanity, queue pressure, and capacity constraints.
   */
  private final PlannerValidator plannerValidator;

  /** Plan assembler: assembles Plan / Slice / Task blueprints (without persistence). */
  private final PlanAssembler planAssembler;

  /**
   * Outbox publisher for tasks: converts queued events to Outbox messages and persists/publishes
   * them.
   */
  private final TaskOutboxPublisher taskOutboxPublisher;

  /**
   * Plan expression builder: generates plan-level expression descriptors (uncompiled snapshot and
   * hash only).
   */
  private final PlanExpressionBuilder planExpressionBuilder;

  /**
   * Schedule instance repository: saves/updates trigger records to support idempotency tracking.
   */
  private final ScheduleInstanceRepository scheduleInstanceRepository;

  /** Plan repository: persist or load plan aggregates; supports idempotency by planKey. */
  private final PlanRepository planRepository;

  /** Plan-slice repository: batch-persist plan slice aggregates. */
  private final PlanSliceRepository planSliceRepository;

  /**
   * Main plan orchestration flow (entry method).
   *
   * <p>Consists of six phases:
   *
   * <ol>
   *   <li>Persist schedule instance and load provenance config snapshot
   *   <li>Query cursor watermark and resolve time window
   *   <li>Build plan expression
   *   <li>Pre-validation (window / backpressure / capacity)
   *   <li>Assemble plan (with idempotent reuse and compensation retries)
   *   <li>Persist and publish task enqueued events
   * </ol>
   *
   * Exception handling: convert runtime exceptions into semantic Plan*Exception types for uniform
   * error-code mapping.
   *
   * @param request scheduling request (provenance, operation, window bounds, priority, trigger
   *     info, etc.)
   * @return summary of plan execution
   */
  @Override
  @Transactional
  public PlanIngestionResult ingestPlan(PlanIngestionCommand request) {
    logPlanIngestionStart(request);

    PlanningContext context = preparePlanningContext(request);
    PlanExpressionDescriptor expressionDescriptor = buildPlanExpression(context);
    performPreValidation(context);

    PlanAssemblyResult assembly = assembleAndValidatePlan(context, expressionDescriptor);
    PlanAggregate existingPlan = checkForExistingPlan(assembly.plan());

    if (existingPlan != null) {
      return handleIdempotentPlanReuse(
          existingPlan, context.schedule(), assembly.plan().getPlanKey());
    }

    return persistAndPublishNewPlan(
        assembly.plan(), assembly, context.schedule(), context.window());
  }

  /**
   * Logs the start of plan ingestion with key request details.
   *
   * @param request plan ingestion command
   */
  private void logPlanIngestionStart(PlanIngestionCommand request) {
    log.info(
        "Starting plan ingestion for provenance [{}] operation [{}]: triggered at {} by scheduler [{}]",
        request.provenanceCode(),
        request.operationCode(),
        request.triggeredAt(),
        request.scheduler());
  }

  /**
   * Prepares planning context by loading configuration and resolving window.
   *
   * @param request plan ingestion command
   * @return planning context with schedule, config, norm, and window
   */
  private PlanningContext preparePlanningContext(PlanIngestionCommand request) {
    log.debug(
        "Preparing planning context for provenance [{}] operation [{}]",
        request.provenanceCode(),
        request.operationCode());

    ScheduleInstanceAggregate schedule = persistScheduleInstanceSafely(request);
    ProvenanceConfigSnapshot configSnapshot =
        patraRegistryPort.fetchConfig(request.provenanceCode(), request.operationCode());
    PlanTriggerNorm norm = buildTriggerNorm(schedule, request);

    Instant cursorWatermark =
        lookupCursorWatermark(request.provenanceCode(), request.operationCode());
    log.debug(
        "Retrieved cursor watermark for provenance [{}] operation [{}]: {}",
        request.provenanceCode(),
        request.operationCode(),
        cursorWatermark);

    PlannerWindow window =
        resolvePlannerWindow(norm, configSnapshot, cursorWatermark, request.triggeredAt());
    logWindowResolution(request.provenanceCode(), request.operationCode(), cursorWatermark, window);

    return new PlanningContext(
        schedule, configSnapshot, norm, window, request.provenanceCode(), request.operationCode());
  }

  /**
   * Builds plan expression descriptor and logs result.
   *
   * @param context planning context
   * @return plan expression descriptor
   */
  private PlanExpressionDescriptor buildPlanExpression(PlanningContext context) {
    PlanExpressionDescriptor descriptor =
        planExpressionBuilder.build(context.norm(), context.configSnapshot());
    log.debug(
        "Built plan expression for provenance [{}] operation [{}]: hash={}, snapshot size={} bytes",
        context.provenanceCode(),
        context.operationCode(),
        descriptor.hash(),
        descriptor.jsonSnapshot().length());
    return descriptor;
  }

  /**
   * Performs pre-assembly validation and logs result.
   *
   * @param context planning context
   * @throws PlanValidationException if validation fails
   */
  private void performPreValidation(PlanningContext context) {
    long queuedTasks =
        taskRepository.countQueuedTasks(
            context.provenanceCode().getCode(), opCode(context.operationCode()));
    validateBeforeAssemble(context.norm(), context.configSnapshot(), context.window(), queuedTasks);
    log.debug(
        "Pre-validation passed for provenance [{}] operation [{}]: {} tasks currently queued",
        context.provenanceCode(),
        context.operationCode(),
        queuedTasks);
  }

  /**
   * Assembles plan and validates result.
   *
   * @param context planning context
   * @param expressionDescriptor plan expression descriptor
   * @return assembly result
   * @throws PlanAssemblyException if assembly fails
   */
  private PlanAssemblyResult assembleAndValidatePlan(
      PlanningContext context, PlanExpressionDescriptor expressionDescriptor) {
    log.debug(
        "Starting plan assembly for provenance [{}] operation [{}]",
        context.provenanceCode(),
        context.operationCode());

    PlanAssemblyRequest assemblyRequest =
        new PlanAssemblyRequest(
            context.norm(), context.window(), context.configSnapshot(), expressionDescriptor);
    PlanAssemblyResult result = assemblePlan(assemblyRequest);

    log.debug(
        "Plan assembly completed for provenance [{}] operation [{}]: status={}, slices={}, tasks={}",
        context.provenanceCode(),
        context.operationCode(),
        result.status(),
        result.slices().size(),
        result.tasks().size());

    return result;
  }

  /**
   * Checks for existing plan by planKey.
   *
   * @param draftPlan draft plan aggregate
   * @return existing plan if found, null otherwise
   */
  private PlanAggregate checkForExistingPlan(PlanAggregate draftPlan) {
    return planRepository.findByPlanKey(draftPlan.getPlanKey()).orElse(null);
  }

  /**
   * Returns the operation code string.
   *
   * @param op domain operation enum
   * @return operation code; null when op is null
   */
  private String opCode(OperationCode op) {
    return op == null ? null : op.getCode();
  }

  /**
   * Internal record holding planning context data.
   *
   * @param schedule schedule instance aggregate
   * @param configSnapshot provenance configuration snapshot
   * @param norm plan trigger norm
   * @param window planner window
   * @param provenanceCode provenance code enum
   * @param operationCode operation code enum
   */
  private record PlanningContext(
      ScheduleInstanceAggregate schedule,
      ProvenanceConfigSnapshot configSnapshot,
      PlanTriggerNorm norm,
      PlannerWindow window,
      ProvenanceCode provenanceCode,
      OperationCode operationCode) {}

  /**
   * Queries the latest global time cursor watermark.
   *
   * @param provenanceCode provenance code
   * @param operationCode operation enum
   * @return latest watermark (null indicates first run)
   * @throws PlanPersistenceException when repository access fails
   */
  private Instant lookupCursorWatermark(
      ProvenanceCode provenanceCode, OperationCode operationCode) {
    try {
      return cursorRepository
          .findLatestGlobalTimeWatermark(provenanceCode.getCode(), opCode(operationCode))
          .orElse(null);
    } catch (RuntimeException ex) {
      throw new PlanPersistenceException(
          PlanPersistenceException.Stage.PLAN, "Failed to load cursor watermark", ex);
    }
  }

  /**
   * Resolves the execution window for the plan.
   *
   * @param norm trigger spec
   * @param configSnapshot provenance configuration snapshot
   * @param cursorWatermark cursor watermark (nullable)
   * @param now current trigger time
   * @return planning window (nullable when no plan should be generated)
   * @throws PlanValidationException when parsing or validation fails
   */
  private PlannerWindow resolvePlannerWindow(
      PlanTriggerNorm norm,
      ProvenanceConfigSnapshot configSnapshot,
      Instant cursorWatermark,
      Instant now) {
    try {
      return planningWindowResolver.resolveWindow(norm, configSnapshot, cursorWatermark, now);
    } catch (PlanValidationException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new PlanValidationException(
          "Failed to resolve planning window: " + ex.getMessage(),
          PlanValidationException.Reason.WINDOW_INVALID,
          ex);
    }
  }

  /**
   * Logs the window resolution details at DEBUG level.
   *
   * @param provenanceCode provenance code
   * @param operationCode operation code
   * @param cursorWatermark cursor watermark timestamp (nullable)
   * @param window resolved planning window (nullable)
   */
  private void logWindowResolution(
      ProvenanceCode provenanceCode,
      OperationCode operationCode,
      Instant cursorWatermark,
      PlannerWindow window) {
    if (log.isDebugEnabled()) {
      log.debug(
          "Resolved planning window for provenance [{}] operation [{}]: cursorWatermark={}, "
              + "window=[{}, {})",
          provenanceCode,
          operationCode,
          cursorWatermark,
          window == null ? null : window.from(),
          window == null ? null : window.to());
    }
  }

  /**
   * Handles idempotent plan reuse when an existing plan with the same planKey is found.
   *
   * <p>This method:
   *
   * <ol>
   *   <li>Loads existing slices and tasks
   *   <li>Identifies tasks eligible for retry (FAILED or CANCELLED status)
   *   <li>Resets and re-queues retry tasks
   *   <li>Publishes retry events via outbox
   *   <li>Returns result based on existing plan
   * </ol>
   *
   * @param existingPlan the existing plan aggregate
   * @param schedule the current schedule instance
   * @param planKey the idempotency key for logging
   * @return ingestion result based on existing plan
   * @throws PlanPersistenceException if retry persistence fails
   */
  private PlanIngestionResult handleIdempotentPlanReuse(
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

    return buildPlanIngestionResult(schedule, existingPlan, existingSlices, existingTasks);
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
   * Processes retry tasks by marking plan partial and publishing retry events.
   *
   * @param existingPlan existing plan aggregate
   * @param schedule schedule instance
   * @param retryTasks tasks to retry
   */
  private void processRetryTasks(
      PlanAggregate existingPlan,
      ScheduleInstanceAggregate schedule,
      List<TaskAggregate> retryTasks) {
    existingPlan.markPartial();
    planRepository.save(existingPlan);
    List<TaskQueuedEvent> retryEvents = collectQueuedEvents(retryTasks);
    taskOutboxPublisher.publishRetry(retryEvents, existingPlan, schedule);
    log.info(
        "Re-queued {} failed tasks for existing plan [{}]",
        retryTasks.size(),
        existingPlan.getId());
  }

  /**
   * Builds plan ingestion result from existing plan data.
   *
   * @param schedule schedule instance
   * @param plan plan aggregate
   * @param slices plan slices
   * @param tasks tasks
   * @return plan ingestion result
   */
  private PlanIngestionResult buildPlanIngestionResult(
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
   * Prepares tasks for retry by resetting failed or cancelled tasks.
   *
   * @param tasks the list of tasks to check
   * @return list of tasks that were prepared for retry
   * @throws PlanPersistenceException if task persistence fails
   */
  private List<TaskAggregate> prepareTasksForRetry(List<TaskAggregate> tasks) {
    List<TaskAggregate> retryTasks = new ArrayList<>();
    for (TaskAggregate task : tasks) {
      if (shouldRetry(task)) {
        task.prepareForRetry();
        saveTaskSafely(task);
        retryTasks.add(task);
      }
    }
    return retryTasks;
  }

  /**
   * Persists new plan with slices and tasks, then publishes queued events.
   *
   * @param draftPlan the draft plan aggregate to persist
   * @param assembly the assembly result containing slices and tasks
   * @param schedule the schedule instance
   * @param window the planning window (nullable)
   * @return ingestion result with persisted IDs
   * @throws PlanPersistenceException if persistence fails
   */
  private PlanIngestionResult persistAndPublishNewPlan(
      PlanAggregate draftPlan,
      PlanAssemblyResult assembly,
      ScheduleInstanceAggregate schedule,
      PlannerWindow window) {
    log.debug(
        "Persisting plan for provenance [{}] operation [{}]: planKey={}",
        draftPlan.getProvenanceCode(),
        draftPlan.getOperationCode(),
        draftPlan.getPlanKey());

    PlanAggregate persistedPlan = savePlanSafely(draftPlan);
    List<PlanSliceAggregate> persistedSlices = persistSlices(persistedPlan, assembly.slices());
    List<TaskAggregate> persistedTasks =
        persistTasks(persistedPlan, persistedSlices, assembly.tasks());

    log.debug(
        "Persisted plan [{}] with {} slices and {} tasks",
        persistedPlan.getId(),
        persistedSlices.size(),
        persistedTasks.size());

    List<TaskQueuedEvent> queuedEvents = collectQueuedEvents(persistedTasks);
    log.debug(
        "Publishing {} task-ready events to outbox for plan [{}]",
        queuedEvents.size(),
        persistedPlan.getId());

    taskOutboxPublisher.publish(queuedEvents, persistedPlan, schedule);

    log.info(
        "Successfully created plan [{}] for provenance [{}] operation [{}]: {} slices, {} tasks "
            + "generated for window [{}, {})",
        persistedPlan.getId(),
        persistedPlan.getProvenanceCode(),
        persistedPlan.getOperationCode(),
        persistedSlices.size(),
        persistedTasks.size(),
        window == null ? null : window.from(),
        window == null ? null : window.to());

    return new PlanIngestionResult(
        schedule.getId(),
        persistedPlan.getId(),
        persistedSlices.stream().map(PlanSliceAggregate::getId).collect(Collectors.toList()),
        persistedTasks.size(),
        assembly.status().name());
  }

  /**
   * Pre-orchestration validation entry point.
   *
   * @param norm trigger specification
   * @param configSnapshot configuration snapshot
   * @param window planning window
   * @param queuedTasks current count of queued tasks
   * @throws PlanValidationException if validation fails
   */
  private void validateBeforeAssemble(
      PlanTriggerNorm norm,
      ProvenanceConfigSnapshot configSnapshot,
      PlannerWindow window,
      long queuedTasks) {
    try {
      plannerValidator.validateBeforeAssemble(norm, configSnapshot, window, queuedTasks);
    } catch (PlanValidationException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new PlanValidationException("Plan validation failed: " + ex.getMessage(), ex);
    }
  }

  /**
   * Assembles plan blueprints and validates result completeness.
   *
   * @param assemblyRequest assembly request
   * @return assembly result
   * @throws PlanAssemblyException if assembly fails or result is empty
   */
  private PlanAssemblyResult assemblePlan(PlanAssemblyRequest assemblyRequest) {
    PlanAssemblyResult assembly;
    try {
      assembly = planAssembler.assemble(assemblyRequest);
    } catch (PlanValidationException | PlanAssemblyException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new PlanAssemblyException(
          "Plan assembly execution failed",
          PlanAssemblyException.Reason.SLICE_GENERATION_FAILED,
          ex);
    }

    if (assembly == null || assembly.status() == PlanAssemblyResult.AssemblyStatus.FAILED) {
      throw new PlanAssemblyException(
          "Plan assembly produced no executable units", PlanAssemblyException.Reason.EMPTY_RESULT);
    }
    return assembly;
  }

  /**
   * Persists plan aggregate and wraps underlying exceptions.
   *
   * @param draftPlan draft plan aggregate
   * @return persisted plan aggregate
   * @throws PlanPersistenceException if persistence fails
   */
  private PlanAggregate savePlanSafely(PlanAggregate draftPlan) {
    try {
      return planRepository.save(draftPlan);
    } catch (RuntimeException ex) {
      throw new PlanPersistenceException(
          PlanPersistenceException.Stage.PLAN, "Failed to persist plan aggregate", ex);
    }
  }

  /**
   * Persists task retry state.
   *
   * @param task task aggregate
   * @throws PlanPersistenceException if persistence fails
   */
  private void saveTaskSafely(TaskAggregate task) {
    try {
      taskRepository.save(task);
    } catch (RuntimeException ex) {
      throw new PlanPersistenceException(
          PlanPersistenceException.Stage.TASK_RETRY, "Failed to persist task retry state", ex);
    }
  }

  /**
   * Saves or updates schedule instance (idempotent).
   *
   * @param request schedule request
   * @return persisted schedule instance
   * @throws PlanPersistenceException if storage fails
   */
  private ScheduleInstanceAggregate persistScheduleInstanceSafely(PlanIngestionCommand request) {
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
   * Builds trigger specification (PlanTriggerNorm).
   *
   * @param schedule schedule instance
   * @param request schedule request
   * @return trigger specification (value object)
   */
  private PlanTriggerNorm buildTriggerNorm(
      ScheduleInstanceAggregate schedule, PlanIngestionCommand request) {
    return new PlanTriggerNorm(
        schedule.getId(),
        request.provenanceCode(),
        request.operationCode(),
        request.step(),
        request.triggerType(),
        request.scheduler(),
        request.schedulerJobId(),
        request.schedulerLogId(),
        request.windowFrom(),
        request.windowTo(),
        request.priority(),
        request.triggerParams());
  }

  /**
   * Collects TaskQueuedEvent instances from task aggregates.
   *
   * <p>Explicitly triggers {@link TaskAggregate#raiseQueuedEvent()} to ensure events exist.
   *
   * @param tasks task collection
   * @return list of queued events (empty list if no tasks)
   */
  private List<TaskQueuedEvent> collectQueuedEvents(List<TaskAggregate> tasks) {
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
   * Determines if task is eligible for compensation retry.
   *
   * @param task task aggregate
   * @return true if retry is needed (FAILED or CANCELLED status)
   */
  private boolean shouldRetry(TaskAggregate task) {
    TaskStatus status = task.getStatus();
    return status == TaskStatus.FAILED || status == TaskStatus.CANCELLED;
  }

  /**
   * Batch persists plan slice aggregates.
   *
   * @param plan plan aggregate (already persisted)
   * @param slices slice collection
   * @return persisted slice collection
   * @throws PlanPersistenceException if persistence fails
   */
  private List<PlanSliceAggregate> persistSlices(
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
  private List<TaskAggregate> persistTasks(
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
}
