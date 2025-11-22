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
import com.patra.ingest.domain.model.vo.plan.PlanTriggerNorm;
import com.patra.ingest.domain.model.vo.plan.PlannerWindow;
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

/// 计划接入编排器。
/// 
/// 职责：
/// 
/// - 持久化调度实例并加载数据源配置快照
///   - 查询游标水位并解析执行窗口
///   - 构建计划表达式并执行预验证
///   - 组装并持久化 Plan/Slice/Task（包含幂等性和补偿逻辑）
///   - 收集任务入队事件并通过 Outbox 模式发布
/// 
/// 该编排器将持久化操作委托给 {@link PlanPersistenceCoordinator}，幂等性处理委托给 {@link
/// PlanIdempotencyCoordinator}，发布操作委托给 {@link PlanPublishingCoordinator}。
/// 
/// 注意：该编排器维护 `@Transactional` 事务边界，确保持久化和事件发布的原子性（Outbox 模式）。
/// 
/// @author linqibin
/// @since 0.1.0
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

  /// 计划编排主流程（入口方法）。
/// 
/// 协调六个阶段：
/// 
/// @param request 调度请求
/// @return 计划执行摘要
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

  /// 记录计划接入开始日志及关键请求详情。
/// 
/// @param request 计划接入命令
  private void logPlanIngestionStart(PlanIngestionCommand request) {
    log.info(
        "开始计划接入 数据源 [{}] 操作 [{}]: 触发时间 {} 调度器 [{}]",
        request.provenanceCode(),
        request.operationCode(),
        request.triggeredAt(),
        request.scheduler());
  }

  /// 准备计划上下文：加载配置并解析窗口。
/// 
/// @param request 计划接入命令
/// @return 包含调度、配置、规范和窗口的计划上下文
  private PlanningContext preparePlanningContext(PlanIngestionCommand request) {
    log.debug("准备计划上下文 数据源 [{}] 操作 [{}]", request.provenanceCode(), request.operationCode());

    ScheduleInstanceAggregate schedule = persistenceCoordinator.persistScheduleInstance(request);
    ProvenanceConfigSnapshot configSnapshot =
        patraRegistryPort.fetchConfig(request.provenanceCode(), request.operationCode());
    PlanTriggerNorm norm = buildTriggerNorm(schedule, request);

    Instant cursorWatermark =
        lookupCursorWatermark(request.provenanceCode(), request.operationCode());
    log.debug(
        "检索游标水位 数据源 [{}] 操作 [{}]: {}",
        request.provenanceCode(),
        request.operationCode(),
        cursorWatermark);

    PlannerWindow window =
        resolvePlannerWindow(norm, configSnapshot, cursorWatermark, request.triggeredAt());
    logWindowResolution(request.provenanceCode(), request.operationCode(), cursorWatermark, window);

    return new PlanningContext(
        schedule, configSnapshot, norm, window, request.provenanceCode(), request.operationCode());
  }

  /// 构建计划表达式描述符并记录结果。
/// 
/// @param context 计划上下文
/// @return 计划表达式描述符
  private PlanExpressionDescriptor buildPlanExpression(PlanningContext context) {
    PlanExpressionDescriptor descriptor =
        planExpressionBuilder.build(context.norm(), context.configSnapshot());
    log.debug(
        "构建计划表达式 数据源 [{}] 操作 [{}]: hash={}, 快照大小={} 字节",
        context.provenanceCode(),
        context.operationCode(),
        descriptor.hash(),
        descriptor.jsonSnapshot().length());
    return descriptor;
  }

  /// 执行组装前验证并记录结果。
/// 
/// @param context 计划上下文
/// @throws PlanValidationException 验证失败时
  private void performPreValidation(PlanningContext context) {
    long queuedTasks =
        taskRepository.countQueuedTasks(context.provenanceCode(), opCode(context.operationCode()));
    validateBeforeAssemble(context.norm(), context.configSnapshot(), context.window(), queuedTasks);
    log.debug(
        "预验证通过 数据源 [{}] 操作 [{}]: 当前队列任务数 {}",
        context.provenanceCode(),
        context.operationCode(),
        queuedTasks);
  }

  /// 组装计划并验证结果。
/// 
/// @param context 计划上下文
/// @param expressionDescriptor 计划表达式描述符
/// @return 组装结果
/// @throws PlanAssemblyException 组装失败时
  private PlanAssemblyResult assembleAndValidatePlan(
      PlanningContext context, PlanExpressionDescriptor expressionDescriptor) {
    log.debug("开始组装计划 数据源 [{}] 操作 [{}]", context.provenanceCode(), context.operationCode());

    PlanAssemblyRequest assemblyRequest =
        new PlanAssemblyRequest(
            context.norm(), context.window(), context.configSnapshot(), expressionDescriptor);
    PlanAssemblyResult result = assemblePlan(assemblyRequest);

    log.debug(
        "计划组装完成 数据源 [{}] 操作 [{}]: 状态={}, 切片数={}, 任务数={}",
        context.provenanceCode(),
        context.operationCode(),
        result.status(),
        result.slices().size(),
        result.tasks().size());

    return result;
  }

  /// 通过 planKey 检查现有计划。
/// 
/// @param draftPlan 草稿计划聚合根
/// @return 找到的现有计划，否则返回 null
  private PlanAggregate checkForExistingPlan(PlanAggregate draftPlan) {
    return planRepository.findByPlanKey(draftPlan.getPlanKey()).orElse(null);
  }

  /// 返回操作代码字符串。
/// 
/// @param op 领域操作枚举
/// @return 操作代码；op 为 null 时返回 null
  private String opCode(OperationCode op) {
    return op == null ? null : op.getCode();
  }

  /// 内部记录，持有计划上下文数据。
