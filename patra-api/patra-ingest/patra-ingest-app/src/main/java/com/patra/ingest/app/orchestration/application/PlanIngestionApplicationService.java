package com.patra.ingest.app.orchestration.application;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.ObjectUtil;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.app.orchestration.application.support.PlanExpressionBuilder;
import com.patra.ingest.app.orchestration.assembly.PlanAssemblyRequest;
import com.patra.ingest.app.orchestration.assembly.PlanAssemblyService;
import com.patra.ingest.app.orchestration.command.PlanIngestionRequest;
import com.patra.ingest.app.orchestration.dto.PlanIngestionResult;
import com.patra.ingest.app.orchestration.expression.PlanExpressionDescriptor;
import com.patra.ingest.app.orchestration.outbox.TaskOutboxPublisher;
import com.patra.ingest.app.orchestration.window.PlanningWindowResolver;
import com.patra.ingest.app.port.ProvenancePort;
import com.patra.ingest.app.validator.PlannerValidator;
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
 * 采集计划编排核心服务，承接调度层调用，完成计划、切片、任务的全链路生成与补偿。
 * <p>负责串联配置读取、窗口计算、装配持久化及 Outbox 发布，是调度入口的核心实现。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanIngestionApplicationService implements PlanIngestionUseCase {

    /** 来源配置查询端口 */
    private final ProvenancePort provenancePort;
    /** 游标仓储，用于获取水位 */
    private final CursorRepository cursorRepository;
    /** 任务仓储 */
    private final TaskRepository taskRepository;
    /** 计划窗口解析策略 */
    private final PlanningWindowResolver planningWindowResolver;
    /** 编排前置校验器 */
    private final PlannerValidator plannerValidator;
    /** 计划装配服务 */
    private final PlanAssemblyService planAssemblyService;
    /** 任务 Outbox 发布器 */
    private final TaskOutboxPublisher taskOutboxPublisher;
    /** 计划表达式构建器 */
    private final PlanExpressionBuilder planExpressionBuilder;

    /** 调度实例仓储 */
    private final ScheduleInstanceRepository scheduleInstanceRepository;
    /** 计划仓储 */
    private final PlanRepository planRepository;
    /** 切片仓储 */
    private final PlanSliceRepository planSliceRepository;

    /**
     * 受调度触发的计划编排主流程。
     *
     * @param request 调度请求，包括来源、操作、窗口及扩展参数
     * @return 计划执行结果摘要
     */
    @Override
    @Transactional
    public PlanIngestionResult ingestPlan(PlanIngestionRequest request) {
        ProvenanceCode provenanceCode = request.provenanceCode();
        OperationCode operationCode = request.operationCode();

        Instant now = request.triggeredAt();
        log.info("plan-ingest start, provenance={}, op={}, triggeredAt={}", provenanceCode, operationCode, now);

        // Phase 1: 调度实例 + 来源配置快照
        ScheduleInstanceAggregate schedule = persistScheduleInstance(request);
        ProvenanceConfigSnapshot configSnapshot = provenancePort.fetchConfig(
                provenanceCode, request.endpoint(), operationCode
        );
        PlanTriggerNorm norm = buildTriggerNorm(schedule, request);

        // Phase 2: 游标水位 (仅前进) + 解析计划窗口（TIME 策略）
        Instant cursorWatermark = cursorRepository
                .findLatestGlobalTimeWatermark(provenanceCode.getCode(), opCode(operationCode))
                .orElse(null);
        PlannerWindow window = planningWindowResolver.resolveWindow(norm, configSnapshot, cursorWatermark, now);
        log.debug("plan-ingest window resolved provenance={} op={} cursorWatermark={} window=[{}, {})",
                provenanceCode, operationCode, cursorWatermark, window == null ? null : window.from(), window == null ? null : window.to());

        // Phase 3: 构建 Plan 级别业务表达式（内存对象，不编译）
        PlanExpressionDescriptor expressionDescriptor = planExpressionBuilder.build(norm, configSnapshot);
        log.debug("plan-ingest expr built hash={} jsonSize={}", expressionDescriptor.hash(), expressionDescriptor.jsonSnapshot().length());

        // Phase 4: 前置验证（窗口合理性 / 背压 / 能力）
        long queuedTasks = taskRepository.countQueuedTasks(
                provenanceCode.getCode(), opCode(operationCode));
        plannerValidator.validateBeforeAssemble(norm, configSnapshot, window, queuedTasks);
        log.debug("plan-ingest validation passed queuedTasks={}", queuedTasks);

        // Phase 5: 组装蓝图
        PlanAssemblyRequest assemblyRequest = new PlanAssemblyRequest(norm, window, configSnapshot, expressionDescriptor);
        PlanAssembly assembly = planAssemblyService.assemble(assemblyRequest);

        PlanAggregate draftPlan = assembly.plan();
        PlanAggregate existingPlan = planRepository.findByPlanKey(draftPlan.getPlanKey()).orElse(null);
        if (existingPlan != null) {
            log.info("plan-ingest dedup hit existing planKey={}, reuse planId={}", draftPlan.getPlanKey(), existingPlan.getId());
            List<PlanSliceAggregate> existingSlices = planSliceRepository.findByPlanId(existingPlan.getId());
            List<TaskAggregate> existingTasks = taskRepository.findByPlanId(existingPlan.getId());

            List<TaskAggregate> retryTasks = new ArrayList<>();
            for (TaskAggregate task : existingTasks) {
                if (shouldRetry(task)) {
                    // 重置失败/取消任务，准备重新排队
                    task.prepareForRetry();
                    taskRepository.save(task);
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
        PlanAggregate persistedPlan = planRepository.save(draftPlan);
        List<PlanSliceAggregate> persistedSlices = persistSlices(persistedPlan, assembly.slices());
        List<TaskAggregate> persistedTasks = persistTasks(persistedPlan, persistedSlices, assembly.tasks());

        List<TaskQueuedEvent> queuedEvents = collectQueuedEvents(persistedTasks);
        taskOutboxPublisher.publish(queuedEvents, persistedPlan, schedule);

        log.info("plan-ingest success, planId={}, sliceCount={}, taskCount={}, window=[{}, {})", persistedPlan.getId(), persistedSlices.size(), persistedTasks.size(), window == null ? null : window.from(), window == null ? null : window.to());

        return new PlanIngestionResult(
                schedule.getId(),
                persistedPlan.getId(),
                persistedSlices.stream().map(PlanSliceAggregate::getId).collect(Collectors.toList()),
                persistedTasks.size(),
                assembly.status().name());
    }


    /**
     * 统一获取操作码字符串，避免多处判空三元表达式。
     */
    private String opCode(OperationCode op) {
        return op == null ? null : op.getCode();
    }

    /**
     * 根据调度请求落库或更新调度实例。
     *
     * @param request 调度请求
     * @return 持久化后的调度实例
     */
    private ScheduleInstanceAggregate persistScheduleInstance(PlanIngestionRequest request) {
        ScheduleInstanceAggregate schedule = ScheduleInstanceAggregate.start(
                request.scheduler(),
                request.schedulerJobId(),
                request.schedulerLogId(),
                request.triggerType(),
                request.triggeredAt(),
                request.triggerParams(),
                request.provenanceCode());
        return scheduleInstanceRepository.saveOrUpdateInstance(schedule);
    }

    /**
     * 基于调度实例与请求构建领域触发规范。
     *
     * @param schedule 调度实例
     * @param request 调度请求
     * @return 触发规范
     */
    private PlanTriggerNorm buildTriggerNorm(ScheduleInstanceAggregate schedule, PlanIngestionRequest request) {
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
     * 收集任务聚合产生的入队事件。
     *
     * @param tasks 任务集合
     * @return 入队事件列表
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
     * 判定任务是否需要发起补偿重试。
     *
     * @param task 任务聚合
     * @return true 表示需要重试
     */
    private boolean shouldRetry(TaskAggregate task) {
        TaskStatus status = task.getStatus();
        return status == TaskStatus.FAILED || status == TaskStatus.CANCELLED;
    }

    private List<PlanSliceAggregate> persistSlices(PlanAggregate plan, List<PlanSliceAggregate> slices) {
        if (CollUtil.isEmpty(slices)) {
            return List.of();
        }
        slices.forEach(slice -> slice.bindPlan(plan.getId()));
        return planSliceRepository.saveAll(slices);
    }

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
        return taskRepository.saveAll(tasks);
    }

}
