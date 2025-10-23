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
    ProvenanceCode provenanceCode = request.provenanceCode();
    OperationCode operationCode = request.operationCode();

    Instant now = request.triggeredAt();
    log.info(
        "plan-ingest start, provenance={}, op={}, triggeredAt={}",
        provenanceCode,
        operationCode,
        now);

    // Phase 1: 调度实例 + 来源配置快照
    ScheduleInstanceAggregate schedule = persistScheduleInstanceSafely(request);
    ProvenanceConfigSnapshot configSnapshot =
        patraRegistryPort.fetchConfig(provenanceCode, operationCode);
    PlanTriggerNorm norm = buildTriggerNorm(schedule, request);

    // Phase 2: 游标水位 (仅前进) + 解析计划窗口（TIME 策略）
    Instant cursorWatermark = lookupCursorWatermark(provenanceCode, operationCode);
    PlannerWindow window = resolvePlannerWindow(norm, configSnapshot, cursorWatermark, now);
    log.debug(
        "plan-ingest window resolved provenance={} op={} cursorWatermark={} window=[{}, {})",
        provenanceCode,
        operationCode,
        cursorWatermark,
        window == null ? null : window.from(),
        window == null ? null : window.to());

    // Phase 3: 构建 Plan 级别业务表达式（内存对象，不编译）
    PlanExpressionDescriptor expressionDescriptor =
        planExpressionBuilder.build(norm, configSnapshot);
    log.debug(
        "plan-ingest expr built hash={} jsonSize={}",
        expressionDescriptor.hash(),
        expressionDescriptor.jsonSnapshot().length());

    // Phase 4: pre-validation (window sanity / backpressure / capacity)
    long queuedTasks =
        taskRepository.countQueuedTasks(provenanceCode.getCode(), opCode(operationCode));
    validateBeforeAssemble(norm, configSnapshot, window, queuedTasks);
    log.debug("plan-ingest validation passed queuedTasks={}", queuedTasks);

    // Phase 5: assemble blueprints
    PlanAssemblyRequest assemblyRequest =
        new PlanAssemblyRequest(norm, window, configSnapshot, expressionDescriptor);
    PlanAssemblyResult assembly = assemblePlan(assemblyRequest);

    PlanAggregate draftPlan = assembly.plan();
    // Idempotent reuse: if planKey already exists, directly take the compensation/retry path
    PlanAggregate existingPlan = planRepository.findByPlanKey(draftPlan.getPlanKey()).orElse(null);
    if (existingPlan != null) {
      log.info(
          "plan-ingest dedup hit existing planKey={}, reuse planId={}",
          draftPlan.getPlanKey(),
          existingPlan.getId());
      List<PlanSliceAggregate> existingSlices =
          planSliceRepository.findByPlanId(existingPlan.getId());
      List<TaskAggregate> existingTasks = taskRepository.findByPlanId(existingPlan.getId());

      List<TaskAggregate> retryTasks = new ArrayList<>();
      for (TaskAggregate task : existingTasks) {
        if (shouldRetry(task)) {
          // Reset failed/canceled tasks and prepare to re-queue
          task.prepareForRetry();
          saveTaskSafely(task);
          retryTasks.add(task);
        }
      }
      if (!retryTasks.isEmpty()) {
        existingPlan.markPartial();
        planRepository.save(existingPlan);
        List<TaskQueuedEvent> retryEvents = collectQueuedEvents(retryTasks);
        taskOutboxPublisher.publishRetry(retryEvents, existingPlan, schedule);
      }

      return new PlanIngestionResult(
          schedule.getId(),
          existingPlan.getId(),
          existingSlices.stream().map(PlanSliceAggregate::getId).collect(Collectors.toList()),
          existingTasks.size(),
          existingPlan.getStatus().name());
    }

    // Phase 6: persist Plan / Slice / Task
    PlanAggregate persistedPlan = savePlanSafely(draftPlan);
    List<PlanSliceAggregate> persistedSlices = persistSlices(persistedPlan, assembly.slices());
    List<TaskAggregate> persistedTasks =
        persistTasks(persistedPlan, persistedSlices, assembly.tasks());

    List<TaskQueuedEvent> queuedEvents = collectQueuedEvents(persistedTasks);
    taskOutboxPublisher.publish(queuedEvents, persistedPlan, schedule);

    log.info(
        "plan-ingest success, planId={}, sliceCount={}, taskCount={}, window=[{}, {})",
        persistedPlan.getId(),
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
   * Returns the operation code string.
   *
   * @param op domain operation enum
   * @return operation code; null when op is null
   */
  private String opCode(OperationCode op) {
    return op == null ? null : op.getCode();
  }

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
   * 前置统一校验入口。
   *
   * @param norm 调度触发规范
   * @param configSnapshot 配置快照
   * @param window 规划窗口
   * @param queuedTasks 当前排队任务数
   * @throws PlanValidationException 校验失败
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
   * 组装计划蓝图并进行结果完整性校验。
   *
   * @param assemblyRequest 装配请求
   * @return 装配结果
   * @throws PlanAssemblyException 装配失败或结果为空
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
   * 持久化计划聚合并包装底层异常。
   *
   * @param draftPlan 初始计划聚合
   * @return 持久化后的计划聚合
   * @throws PlanPersistenceException 持久化失败
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
   * 持久化任务重试状态。
   *
   * @param task 任务聚合
   * @throws PlanPersistenceException 持久化失败
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
   * 保存或更新调度实例（幂等）。
   *
   * @param request 调度请求
   * @return 持久化后的调度实例
   * @throws PlanPersistenceException 存储失败
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
            request.provenanceCode());
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
   * 构建调度触发规范（PlanTriggerNorm）。
   *
   * @param schedule 调度实例
   * @param request 调度请求
   * @return 触发规范（值对象）
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
   * 收集任务聚合内产生的 TaskQueuedEvent。
   *
   * <p>调用时会显式触发 {@link TaskAggregate#raiseQueuedEvent()} 以确保事件存在。
   *
   * @param tasks 任务集合
   * @return 入队事件列表（空集合表示无任务）
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
   * 判定任务是否符合补偿重试条件。
   *
   * @param task 任务聚合
   * @return true 需要重试（FAILED / CANCELLED）
   */
  private boolean shouldRetry(TaskAggregate task) {
    TaskStatus status = task.getStatus();
    return status == TaskStatus.FAILED || status == TaskStatus.CANCELLED;
  }

  /**
   * 批量持久化切片聚合。
   *
   * @param plan 计划聚合（已持久化）
   * @param slices 切片集合
   * @return 持久化后的切片集合
   * @throws PlanPersistenceException 失败时抛出
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
   * 批量持久化任务聚合，并绑定计划与切片 ID。
   *
   * @param plan 计划聚合
   * @param persistedSlices 已持久化切片
   * @param tasks 任务集合
   * @return 持久化后的任务集合
   * @throws PlanPersistenceException 持久化失败
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