/// 
/// @param schedule 调度实例聚合根
/// @param configSnapshot 数据源配置快照
/// @param norm 计划触发规范
/// @param window 计划窗口
/// @param provenanceCode 数据源代码枚举
/// @param operationCode 操作代码枚举
  private record PlanningContext(
      ScheduleInstanceAggregate schedule,
      ProvenanceConfigSnapshot configSnapshot,
      PlanTriggerNorm norm,
      PlannerWindow window,
      ProvenanceCode provenanceCode,
      OperationCode operationCode) {}

  /// 查询最新的全局时间游标水位。
/// 
/// @param provenanceCode 数据源代码
/// @param operationCode 操作枚举
/// @return 最新水位（null 表示首次运行）
/// @throws PlanPersistenceException 仓储访问失败时
  private Instant lookupCursorWatermark(
      ProvenanceCode provenanceCode, OperationCode operationCode) {
    try {
      return cursorRepository
          .findLatestGlobalTimeWatermark(provenanceCode, opCode(operationCode))
          .orElse(null);
    } catch (RuntimeException ex) {
      throw new PlanPersistenceException(PlanPersistenceException.Stage.PLAN, "加载游标水位失败", ex);
    }
  }

  /// 解析计划的执行窗口。
/// 
/// @param norm 触发规范
/// @param configSnapshot 数据源配置快照
/// @param cursorWatermark 游标水位（可为 null）
/// @param now 当前触发时间
/// @return 计划窗口（不应生成计划时为 null）
/// @throws PlanValidationException 解析或验证失败时
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
          "解析计划窗口失败: " + ex.getMessage(), PlanValidationException.Reason.WINDOW_INVALID, ex);
    }
  }

  /// 在 DEBUG 级别记录窗口解析详情。
/// 
/// @param provenanceCode 数据源代码
/// @param operationCode 操作代码
/// @param cursorWatermark 游标水位时间戳（可为 null）
/// @param window 已解析的计划窗口（可为 null）
  private void logWindowResolution(
      ProvenanceCode provenanceCode,
      OperationCode operationCode,
      Instant cursorWatermark,
      PlannerWindow window) {
    if (log.isDebugEnabled()) {
      log.debug(
          "解析计划窗口 数据源 [{}] 操作 [{}]: 游标水位={}, 窗口=[{}, {})",
          provenanceCode,
          operationCode,
          cursorWatermark,
          window == null ? null : window.from(),
          window == null ? null : window.to());
    }
  }

  /// 持久化新计划（包含切片和任务），然后发布入队事件。
/// 
/// @param draftPlan 待持久化的草稿计划聚合根
/// @param assembly 包含切片和任务的组装结果
/// @param schedule 调度实例
/// @param window 计划窗口（可为 null）
/// @return 包含已持久化 ID 的接入结果
  private PlanIngestionResult persistAndPublishNewPlan(
      PlanAggregate draftPlan,
      PlanAssemblyResult assembly,
      ScheduleInstanceAggregate schedule,
      PlannerWindow window) {
    log.debug(
        "持久化计划 数据源 [{}] 操作 [{}]: planKey={}",
        draftPlan.getProvenanceCode(),
        draftPlan.getOperationCode(),
        draftPlan.getPlanKey());

    PlanAggregate persistedPlan = persistenceCoordinator.savePlan(draftPlan);
    List<PlanSliceAggregate> persistedSlices =
        persistenceCoordinator.persistSlices(persistedPlan, assembly.slices());
    List<TaskAggregate> persistedTasks =
        persistenceCoordinator.persistTasks(persistedPlan, persistedSlices, assembly.tasks());

    log.debug(
        "已持久化计划 [{}]: {} 个切片, {} 个任务",
        persistedPlan.getId(),
        persistedSlices.size(),
        persistedTasks.size());

    List<TaskQueuedEvent> queuedEvents = publishingCoordinator.collectQueuedEvents(persistedTasks);
    publishingCoordinator.publishNewPlanEvents(queuedEvents, persistedPlan, schedule);

    log.info(
        "成功创建计划 [{}] 数据源 [{}] 操作 [{}]: {} 个切片, {} 个任务，窗口 [{}, {})",
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

  /// 编排前验证入口点。
/// 
/// @param norm 触发规范
/// @param configSnapshot 配置快照
/// @param window 计划窗口
/// @param queuedTasks 当前队列任务数
/// @throws PlanValidationException 验证失败时
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
      throw new PlanValidationException("计划验证失败: " + ex.getMessage(), ex);
    }
  }

  /// 组装计划蓝图并验证结果完整性。
/// 
/// @param assemblyRequest 组装请求
/// @return 组装结果
/// @throws PlanAssemblyException 组装失败或结果为空时
  private PlanAssemblyResult assemblePlan(PlanAssemblyRequest assemblyRequest) {
    PlanAssemblyResult assembly;
    try {
      assembly = planAssembler.assemble(assemblyRequest);
    } catch (PlanValidationException | PlanAssemblyException ex) {
      throw ex;
    } catch (RuntimeException ex) {
      throw new PlanAssemblyException(
          "计划组装执行失败", PlanAssemblyException.Reason.SLICE_GENERATION_FAILED, ex);
    }

    if (assembly == null || assembly.status() == PlanAssemblyResult.AssemblyStatus.FAILED) {
      throw new PlanAssemblyException("计划组装未生成可执行单元", PlanAssemblyException.Reason.EMPTY_RESULT);
    }
    return assembly;
  }

  /// 构建触发规范 (PlanTriggerNorm)。
/// 
/// @param schedule 调度实例
/// @param request 调度请求
/// @return 触发规范（值对象）
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
