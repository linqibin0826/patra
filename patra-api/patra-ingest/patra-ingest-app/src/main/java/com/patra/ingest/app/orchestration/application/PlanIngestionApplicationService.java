package com.patra.ingest.app.orchestration.application;

import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.And;
import com.patra.ingest.app.orchestration.command.PlanIngestionRequest;
import com.patra.ingest.app.orchestration.dto.PlanIngestionResult;
import com.patra.ingest.app.orchestration.assembly.PlanAssemblyRequest;
import com.patra.ingest.app.orchestration.assembly.PlanAssemblyService;
import com.patra.ingest.app.orchestration.expression.PlanExpressionDescriptor;
import com.patra.ingest.app.orchestration.window.PlanningWindowResolver;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.port.CursorRepository;
import com.patra.ingest.app.port.ProvenancePort;
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
import com.patra.ingest.app.validator.PlannerValidator;
import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import com.patra.starter.core.json.JsonNormalizer;
import com.patra.common.util.HashUtils;
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

        // Phase 1: 调度实例（持久化一次即可） + 来源配置快照
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
        PlanExpressionDescriptor expressionDescriptor = buildPlanExpression(norm, configSnapshot); // 外部条件未来在 buildPlanBusinessExpression 前构造成 Expr 再注入
        log.debug("plan-ingest expr built hash={} jsonSize={}", expressionDescriptor.hash(), expressionDescriptor.jsonSnapshot().length());

        // Phase 4: 前置验证（窗口合理性 / 背压 / 能力）
        long queuedTasks = taskRepository.countQueuedTasks(
                provenanceCode.getCode(), opCode(operationCode));
        plannerValidator.validateBeforeAssemble(norm, configSnapshot, window, queuedTasks);
        log.debug("plan-ingest validation passed queuedTasks={}", queuedTasks);

        // Phase 5: 组装蓝图
        PlanAssemblyRequest assemblyRequest = new PlanAssemblyRequest(norm, window, configSnapshot, expressionDescriptor);
        PlanAssembly assembly = planAssemblyService.assemble(assemblyRequest);

        // Phase 6: 持久化 Plan / Slice / Task
        PlanAggregate persistedPlan = planRepository.save(assembly.plan());
        List<PlanSliceAggregate> persistedSlices = persistSlices(persistedPlan, assembly.slices());
        List<TaskAggregate> persistedTasks = persistTasks(schedule, persistedPlan, persistedSlices, assembly.tasks());

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


    /**
     * Phase 3: 构建 Plan 级业务表达式（纯业务逻辑模板，不含窗口约束）。
     * 窗口约束在切片策略中动态添加；该表达式不会直接执行，因此不做编译。
     */
    private PlanExpressionDescriptor buildPlanExpression(PlanTriggerNorm norm, ProvenanceConfigSnapshot configSnapshot) { // 后续可抽取为 ExpressionCanonicalizer
        Expr expr = buildPlanBusinessExpression(norm, configSnapshot);
        String exprJson = Exprs.toJson(expr);
        JsonNormalizer.Result normalized = JsonNormalizer.normalizeDefault(exprJson);
        String canonicalJson = normalized.getCanonicalJson();
        String hash = HashUtils.sha256Hex(normalized.getHashMaterial());
        return new PlanExpressionDescriptor(expr, canonicalJson, hash);
    }

    /**
     * 构建 Plan 级别业务表达式
     * 包含业务逻辑约束，但不包含窗口约束（窗口约束由 slice planner 添加）
     */

    /**
     * 未来注入“外部（管理员配置）条件 → Expr” 的单一占位。
     * 当前返回 null 表示无外部条件。
     * 后续直接在此方法中把前端参数转换为一棵 Expr（可以是 AND/OR 复合），然后在 buildPlanBusinessExpression 中纳入组合即可。
     */
    private Expr buildExternalConditionsExpr(PlanTriggerNorm norm) {
        return null; // 占位：后续实现
    }

    private Expr buildPlanBusinessExpression(PlanTriggerNorm norm, ProvenanceConfigSnapshot configSnapshot) {
        log.debug("构建 Plan 业务表达式，操作类型: {}", norm.operationCode());

        // 根据配置和操作类型构建业务逻辑表达式
        // 例如：数据源过滤、状态约束、业务规则等
        // 注意：这里不包含时间窗口约束，时间窗口由 slice planner 添加

        List<Expr> businessConstraints = buildBusinessConstraints(norm, configSnapshot);
        // 外部条件占位注入
        Expr external = buildExternalConditionsExpr(norm);
        if (external != null) {
            if (external instanceof And(List<Expr> children)) {
                // 若外部本身是 AND，扁平化可减少树深度
                businessConstraints.addAll(children);
            } else {
                businessConstraints.add(external);
            }
        }

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

    // 读写合并后，游标键的选择在窗口策略中统一处理，不再由应用服务关心
}
