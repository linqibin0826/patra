package com.patra.ingest.app.service;

import com.patra.ingest.app.command.PlanTriggerCommand;
import com.patra.ingest.app.dto.PlanTriggerResult;
import com.patra.ingest.app.port.outbound.CursorReadPort;
import com.patra.ingest.app.port.outbound.ExprCompilerPort;
import com.patra.ingest.app.port.outbound.ExprPrototypePort;
import com.patra.ingest.app.port.outbound.ProvenanceConfigPort;
import com.patra.ingest.app.port.outbound.TaskInventoryPort;
import com.patra.ingest.app.usecase.PlanTriggerUseCase;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanAssembly;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.command.PlanBlueprintCommand;
import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.value.PlannerWindow;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.domain.port.ScheduleInstanceRepository;
import com.patra.ingest.domain.port.TaskRepository;
import com.patra.ingest.app.engine.PlannerEngine;
import com.patra.ingest.app.policy.PlannerWindowPolicy;
import com.patra.ingest.app.validator.PlannerValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 计划触发应用服务。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanTriggerAppService implements PlanTriggerUseCase {

    private final ProvenanceConfigPort provenanceConfigPort;
    private final ExprPrototypePort exprPrototypePort;
    private final CursorReadPort cursorReadPort;
    private final TaskInventoryPort taskInventoryPort;
    private final PlannerWindowPolicy plannerWindowPolicy;
    private final PlannerValidator plannerValidator;
    private final PlannerEngine plannerEngine;

    private final ScheduleInstanceRepository scheduleInstanceRepository;
    private final PlanRepository planRepository;
    private final PlanSliceRepository planSliceRepository;
    private final TaskRepository taskRepository;

    @Override
    @Transactional
    public PlanTriggerResult triggerPlan(PlanTriggerCommand command) {
        log.info("plan-trigger start, provenance={}, op={}", command.provenanceCode(), command.operationType());
        Instant currentTime = Instant.now();

        ScheduleInstanceAggregate schedule = persistScheduleInstance(command);
        ProvenanceConfigSnapshot configSnapshot = provenanceConfigPort.fetchConfig(
                command.provenanceCode(),
                command.endpointCode(),
                command.operationType());

        // 根据配置的内容，通过 patra-spring-boot-starter-expr 编译表达式原型



//        schedule.recordSnapshots(buildConfigSnapshotJson(configSnapshot), artifacts.exprProtoHash(), artifacts.exprProtoSnapshotJson());
        schedule = scheduleInstanceRepository.save(schedule);

        PlanTriggerNorm norm = buildTriggerNorm(schedule, command);
        Instant cursorWatermark = cursorReadPort.loadForwardWatermark(command.provenanceCode().getCode(), command.operationType().name());
        PlannerWindow window = plannerWindowPolicy.resolveWindow(norm, configSnapshot, cursorWatermark, currentTime);
        long queuedTasks = taskInventoryPort.countQueuedTasks(command.provenanceCode().getCode(), command.operationType().name());
        plannerValidator.validateBeforeAssemble(norm, configSnapshot, window, queuedTasks);

        PlanBlueprintCommand blueprint = new PlanBlueprintCommand(norm, window, configSnapshot, null);
        PlanAssembly assembly = plannerEngine.assemble(blueprint);

        PlanAggregate persistedPlan = planRepository.save(assembly.plan());
        List<PlanSliceAggregate> persistedSlices = persistSlices(persistedPlan, assembly.slices());
        List<TaskAggregate> persistedTasks = persistTasks(schedule, persistedPlan, persistedSlices, assembly.tasks());

        log.info("plan-trigger success, planId={}, sliceCount={}, taskCount={}",
                persistedPlan.getId(), persistedSlices.size(), persistedTasks.size());

        return new PlanTriggerResult(
                schedule.getId(),
                persistedPlan.getId(),
                persistedSlices.stream().map(PlanSliceAggregate::getId).collect(Collectors.toList()),
                persistedTasks.size(),
                assembly.status().name());
    }

    private ScheduleInstanceAggregate persistScheduleInstance(PlanTriggerCommand command) {
        ScheduleInstanceAggregate schedule = ScheduleInstanceAggregate.start(
                command.schedulerCode(),
                command.schedulerJobId(),
                command.schedulerLogId(),
                command.triggerType(),
                Instant.now(),
                command.provenanceCode());
        return scheduleInstanceRepository.save(schedule);
    }

    private PlanTriggerNorm buildTriggerNorm(ScheduleInstanceAggregate schedule, PlanTriggerCommand command) {
        return new PlanTriggerNorm(
                schedule.getId(),
                command.provenanceCode(),
                command.endpointCode(),
                command.operationType(),
                command.triggerType(),
                command.schedulerCode(),
                command.schedulerJobId(),
                command.schedulerLogId(),
                command.windowFrom(),
                command.windowTo(),
                command.priority(),
                command.triggerParams());
    }

    private String buildConfigSnapshotJson(ProvenanceConfigSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return snapshot.provenance() == null ? null : "{\"provenanceCode\":\"" + snapshot.provenance().code() + "\"}";
    }

    private List<PlanSliceAggregate> persistSlices(PlanAggregate plan, List<PlanSliceAggregate> slices) {
        for (PlanSliceAggregate slice : slices) {
            slice.bindPlan(plan.getId());
        }
        return planSliceRepository.saveAll(slices);
    }

    private List<TaskAggregate> persistTasks(ScheduleInstanceAggregate schedule,
                                             PlanAggregate plan,
                                             List<PlanSliceAggregate> persistedSlices,
                                             List<TaskAggregate> tasks) {
        Map<Integer, PlanSliceAggregate> sliceBySeq = persistedSlices.stream()
                .collect(Collectors.toMap(PlanSliceAggregate::getSequence, it -> it));
        for (TaskAggregate task : tasks) {
            Long placeholderSliceId = task.getSliceId();
            int sequence = placeholderSliceId == null ? 0 : placeholderSliceId.intValue();
            PlanSliceAggregate slice = sliceBySeq.get(sequence);
            if (slice != null) {
                task.bindPlanAndSlice(plan.getId(), slice.getId());
            } else {
                task.bindPlanAndSlice(plan.getId(), null);
            }
        }
        return taskRepository.saveAll(tasks);
    }
}
