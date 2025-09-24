package com.patra.ingest.app.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.app.command.PlanTriggerCommand;
import com.patra.ingest.app.dto.PlanTriggerResult;
import com.patra.ingest.domain.model.expr.ExprPlanPrototype;
import com.patra.ingest.domain.port.CursorReadPort;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
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

        // Phase 3: 编译 Plan 级别业务表达式
        ExprPlanPrototype planPrototype = compilePlanBusinessExpression(norm, configSnapshot);

        // Phase 4: 快照落库（配置 + 表达式规范化形态）
        String configSnapshotJson = serializeConfigSnapshot(configSnapshot);
        schedule.recordSnapshots(Integer.toHexString(configSnapshot.hashCode()),
                configSnapshotJson, planPrototype.exprProtoHash(), planPrototype.exprDefinitionJson());
        schedule = scheduleInstanceRepository.save(schedule);

        // Phase 5: 前置验证（窗口合理性 / 背压 / 能力）
        long queuedTasks = taskInventoryPort.countQueuedTasks(
                command.provenanceCode().getCode(), command.operationType().name());
        plannerValidator.validateBeforeAssemble(norm, configSnapshot, window, queuedTasks);

        // Phase 6: 构建制品并组装蓝图
        ExprPlanArtifacts exprArtifacts = buildPlanArtifactsFromPrototype(planPrototype);
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
     * Phase 3: 编译 Plan 级别业务表达式
     * <p>
     * 核心设计理念：
     * 1. 只负责 Plan 业务表达式，不涉及任何 Slice 逻辑
     * 2. 返回 ExprPlanPrototype，表示这只是一个原型，不能直接使用
     * 3. Slice 表达式由 SliceStrategy 在 Phase 6 中动态构建
     */
    private ExprPlanPrototype compilePlanBusinessExpression(PlanTriggerNorm norm, ProvenanceConfigSnapshot configSnapshot) {
        log.info("开始编译 Plan 业务表达式，操作类型: {}", norm.operationType());

        // 1. 构建 Plan 级别业务表达式（包含业务逻辑，但无窗口约束）
        Expr planBusinessExpr = buildPlanBusinessExpression(norm, configSnapshot);
        String planExprJson = serializeExprToJson(planBusinessExpr);
        String planExprHash = Integer.toHexString(planExprJson.hashCode());

        // 2. 构建 Plan 原型
        ExprPlanPrototype planPrototype = new ExprPlanPrototype(planExprHash, planExprJson, Map.of());

        log.info("Plan 业务表达式编译完成，哈希: {}", planPrototype.exprProtoHash());

        return planPrototype;
    }

    /**
     * Phase 6: 构建 Plan 制品
     * 
     * 关键理念：
     * 1. Plan 表达式不需要编译，因为它不会被直接执行
     * 2. Plan 表达式没有参数，因为参数在构建时已经确定
     * 3. 只是简单地将原型转换为制品格式，供 SliceStrategy 使用
     */
    private ExprPlanArtifacts buildPlanArtifactsFromPrototype(ExprPlanPrototype planPrototype) {
        // 直接构建制品，不进行编译
        // Plan 表达式只是原型，不会被直接执行，所以不需要编译
        return new ExprPlanArtifacts(
                planPrototype.exprProtoHash(),           // Plan 业务表达式哈希
                planPrototype.exprDefinitionJson(),      // Plan 业务表达式定义（未编译）  
                Map.of(),                                // 无参数，因为参数在表达式构建时已确定
                List.of()                                // 空 Slice 模板，由 SliceStrategy 动态构建
        );
    }

    /**
     * 构建 Plan 级别业务表达式
     * 包含业务逻辑约束，但不包含窗口约束（窗口约束由 SliceStrategy 添加）
     */
    private Expr buildPlanBusinessExpression(PlanTriggerNorm norm, ProvenanceConfigSnapshot configSnapshot) {
        log.debug("构建 Plan 业务表达式，操作类型: {}", norm.operationType());

        // 根据配置和操作类型构建业务逻辑表达式
        // 例如：数据源过滤、状态约束、业务规则等
        // 注意：这里不包含时间窗口约束，时间窗口由 SliceStrategy 添加

        List<Expr> businessConstraints = buildBusinessConstraints(norm, configSnapshot);

        if (businessConstraints.isEmpty()) {
            // 如果没有业务约束，返回恒真（但实际使用时必须添加窗口约束）
            return Exprs.constTrue();
        } else if (businessConstraints.size() == 1) {
            return businessConstraints.getFirst();
        } else {
            // 多个业务约束用 AND 连接
            return Exprs.and(businessConstraints);
        }
    }

    /**
     * 构建业务约束列表
     */
    private List<Expr> buildBusinessConstraints(PlanTriggerNorm norm, ProvenanceConfigSnapshot configSnapshot) {
        List<Expr> constraints = new ArrayList<>();

        // 根据数据源配置添加约束

        // 根据操作类型添加特定约束
        if (norm.isUpdate()) {
            // UPDATE 操作可能有特定的业务约束
            constraints.addAll(buildUpdateBusinessConstraints(norm, configSnapshot));
        }

        // 可以根据 configSnapshot 添加更多业务约束
        // constraints.add(buildSourceConstraint(configSnapshot));
        // constraints.add(buildStatusConstraint(configSnapshot));

        return constraints;
    }

    /**
     * 构建 UPDATE 操作的业务约束
     */
    private List<Expr> buildUpdateBusinessConstraints(PlanTriggerNorm norm, ProvenanceConfigSnapshot configSnapshot) {
        List<Expr> constraints = new ArrayList<>();

        // UPDATE 操作的特定业务约束
        // 例如：只更新特定状态的记录
        // constraints.add(Exprs.term("status", "pending", TextMatch.EXACT));

        return constraints;
    }

    /**
     * 使用 ObjectMapper 将表达式序列化为 JSON 字符串
     */
    private String serializeExprToJson(Expr expr) {
        if (expr == null) {
            return "null";
        }
        try {
            return objectMapper.writeValueAsString(expr);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize expression to JSON", e);
        }
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
