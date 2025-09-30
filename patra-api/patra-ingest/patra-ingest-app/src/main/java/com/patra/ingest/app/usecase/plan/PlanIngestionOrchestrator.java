package com.patra.ingest.app.usecase.plan;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.app.usecase.plan.expression.PlanExpressionBuilder;
import com.patra.ingest.app.usecase.plan.assembler.PlanAssemblyRequest;
import com.patra.ingest.app.usecase.plan.assembler.PlanAssembler;
import com.patra.ingest.app.usecase.plan.command.PlanIngestionCommand;
import com.patra.ingest.app.usecase.plan.dto.PlanIngestionResult;
import com.patra.ingest.app.usecase.plan.expression.PlanExpressionDescriptor;
import com.patra.ingest.app.usecase.plan.publisher.TaskOutboxPublisher;
import com.patra.ingest.app.usecase.plan.window.PlanningWindowResolver;
import com.patra.ingest.domain.port.PatraRegistryPort;
import com.patra.ingest.app.usecase.plan.validator.PlannerValidator;
import com.patra.ingest.domain.exception.PlanAssemblyException;
import com.patra.ingest.domain.exception.PlanPersistenceException;
import com.patra.ingest.domain.exception.PlanValidationException;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanAssembly;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.TaskStatus;
import com.patra.ingest.domain.model.event.TaskQueuedEvent;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.value.PlannerWindow;
import com.patra.ingest.domain.port.CursorRepository;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.domain.port.ScheduleInstanceRepository;
import com.patra.ingest.domain.port.TaskRepository;

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
 * 采集计划编排核心应用服务：承接调度层（调度任务 / 手动触发）调用，完成
 * <ul>
 *     <li>调度实例落库与来源配置快照读取</li>
 *     <li>游标水位查询与窗口解析（时间窗口策略）</li>
 *     <li>计划表达式（Plan Expression）构建与前置校验</li>
 *     <li>计划 / 切片 / 任务装配与持久化（含幂等去重与补偿）</li>
 *     <li>任务入队事件收集并通过 Outbox 模式发布</li>
 * </ul>
 * 该服务是“计划编排”入口的核心编排器（Application Service），不承载具体领域规则细节，
 * 主要负责：流程编排、异常语义转换、幂等及日志可观测性。所有领域约束交由领域对象或校验器完成。
 * <p>日志规范：INFO 级别输出一次性流程结果 / 关键分支命中；DEBUG 级别记录窗口与表达式调试信息。</p>
 * <p>
 * 线程安全说明：该服务无内部可变共享状态，仅依赖线程安全的 Spring Bean（Repository/Builder），可并发调用。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator implements PlanIngestionUseCase {

    /**
     * 来源配置查询端口（上游 provenance 服务访问抽象）。
     */
    private final PatraRegistryPort patraRegistryPort;

    /**
     * 游标仓储：用于查询最近一次全局时间水位（推进窗口起点）。
     */
    private final CursorRepository cursorRepository;

    /**
     * 任务仓储：支撑任务去重统计与批量保存。
     */
    private final TaskRepository taskRepository;

    /**
     * 计划窗口解析策略：根据调度规范、配置与游标水位计算本次执行窗口。
     */
    private final PlanningWindowResolver planningWindowResolver;

    /**
     * 编排前置校验器：集中校验窗口合理性、排队压力、能力边界等。
     */
    private final PlannerValidator plannerValidator;

    /**
     * 计划装配服务：负责 Plan / Slice / Task 蓝图组装（不含持久化）。
     */
    private final PlanAssembler planAssembler;

    /**
     * 任务 Outbox 发布器：将入队事件转换为 Outbox 消息并持久化 / 发布。
     */
    private final TaskOutboxPublisher taskOutboxPublisher;

    /**
     * 计划表达式构建器：生成计划级表达式描述（未编译，仅快照与哈希）。
     */
    private final PlanExpressionBuilder planExpressionBuilder;

    /**
     * 调度实例仓储：保存 / 更新调度触发记录，支撑幂等追踪。
     */
    private final ScheduleInstanceRepository scheduleInstanceRepository;

    /**
     * 计划仓储：保存或查询计划聚合，支持 planKey 幂等判断。
     */
    private final PlanRepository planRepository;

    /**
     * 切片仓储：批量持久化计划切片聚合。
     */
    private final PlanSliceRepository planSliceRepository;

    /**
     * 计划编排主流程（入口方法）。
     * <p>整体包含 6 个阶段：
     * <ol>
     *     <li>调度实例落库 & 来源配置快照读取</li>
     *     <li>游标水位查询 & 时间窗口解析</li>
     *     <li>计划表达式构建</li>
     *     <li>前置校验（窗口 / 压力 / 能力）</li>
     *     <li>计划装配（含幂等复用与补偿重试）</li>
     *     <li>持久化并发布任务入队事件</li>
     * </ol>
     * 异常治理：将运行期异常转换为语义化 Plan*Exception，供上层统一映射错误码。</p>
     *
     * @param request 调度请求（包含 provenance、endpoint、operation、窗口边界、优先级、触发信息等）
     * @return 计划执行结果摘要
     */
    @Override
    @Transactional
    public PlanIngestionResult ingestPlan(PlanIngestionCommand request) {
        ProvenanceCode provenanceCode = request.provenanceCode();
        OperationCode operationCode = request.operationCode();

        Instant now = request.triggeredAt();
        log.info("[INGEST][APP] plan-ingest start, provenance={}, op={}, triggeredAt={}", provenanceCode, operationCode, now);

        // Phase 1: 调度实例 + 来源配置快照
        ScheduleInstanceAggregate schedule = persistScheduleInstanceSafely(request);
        ProvenanceConfigSnapshot configSnapshot = patraRegistryPort.fetchConfig(
                provenanceCode, request.endpoint(), operationCode
        );
        PlanTriggerNorm norm = buildTriggerNorm(schedule, request);

        // Phase 2: 游标水位 (仅前进) + 解析计划窗口（TIME 策略）
        Instant cursorWatermark = lookupCursorWatermark(provenanceCode, operationCode);
        PlannerWindow window = resolvePlannerWindow(norm, configSnapshot, cursorWatermark, now);
        log.debug("[INGEST][APP] plan-ingest window resolved provenance={} op={} cursorWatermark={} window=[{}, {})",
                provenanceCode, operationCode, cursorWatermark, window == null ? null : window.from(), window == null ? null : window.to());

        // Phase 3: 构建 Plan 级别业务表达式（内存对象，不编译）
        PlanExpressionDescriptor expressionDescriptor = planExpressionBuilder.build(norm, configSnapshot);
        log.debug("[INGEST][APP] plan-ingest expr built hash={} jsonSize={}", expressionDescriptor.hash(), expressionDescriptor.jsonSnapshot().length());

        // Phase 4: 前置验证（窗口合理性 / 背压 / 能力）
        long queuedTasks = taskRepository.countQueuedTasks(
                provenanceCode.getCode(), opCode(operationCode));
        validateBeforeAssemble(norm, configSnapshot, window, queuedTasks);
        log.debug("[INGEST][APP] plan-ingest validation passed queuedTasks={}", queuedTasks);

        // Phase 5: 组装蓝图
        PlanAssemblyRequest assemblyRequest = new PlanAssemblyRequest(norm, window, configSnapshot, expressionDescriptor);
        PlanAssembly assembly = assemblePlan(assemblyRequest);

        PlanAggregate draftPlan = assembly.plan();
        // 幂等复用：若 planKey 已存在，直接走补偿/重试分支
        PlanAggregate existingPlan = planRepository.findByPlanKey(draftPlan.getPlanKey()).orElse(null);
        if (existingPlan != null) {
            log.info("[INGEST][APP] plan-ingest dedup hit existing planKey={}, reuse planId={}", draftPlan.getPlanKey(), existingPlan.getId());
            List<PlanSliceAggregate> existingSlices = planSliceRepository.findByPlanId(existingPlan.getId());
            List<TaskAggregate> existingTasks = taskRepository.findByPlanId(existingPlan.getId());

            List<TaskAggregate> retryTasks = new ArrayList<>();
            for (TaskAggregate task : existingTasks) {
                if (shouldRetry(task)) {
                    // 重置失败/取消任务，准备重新排队
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

        // Phase 6: 持久化 Plan / Slice / Task
        PlanAggregate persistedPlan = savePlanSafely(draftPlan);
        List<PlanSliceAggregate> persistedSlices = persistSlices(persistedPlan, assembly.slices());
        List<TaskAggregate> persistedTasks = persistTasks(persistedPlan, persistedSlices, assembly.tasks());

        List<TaskQueuedEvent> queuedEvents = collectQueuedEvents(persistedTasks);
        taskOutboxPublisher.publish(queuedEvents, persistedPlan, schedule);

        log.info("[INGEST][APP] plan-ingest success, planId={}, sliceCount={}, taskCount={}, window=[{}, {})", persistedPlan.getId(), persistedSlices.size(), persistedTasks.size(), window == null ? null : window.from(), window == null ? null : window.to());

        return new PlanIngestionResult(
                schedule.getId(),
                persistedPlan.getId(),
                persistedSlices.stream().map(PlanSliceAggregate::getId).collect(Collectors.toList()),
                persistedTasks.size(),
                assembly.status().name());
    }


    /**
     * 获取操作码字符串表示。
     *
     * @param op 领域操作枚举
     * @return 操作码；若 null 返回 null
     */
    private String opCode(OperationCode op) {
        return op == null ? null : op.getCode();
    }

    /**
     * 查询最近一次全局时间游标水位。
     *
     * @param provenanceCode 来源编码
     * @param operationCode  操作枚举
     * @return 最新水位（可能为 null 表示首次执行）
     * @throws PlanPersistenceException 仓储访问失败
     */
    private Instant lookupCursorWatermark(ProvenanceCode provenanceCode, OperationCode operationCode) {
        try {
            return cursorRepository.findLatestGlobalTimeWatermark(provenanceCode.getCode(), opCode(operationCode))
                    .orElse(null);
        } catch (RuntimeException ex) {
            throw new PlanPersistenceException(PlanPersistenceException.Stage.PLAN,
                    "Failed to load cursor watermark", ex);
        }
    }

    /**
     * 解析计划执行窗口。
     *
     * @param norm            触发规范
     * @param configSnapshot  来源配置快照
     * @param cursorWatermark 游标水位（可为空）
     * @param now             当前触发时间
     * @return 规划窗口（可为空表示无需生成计划）
     * @throws PlanValidationException 解析或逻辑校验失败
     */
    private PlannerWindow resolvePlannerWindow(PlanTriggerNorm norm,
                                               ProvenanceConfigSnapshot configSnapshot,
                                               Instant cursorWatermark,
                                               Instant now) {
        try {
            return planningWindowResolver.resolveWindow(norm, configSnapshot, cursorWatermark, now);
        } catch (PlanValidationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new PlanValidationException("Failed to resolve planning window: " + ex.getMessage(),
                    PlanValidationException.Reason.WINDOW_INVALID, ex);
        }
    }

    /**
     * 前置统一校验入口。
     *
     * @param norm           调度触发规范
     * @param configSnapshot 配置快照
     * @param window         规划窗口
     * @param queuedTasks    当前排队任务数
     * @throws PlanValidationException 校验失败
     */
    private void validateBeforeAssemble(PlanTriggerNorm norm,
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
    private PlanAssembly assemblePlan(PlanAssemblyRequest assemblyRequest) {
        PlanAssembly assembly;
        try {
            assembly = planAssembler.assemble(assemblyRequest);
        } catch (PlanValidationException | PlanAssemblyException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new PlanAssemblyException("Plan assembly execution failed", PlanAssemblyException.Reason.SLICE_GENERATION_FAILED, ex);
        }

        if (assembly == null || assembly.status() == PlanAssembly.PlanAssemblyStatus.FAILED) {
            throw new PlanAssemblyException("Plan assembly produced no executable units", PlanAssemblyException.Reason.EMPTY_RESULT);
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
            throw new PlanPersistenceException(PlanPersistenceException.Stage.PLAN,
                    "Failed to persist plan aggregate", ex);
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
            throw new PlanPersistenceException(PlanPersistenceException.Stage.TASK_RETRY,
                    "Failed to persist task retry state", ex);
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
        ScheduleInstanceAggregate schedule = ScheduleInstanceAggregate.start(
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
            throw new PlanPersistenceException(PlanPersistenceException.Stage.SCHEDULE_INSTANCE,
                    "Failed to persist schedule instance", ex);
        }
    }

    /**
     * 构建调度触发规范（PlanTriggerNorm）。
     *
     * @param schedule 调度实例
     * @param request  调度请求
     * @return 触发规范（值对象）
     */
    private PlanTriggerNorm buildTriggerNorm(ScheduleInstanceAggregate schedule, PlanIngestionCommand request) {
        return new PlanTriggerNorm(
                schedule.getId(),
                request.provenanceCode(),
                request.endpoint(),
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
     * <p>调用时会显式触发 {@link TaskAggregate#raiseQueuedEvent()} 以确保事件存在。</p>
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
     * @param plan   计划聚合（已持久化）
     * @param slices 切片集合
     * @return 持久化后的切片集合
     * @throws PlanPersistenceException 失败时抛出
     */
    private List<PlanSliceAggregate> persistSlices(PlanAggregate plan, List<PlanSliceAggregate> slices) {
        if (CollUtil.isEmpty(slices)) {
            return List.of();
        }
        slices.forEach(slice -> slice.bindPlan(plan.getId()));
        try {
            return planSliceRepository.saveAll(slices);
        } catch (RuntimeException ex) {
            throw new PlanPersistenceException(PlanPersistenceException.Stage.PLAN_SLICE,
                    "Failed to persist plan slices", ex);
        }
    }

    /**
     * 批量持久化任务聚合，并绑定计划与切片 ID。
     *
     * @param plan            计划聚合
     * @param persistedSlices 已持久化切片
     * @param tasks           任务集合
     * @return 持久化后的任务集合
     * @throws PlanPersistenceException 持久化失败
     */
    private List<TaskAggregate> persistTasks(PlanAggregate plan,
                                             List<PlanSliceAggregate> persistedSlices,
                                             List<TaskAggregate> tasks) {
        if (CollUtil.isEmpty(tasks)) {
            return List.of();
        }
        Map<Integer, PlanSliceAggregate> sliceBySeq = MapUtil.newHashMap(persistedSlices.size());
        for (PlanSliceAggregate slice : persistedSlices) {
            sliceBySeq.putIfAbsent(slice.getSequence(), slice);
        }
        for (TaskAggregate task : tasks) {
            Long placeholderSequence = task.getSliceId();
            PlanSliceAggregate slice = ObjectUtil.isNull(placeholderSequence)
                    ? null
                    : sliceBySeq.get(placeholderSequence.intValue());
            task.bindPlanAndSlice(plan.getId(), slice == null ? null : slice.getId());
        }
        try {
            return taskRepository.saveAll(tasks);
        } catch (RuntimeException ex) {
            throw new PlanPersistenceException(PlanPersistenceException.Stage.TASK,
                    "Failed to persist tasks", ex);
        }
    }

}
