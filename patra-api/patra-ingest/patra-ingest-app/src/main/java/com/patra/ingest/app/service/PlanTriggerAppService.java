package com.patra.ingest.app.service;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.app.command.PlanTriggerCommand;
import com.patra.ingest.app.dto.PlanTriggerResult;
import com.patra.ingest.app.model.PlanBusinessExpr;
import com.patra.ingest.app.model.PlanBlueprintCommand;
import com.patra.ingest.app.util.ExprHashUtil;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.port.CursorReadPort;
import com.patra.ingest.app.port.outbound.ProvenancePort;
import com.patra.ingest.domain.port.TaskInventoryPort;
import com.patra.ingest.app.usecase.PlanTriggerUseCase;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanAssembly;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
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

    private final ProvenancePort provenancePort;
    private final CursorReadPort cursorReadPort;
    private final TaskInventoryPort taskInventoryPort;
    private final PlannerWindowPolicy plannerWindowPolicy;
    private final PlannerValidator plannerValidator;
    private final PlannerEngine plannerEngine;

    private final ScheduleInstanceRepository scheduleInstanceRepository;
    private final PlanRepository planRepository;
    private final PlanSliceRepository planSliceRepository;
    private final TaskRepository taskRepository;

    // ObjectMapper 不再用于表达式序列化，改用 Exprs.toJson；如后续需要通用 JSON，可再注入。

    @Override
    @Transactional
    public PlanTriggerResult triggerPlan(PlanTriggerCommand command) {
        ProvenanceCode provenanceCode = command.provenanceCode();
        OperationCode operationCode = command.operationCode();

        log.info("plan-trigger start, provenance={}, op={}", provenanceCode, operationCode);
        Instant now = command.triggeredAt();

        // Phase 1: 调度实例 & 来源配置运行期快照
        ScheduleInstanceAggregate schedule = persistScheduleInstance(command);

        ProvenanceConfigSnapshot configSnapshot = provenancePort.fetchConfig(
                provenanceCode, command.endpoint(), operationCode
        );
        PlanTriggerNorm norm = buildTriggerNorm(schedule, command);

        // Phase 2: 游标水位 (仅前进) + 解析计划窗口（TIME 策略）
        Instant cursorWatermark = cursorReadPort.loadForwardWatermark(provenanceCode, operationCode);
        PlannerWindow window = plannerWindowPolicy.resolveWindow(norm, configSnapshot, cursorWatermark, now);

        // Phase 3: 构建 Plan 级别业务表达式（内存对象，不编译）
        PlanBusinessExpr planBusinessExpr = buildPlanBusinessExpr(norm, configSnapshot);

        // Phase 4: scheduleInstance落库
        schedule = scheduleInstanceRepository.saveOrUpdateInstance(schedule);

        // Phase 5: 前置验证（窗口合理性 / 背压 / 能力）
        long queuedTasks = taskInventoryPort.countQueuedTasks(
                provenanceCode.getCode(), operationCode.name());
        plannerValidator.validateBeforeAssemble(norm, configSnapshot, window, queuedTasks);

        // Phase 6: 组装蓝图
        PlanBlueprintCommand blueprint = new PlanBlueprintCommand(norm, window, configSnapshot, planBusinessExpr);
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
                command.scheduler(),
                command.schedulerJobId(),
                command.schedulerLogId(),
                command.triggerType(),
                command.triggeredAt(),
                command.provenanceCode());
        return scheduleInstanceRepository.saveOrUpdateInstance(schedule);
    }

    private PlanTriggerNorm buildTriggerNorm(ScheduleInstanceAggregate schedule, PlanTriggerCommand command) {
        return new PlanTriggerNorm(
                schedule.getId(),
                command.provenanceCode(),
                command.endpoint(),
                command.operationCode(),
                command.step(),
                command.triggerType(),
                command.scheduler(),
                command.schedulerJobId(),
                command.schedulerLogId(),
                command.windowFrom(),
                command.windowTo(),
                command.priority(),
                command.triggerParams());
    }


    /**
     * Phase 3: 构建 Plan 级业务表达式（纯业务逻辑模板，不含窗口约束）。
     * 窗口约束在切片策略中动态添加；该表达式不会直接执行，因此不做编译。
     */
    private PlanBusinessExpr buildPlanBusinessExpr(PlanTriggerNorm norm, ProvenanceConfigSnapshot configSnapshot) {
        Expr expr = buildPlanBusinessExpression(norm, configSnapshot);
        String json = Exprs.toJson(expr);
        String hash = ExprHashUtil.sha256Hex(json);
        return new PlanBusinessExpr(expr, json, hash);
    }

    /**
     * 构建 Plan 级别业务表达式
     * 包含业务逻辑约束，但不包含窗口约束（窗口约束由 SliceStrategy 添加）
     */
    private Expr buildPlanBusinessExpression(PlanTriggerNorm norm, ProvenanceConfigSnapshot configSnapshot) {
        log.debug("构建 Plan 业务表达式，操作类型: {}", norm.operationCode());

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

    /**
     * 根据设计：cursor_key 优先使用 offsetFieldName（若是时间字段），否则使用 defaultDateFieldName。
     * 这里无法直接判断 offsetFieldName 是否时间字段（需要来源 schema），简化：若存在 offsetFieldName 则用之；否则 fallback defaultDateFieldName；仍为空则使用操作类型默认键。
     */
    private String resolveCursorKey(ProvenanceConfigSnapshot snapshot, OperationCode op) {
        ProvenanceConfigSnapshot.WindowOffsetConfig w = snapshot.windowOffset();
        if (w != null) {
            if (w.offsetFieldName() != null && !w.offsetFieldName().isBlank()) {
                return w.offsetFieldName();
            }
            if (w.defaultDateFieldName() != null && !w.defaultDateFieldName().isBlank()) {
                return w.defaultDateFieldName();
            }
        }
        // UPDATE 特殊：刷新检查时间统一使用固定键
        if (op == OperationCode.UPDATE) {
            return "refresh_checked_at";
        }
        return "_ts"; // 通用兜底键
    }
}
