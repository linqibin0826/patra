package com.patra.ingest.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.app.command.PlanTriggerCommand;
import com.patra.ingest.app.dto.PlanTriggerResult;
import com.patra.ingest.domain.model.expr.ExprPlanPrototype;
import com.patra.ingest.domain.port.CursorReadPort;
import com.patra.ingest.app.port.outbound.ExprCompilerPort;
import com.patra.ingest.domain.port.ExprPrototypePort;
import com.patra.ingest.app.port.outbound.ProvenanceConfigPort;
import com.patra.ingest.domain.port.TaskInventoryPort;
import com.patra.ingest.app.usecase.PlanTriggerUseCase;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanAssembly;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.command.PlanBlueprintCommand;
import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.expr.ExprPlanArtifacts;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.value.PlannerWindow;
import com.patra.ingest.domain.port.PlanRepository;
import com.patra.ingest.domain.port.PlanSliceRepository;
import com.patra.ingest.domain.port.ScheduleInstanceRepository;
import com.patra.ingest.domain.port.TaskRepository;
import com.patra.ingest.app.engine.PlannerEngine;
import com.patra.ingest.app.policy.PlannerWindowPolicy;
import com.patra.ingest.app.validator.PlannerValidator;
import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import com.patra.starter.expr.compiler.model.OperationCodes;
import com.patra.starter.expr.compiler.model.TaskTypes;
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
    private final ExprCompilerPort exprCompilerPort;
    private final CursorReadPort cursorReadPort;
    private final TaskInventoryPort taskInventoryPort;
    private final PlannerWindowPolicy plannerWindowPolicy;
    private final PlannerValidator plannerValidator;
    private final PlannerEngine plannerEngine;

    private final ScheduleInstanceRepository scheduleInstanceRepository;
    private final PlanRepository planRepository;
    private final PlanSliceRepository planSliceRepository;
    private final TaskRepository taskRepository;

    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public PlanTriggerResult triggerPlan(PlanTriggerCommand command) {
        log.info("plan-trigger start, provenance={}, op={}", command.provenanceCode(), command.operationType());
        Instant now = Instant.now();

        // Phase 1: 调度实例 & 来源配置运行期快照
        ScheduleInstanceAggregate schedule = persistScheduleInstance(command);
        ProvenanceConfigSnapshot configSnapshot = provenanceConfigPort.fetchConfig(
                command.provenanceCode(), command.endpointCode(), command.operationType());
        PlanTriggerNorm norm = buildTriggerNorm(schedule, command);

        // Phase 2: 游标水位 (仅前进) + 解析计划窗口（TIME 策略）
        Instant cursorWatermark = cursorReadPort.loadForwardWatermark(
                command.provenanceCode().getCode(), command.operationType().name());


        PlannerWindow window = plannerWindowPolicy.resolveWindow(norm, configSnapshot, cursorWatermark, now);

        // Phase 3: 计划级表达式构造（窗口→RANGE；UPDATE→TRUE）并通过 Port 生成产物
        Expr planExpr = buildPlanExpression(norm, configSnapshot, window);


        ExprPlanArtifacts exprArtifacts = compilePlanLevelArtifacts(planExpr, norm, configSnapshot);

        // Phase 4: 快照落库（配置 + 表达式规范化形态）
        String configSnapshotJson = serializeConfigSnapshot(configSnapshot);
        schedule.recordSnapshots(Integer.toHexString(configSnapshot.hashCode()),
                configSnapshotJson, exprArtifacts.exprProtoHash(), exprArtifacts.exprProtoSnapshotJson());
        schedule = scheduleInstanceRepository.save(schedule);

        // Phase 5: 前置验证（窗口合理性 / 背压 / 能力）
        long queuedTasks = taskInventoryPort.countQueuedTasks(
                command.provenanceCode().getCode(), command.operationType().name());
        plannerValidator.validateBeforeAssemble(norm, configSnapshot, window, queuedTasks);

        // Phase 6: 组装蓝图 (exprArtifacts 注入) → assemble
        PlanBlueprintCommand blueprint = new PlanBlueprintCommand(norm, window, configSnapshot, exprArtifacts);
        PlanAssembly assembly = plannerEngine.assemble(blueprint);

        // Phase 7: 持久化 Plan / Slice / Task
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
        command.step(),
        command.triggerType(),
        command.schedulerCode(),
        command.schedulerJobId(),
        command.schedulerLogId(),
        command.windowFrom(),
        command.windowTo(),
        command.priority(),
        command.triggerParams());
    }

    /**
     * 基于窗口和配置构建计划表达式。
     */
    private Expr buildPlanExpression(PlanTriggerNorm norm,
                                     ProvenanceConfigSnapshot configSnapshot,
                                     PlannerWindow window) {

        log.debug("building plan expression for provenance={}, operation={}, window={}",
                norm.provenanceCode(), norm.operationType(), window);

        // 如果是 UPDATE 操作或者没有窗口，返回常量 true（全量）
        if (norm.isUpdate() || window.from() == null || window.to() == null) {
            log.debug("building constant true expression for update/full mode");
            return Exprs.constTrue();
        }

        // 基于窗口配置构建时间范围表达式
        ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset = configSnapshot.windowOffset();
        String dateField = determineDateField(windowOffset);

        // 构建时间范围表达式
        Expr timeRangeExpr = Exprs.rangeDateTime(dateField, window.from(), window.to());

        log.debug("built time range expression: field={}, from={}, to={}", dateField, window.from(), window.to());

        // 可以根据需要添加其他条件
        return timeRangeExpr;
    }

    /**
     * 确定时间字段名称。
     * TODO
     */
    private String determineDateField(ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset) {
        if (windowOffset == null) {
            log.warn("window offset config is null, using default 'date' field");
            return "date"; // 默认字段名
        }

        // 优先使用配置的偏移字段
        if (windowOffset.offsetFieldName() != null && !windowOffset.offsetFieldName().trim().isEmpty()) {
            return windowOffset.offsetFieldName();
        }

        // 使用默认日期字段
        if (windowOffset.defaultDateFieldName() != null && !windowOffset.defaultDateFieldName().trim().isEmpty()) {
            return windowOffset.defaultDateFieldName();
        }

        // 最后降级到通用字段名
        return "date";
    }

    /**
     * 编译表达式。
     */
    private ExprPlanArtifacts compilePlanLevelArtifacts(Expr expression,
                                                        PlanTriggerNorm norm,
                                                        ProvenanceConfigSnapshot snapshot) {
        // 1. 获取表达式原型（可来自 registry / cache）
        var prototype = exprPrototypePort.fetchPrototype(
                norm.provenanceCode().getCode(),
                norm.endpointCode() == null ? null : norm.endpointCode().name().toLowerCase(),
                determineOperationCode(norm));
        // 2. 目前未对 AST 融合原型；简单将窗口表达式附加（占位）
        String mergedDefinition = mergeProtoWithWindowExpr(prototype.exprDefinitionJson(), expression);
        // 3. 重新构造一个新的原型再编译（stub 编译为直返）
        var mergedProto = new ExprPlanPrototype(
                Integer.toHexString(mergedDefinition.hashCode()),
                mergedDefinition,
                prototype.metadata());
        ExprPlanArtifacts artifacts = exprCompilerPort.compilePrototype(mergedProto);
        log.debug("expr artifacts ready hash={} protoLen={}", artifacts.exprProtoHash(), artifacts.exprProtoSnapshotJson().length());
        return artifacts;
    }

    /**
     * 确定任务类型。
     */
    private String determineTaskType(PlanTriggerNorm norm) {
        if (norm.isUpdate()) {
            return TaskTypes.UPDATE;
        } else if (norm.isHarvest()) {
            return TaskTypes.SEARCH;
        }
        return TaskTypes.SEARCH; // 默认
    }

    /**
     * 确定操作代码。
     */
    private String determineOperationCode(PlanTriggerNorm norm) {
        // 可以根据 norm.operationType() 映射到具体的操作代码
        String operationType = norm.operationType().name();
        return switch (operationType.toUpperCase()) {
            case "HARVEST" -> OperationCodes.SEARCH;
            case "UPDATE" -> OperationCodes.SEARCH;
            case "DETAIL" -> OperationCodes.DETAIL;
            case "COUNT" -> OperationCodes.COUNT;
            default -> OperationCodes.SEARCH;
        };
    }

    /**
     * 生成表达式哈希。
     */
    private String mergeProtoWithWindowExpr(String protoJson, Expr windowExpr) {
        // 简化合并策略：若窗口表达式为 TRUE 则直接返回原型；否则包一层复合 JSON
        String exprPart = windowExpr.toString();
        if ("TRUE".equalsIgnoreCase(exprPart)) {
            return protoJson;
        }
        return "{\"type\":\"AND\",\"children\":[" + protoJson + ",{" + "\"rangeExpr\":\"" + exprPart.replace("\"", "'") + "\"}" + "]}";
    }

    private String serializeConfigSnapshot(ProvenanceConfigSnapshot snapshot) {
        if (snapshot == null) return null;
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize config snapshot", e);
        }
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
