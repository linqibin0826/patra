package com.patra.ingest.app.usecase.plan.assembler;

import cn.hutool.core.text.CharSequenceUtil;
import com.patra.common.enums.Priority;
import com.patra.common.json.JsonNormalizer;
import com.patra.common.util.HashUtils;
import com.patra.expr.canonical.ExprCanonicalSnapshot;
import com.patra.expr.canonical.ExprCanonicalizer;
import com.patra.ingest.app.usecase.plan.expression.PlanExpressionDescriptor;
import com.patra.ingest.app.usecase.plan.slicer.SlicePlanner;
import com.patra.ingest.app.usecase.plan.slicer.SlicePlannerRegistry;
import com.patra.ingest.app.usecase.plan.slicer.SliceStrategy;
import com.patra.ingest.app.usecase.plan.slicer.model.SlicePlan;
import com.patra.ingest.app.usecase.plan.slicer.model.SlicePlanningContext;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanAssembly;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.command.PlanTriggerNorm;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.value.PlannerWindow;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/**
 * 默认计划装配服务实现。
 * <p>
 * 负责将 {@link PlanAssemblyRequest} 转换为 {@link PlanAssembly} 聚合集合：
 * <ol>
 *   <li>确定切片策略（UPDATE → SINGLE；否则 TIME）</li>
 *   <li>规范化配置（canonical JSON + hash material）</li>
 *   <li>创建 Plan 聚合（含表达式快照 / 配置签名 / 窗口 / 策略编码）</li>
 *   <li>调用切片策略生成草稿（SlicePlan）并转为 PlanSlice 聚合（表达式 canonical + hash）</li>
 *   <li>为每个切片派生 Task 聚合（幂等键、优先级、计划执行时间）</li>
 *   <li>根据切片 / 任务是否为空标记 Plan 状态（FAILED 或 READY）</li>
 * </ol>
 * </p>
 * <h4>幂等策略</h4>
 * <p>PlanKey 在 createPlanAggregate 内基于（provenance, operation, endpoint?, windowFrom-windowTo?）生成；任务幂等键使用（provenance | operation | sliceSignatureHash）。
 * 该类不做重复检测，由上层持久化与唯一索引确保。</p>
 * <h4>失败条件</h4>
 * <ul>
 *   <li>未找到切片策略实现（registry 返回 null） → 返回 FAILED（空集合）</li>
 *   <li>切片策略返回空列表 → FAILED</li>
 *   <li>切片存在但派生任务为空（理论不应出现）→ FAILED</li>
 * </ul>
 * <h4>复杂度</h4>
 * <p>O(n) n=切片数；规范化与哈希为线性。</p>
 * <h4>线程安全</h4>
 * <p>无共享可变状态；registry 只读查找，可单例。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Component
public class PlanAssemblerImpl implements PlanAssembler {

    /**
     * 默认 JSON 规范化器，用于保持配置与策略参数的稳定序列化形态。
     */
    private static final JsonNormalizer DEFAULT_NORMALIZER = JsonNormalizer.usingDefault();

    /**
     * 任务参数专用规范化器：关闭布尔、时间的强制推断，避免序号 0/1 被自动转为布尔或时间戳。
     */
    private static final JsonNormalizer TASK_PARAM_NORMALIZER = JsonNormalizer.withConfig(
            JsonNormalizer.Config.builder()
                    .coerceBoolean(JsonNormalizer.Config.CoerceBoolean.NONE)
                    .coerceTime(false)
                    .build()
    );

    private final SlicePlannerRegistry slicePlannerRegistry;

    public PlanAssemblerImpl(SlicePlannerRegistry slicePlannerRegistry) {
        this.slicePlannerRegistry = slicePlannerRegistry;
    }

    /**
     * 装配入口。
     * <p>无副作用（除时间调用 Instant.now 与规范化过程），不持久化。</p>
     *
     * @param request 装配请求
     * @return PlanAssembly（READY 或 FAILED）
     */
    @Override
    public PlanAssembly assemble(PlanAssemblyRequest request) {
        PlanTriggerNorm norm = request.triggerNorm();
        PlannerWindow window = request.window();
        PlanExpressionDescriptor planExpression = request.planExpression();
        ProvenanceConfigSnapshot configSnapshot = request.configSnapshot();

        SliceStrategy sliceStrategy = determineSliceStrategy(norm);
        JsonNormalizer.Result configCanonical = normalizeConfigSnapshot(configSnapshot);

        PlanAggregate plan = createPlanAggregate(norm, window, planExpression, sliceStrategy, configCanonical);
        plan.startSlicing();

        SliceGenerationResult sliceResult = createSlices(norm, window, planExpression, configSnapshot, sliceStrategy);
        List<PlanSliceAggregate> slices = sliceResult.aggregates();
        List<TaskAggregate> tasks = slices.isEmpty() ? List.of() : createTasks(norm, window, sliceResult);

        if (slices.isEmpty() || tasks.isEmpty()) {
            plan.markFailed();
            return new PlanAssembly(plan, slices, tasks, PlanAssembly.PlanAssemblyStatus.FAILED);
        }

        plan.markReady();
        return new PlanAssembly(plan, slices, tasks, PlanAssembly.PlanAssemblyStatus.READY);
    }

    /**
     * 构建 Plan 聚合根。
     * <p>包含：表达式哈希、表达式 JSON 快照、配置 canonical JSON + hash、窗口、切片策略、切片参数 JSON。</p>
     */
    private PlanAggregate createPlanAggregate(PlanTriggerNorm norm,
                                              PlannerWindow window,
                                              PlanExpressionDescriptor planExpression,
                                              SliceStrategy sliceStrategy,
                                              JsonNormalizer.Result configSnapshot) {
        String planKey = buildPlanKey(norm, window);
        String configSnapshotJson = configSnapshot == null ? null : configSnapshot.getCanonicalJson();
        String configSnapshotHash = configSnapshot == null ? null : HashUtils.sha256Hex(configSnapshot.getHashMaterial());

        String sliceStrategyCode = sliceStrategy.getCode();
        return PlanAggregate.create(
                norm.scheduleInstanceId(),
                planKey,
                norm.provenanceCode().getCode(),
                norm.endpoint() == null ? null : norm.endpoint().name(),
                norm.operationCode() == null ? null : norm.operationCode().name(),
                planExpression.hash(),
                planExpression.jsonSnapshot(),
                configSnapshotJson,
                configSnapshotHash,
                window.from(),
                window.to(),
                sliceStrategyCode,
                buildSliceParams(sliceStrategy)
        );
    }

    /**
     * 触发对应的切片规划器生成切片草稿，并转为聚合。
     */
    /**
     * 生成切片：调用策略 → SlicePlan 列表 → canonical 表达式快照 → PlanSlice 聚合。
     */
    private SliceGenerationResult createSlices(PlanTriggerNorm norm,
                                               PlannerWindow window,
                                               PlanExpressionDescriptor planExpression,
                                               ProvenanceConfigSnapshot configSnapshot,
                                               SliceStrategy sliceStrategy) {
        SlicePlanner planner = slicePlannerRegistry.get(sliceStrategy);
        if (planner == null) {
            return new SliceGenerationResult(List.of(), List.of());
        }

        List<SlicePlan> drafts = planner.slice(new SlicePlanningContext(norm, window, planExpression, configSnapshot));
        if (drafts == null || drafts.isEmpty()) {
            return new SliceGenerationResult(List.of(), List.of());
        }

        List<PlanSliceAggregate> slices = new ArrayList<>(drafts.size());
        for (SlicePlan draft : drafts) {
            ExprCanonicalSnapshot sliceSnapshot = ExprCanonicalizer.canonicalize(draft.sliceExpr());
            slices.add(PlanSliceAggregate.create(
                    null,
                    norm.provenanceCode().getCode(),
                    draft.sequence(),
                    draft.sliceSignatureSeed(),
                    draft.sliceSpecJson(),
                    sliceSnapshot.hash(),
                    sliceSnapshot.canonicalJson()
            ));
        }
        return new SliceGenerationResult(slices, drafts);
    }

    /**
     * 为每个切片派生任务。此处暂以切片序号作为占位 sliceId，后续持久化时再绑定真实 ID。
     */
    /**
     * 生成任务：一切片一任务。
     * <p>任务幂等键 material = provenance | operation | sliceSignatureHash → sha256 → Base64Url。</p>
     */
    private List<TaskAggregate> createTasks(PlanTriggerNorm norm,
                                            PlannerWindow window,
                                            SliceGenerationResult sliceResult) {
        List<PlanSliceAggregate> slices = sliceResult.aggregates();
        List<SlicePlan> drafts = sliceResult.drafts();
        List<TaskAggregate> tasks = new ArrayList<>(slices.size());
        for (int i = 0; i < slices.size(); i++) {
            PlanSliceAggregate slice = slices.get(i);
            SlicePlan draft = drafts.get(i);
            String idemKey = computeSignature(norm, slice.getSliceSignatureHash());
            Priority effectivePriority = norm.priority() == null ? Priority.NORMAL : norm.priority();
            int priorityVal = effectivePriority.queueValue();
            Instant scheduledAt = determineScheduledAt(draft, window);

            tasks.add(TaskAggregate.create(
                    norm.scheduleInstanceId(),
                    null,
                    (long) slice.getSequence(),
                    norm.provenanceCode().getCode(),
                    norm.operationCode().name(),
                    buildTaskParamsJson(slice.getSequence()),
                    idemKey,
                    slice.getExprHash(),
                    priorityVal,
                    scheduledAt
            ));
        }
        return tasks;
    }

    /**
     * 计算任务调度时间：优先切片 windowFrom，其次总窗口 from，最后即时 now。
     */
    private Instant determineScheduledAt(SlicePlan draft, PlannerWindow window) {
        if (draft.windowFrom() != null) {
            return draft.windowFrom();
        }
        if (window != null && window.from() != null) {
            return window.from();
        }
        // 无窗口时回退当前时间，保持调度及时性
        return Instant.now();
    }

    /**
     * 计算 PlanKey：provenance:operation[:endpoint][:from-toMillis]。
     */
    private String buildPlanKey(PlanTriggerNorm norm, PlannerWindow window) {
        StringBuilder builder = new StringBuilder();
        builder.append(norm.provenanceCode().getCode()).append(":").append(norm.operationCode().name());
        if (norm.endpoint() != null) {
            builder.append(":").append(norm.endpoint().name().toLowerCase());
        }
        if (window.from() != null && window.to() != null) {
            builder.append(":").append(window.from().toEpochMilli()).append("-").append(window.to().toEpochMilli());
        }
        return builder.toString();
    }

    /**
     * 选择切片策略（扩展点）：目前 UPDATE → SINGLE，其他 → TIME。
     */
    private SliceStrategy determineSliceStrategy(PlanTriggerNorm norm) {
        if (norm.isUpdate()) {
            return SliceStrategy.SINGLE;
        }
        return SliceStrategy.TIME;
    }

    /**
     * 生成策略参数 JSON：保持稳定序列化（例如{"strategy":"time"}）。
     */
    private String buildSliceParams(SliceStrategy sliceStrategy) {
        JsonNormalizer.Result normalized = DEFAULT_NORMALIZER.normalize(Map.of("strategy", sliceStrategy.getCode()));
        return normalized.getCanonicalJson();
    }

    /**
     * 构造任务参数 JSON：仅包含 sliceNo；使用定制 normalizer 防止类型歧义。
     */
    private String buildTaskParamsJson(int sliceSequence) {
        JsonNormalizer.Result normalized = TASK_PARAM_NORMALIZER.normalize(Map.of("sliceNo", sliceSequence));
        return normalized.getCanonicalJson();
    }

    /**
     * 配置快照 canonical 化：返回 null 表示无配置（允许 UPDATE 等模式）。
     */
    private JsonNormalizer.Result normalizeConfigSnapshot(ProvenanceConfigSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return DEFAULT_NORMALIZER.normalize(snapshot);
    }

    /**
     * 生成任务幂等键：sha256(provenance|operation|sliceHash) → Base64Url 无填充。
     */
    private String computeSignature(PlanTriggerNorm norm, String payload) {
        String material = CharSequenceUtil.join("|",
                norm.provenanceCode().getCode(),
                norm.operationCode().name(),
                payload == null ? CharSequenceUtil.EMPTY : payload);
        byte[] digest = HashUtils.sha256(material);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
    }

    private record SliceGenerationResult(List<PlanSliceAggregate> aggregates, List<SlicePlan> drafts) {
    }
}
