package com.patra.ingest.app.usecase.plan;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.app.usecase.plan.assembler.PlanAssembler;
import com.patra.ingest.app.usecase.plan.assembler.PlanAssemblyRequest;
import com.patra.ingest.app.usecase.plan.command.PlanIngestionCommand;
import com.patra.ingest.app.usecase.plan.dto.PlanAssemblyResult;
import com.patra.ingest.app.usecase.plan.dto.PlanIngestionResult;
import com.patra.ingest.app.usecase.plan.expression.PlanExpressionBuilder;
import com.patra.ingest.app.usecase.plan.expression.PlanExpressionDescriptor;
import com.patra.ingest.app.usecase.plan.validator.PlannerValidator;
import com.patra.ingest.app.usecase.plan.window.PlanningWindowResolver;
import com.patra.ingest.domain.event.TaskQueuedEvent;
import com.patra.ingest.domain.exception.PlanAssemblyException;
import com.patra.ingest.domain.exception.PlanPersistenceException;
import com.patra.ingest.domain.exception.PlanValidationException;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.PlanTriggerNorm;
import com.patra.ingest.domain.model.vo.PlannerWindow;
import com.patra.ingest.domain.port.CursorRepository;
import com.patra.ingest.domain.port.PatraRegistryPort;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.domain.port.TaskRepository;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Main orchestrator for plan ingestion flow.
 *
 * <p>Coordinates the following phases:
 *
 * <ul>
 *   <li>Persist schedule instance and load provenance config snapshot
 *   <li>Query cursor watermark and resolve execution window
 *   <li>Build plan expression and run pre-validations
 *   <li>Assemble and persist plan/slices/tasks (with idempotency and compensation)
 *   <li>Collect task enqueued events and publish via Outbox pattern
 * </ul>
 *
 * <p>This orchestrator delegates persistence to {@link PlanPersistenceCoordinator}, idempotency
 * handling to {@link PlanIdempotencyCoordinator}, and publishing to {@link
 * PlanPublishingCoordinator}.
 *
 * <p>Note: This orchestrator maintains the {@code @Transactional} boundary to ensure atomicity
 * across persistence and event publishing (Outbox pattern).
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator implements PlanIngestionUseCase {

  private final PatraRegistryPort patraRegistryPort;
  private final CursorRepository cursorRepository;
  private final TaskRepository taskRepository;
  private final PlanningWindowResolver planningWindowResolver;
  private final PlannerValidator plannerValidator;
  private final PlanAssembler planAssembler;
  private final PlanExpressionBuilder planExpressionBuilder;
  private final PlanRepository planRepository;

  // Coordinators for delegating specific responsibilities
  private final PlanPersistenceCoordinator persistenceCoordinator;
  private final PlanIdempotencyCoordinator idempotencyCoordinator;
  private final PlanPublishingCoordinator publishingCoordinator;

  /**
   * Main plan orchestration flow (entry method).
   *
   * <p>Coordinates six phases:
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
   * @param request scheduling request
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
      return idempotencyCoordinator.handleIdempotentPlanReuse(
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

    ScheduleInstanceAggregate schedule = persistenceCoordinator.persistScheduleInstance(request);
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
   * Persists new plan with slices and tasks, then publishes queued events.
   *
   * @param draftPlan the draft plan aggregate to persist
   * @param assembly the assembly result containing slices and tasks
   * @param schedule the schedule instance
   * @param window the planning window (nullable)
   * @return ingestion result with persisted IDs
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

    PlanAggregate persistedPlan = persistenceCoordinator.savePlan(draftPlan);
    List<PlanSliceAggregate> persistedSlices =
        persistenceCoordinator.persistSlices(persistedPlan, assembly.slices());
    List<TaskAggregate> persistedTasks =
        persistenceCoordinator.persistTasks(persistedPlan, persistedSlices, assembly.tasks());

    log.debug(
        "Persisted plan [{}] with {} slices and {} tasks",
        persistedPlan.getId(),
        persistedSlices.size(),
        persistedTasks.size());

    List<TaskQueuedEvent> queuedEvents = publishingCoordinator.collectQueuedEvents(persistedTasks);
    publishingCoordinator.publishNewPlanEvents(queuedEvents, persistedPlan, schedule);

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

    return publishingCoordinator.buildIngestionResult(
        schedule, persistedPlan, persistedSlices, persistedTasks.size(), assembly.status().name());
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
}
