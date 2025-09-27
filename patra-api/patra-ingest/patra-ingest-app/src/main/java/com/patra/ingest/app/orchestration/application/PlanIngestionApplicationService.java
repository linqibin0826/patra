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
 * Ingestion orchestration entry point used by various adapters.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanIngestionApplicationService implements PlanIngestionUseCase {

    private final ProvenancePort provenancePort;
    private final CursorRepository cursorRepository;
    private final TaskRepository taskRepository;
    private final PlanningWindowResolver planningWindowResolver;
    private final PlannerValidator plannerValidator;
    private final PlanAssemblyService planAssemblyService;
    private final TaskOutboxPublisher taskOutboxPublisher;
    private final PlanExpressionBuilder planExpressionBuilder;

    private final ScheduleInstanceRepository scheduleInstanceRepository;
    private final PlanRepository planRepository;
    private final PlanSliceRepository planSliceRepository;

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
