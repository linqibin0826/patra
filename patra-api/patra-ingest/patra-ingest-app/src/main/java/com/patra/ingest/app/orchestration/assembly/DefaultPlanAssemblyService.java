package com.patra.ingest.app.orchestration.assembly;

import cn.hutool.core.text.CharSequenceUtil;
import com.patra.common.enums.Priority;
import com.patra.common.json.JsonNormalizer;
import com.patra.common.util.HashUtils;
import com.patra.expr.canonical.ExprCanonicalSnapshot;
import com.patra.expr.canonical.ExprCanonicalizer;
import com.patra.ingest.app.orchestration.expression.PlanExpressionDescriptor;
import com.patra.ingest.app.orchestration.slice.SlicePlanner;
import com.patra.ingest.app.orchestration.slice.SlicePlannerRegistry;
import com.patra.ingest.app.orchestration.slice.model.SlicePlan;
import com.patra.ingest.app.orchestration.slice.model.SlicePlanningContext;
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
 * 默认的计划装配服务：根据触发请求组装计划、切片与任务聚合。
 * <p>负责驱动切片策略与任务生成，是计划落库前的核心装配器。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Component
public class DefaultPlanAssemblyService implements PlanAssemblyService {

    private static final String SLICE_STRATEGY_SINGLE = "SINGLE";
    private static final String SLICE_STRATEGY_TIME = "TIME";
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

    public DefaultPlanAssemblyService(SlicePlannerRegistry slicePlannerRegistry) {
        this.slicePlannerRegistry = slicePlannerRegistry;
    }

    /**
     * 组装计划、切片与任务，形成可持久化的聚合集合。
     * <p>流程：规范化配置 → 构建计划聚合 → 调用切片策略 → 派生任务。</p>
     *
     * @param request 装配请求，包含触发规范、窗口、配置与表达式
     * @return 组合后的聚合集合
     */
    @Override
    public PlanAssembly assemble(PlanAssemblyRequest request) {
        PlanTriggerNorm norm = request.triggerNorm();
        PlannerWindow window = request.window();
        PlanExpressionDescriptor planExpression = request.planExpression();
        ProvenanceConfigSnapshot configSnapshot = request.configSnapshot();

        String sliceStrategy = determineSliceStrategy(norm);
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
     * 根据归一化请求构造计划聚合。
     */
    /**
     * 构建计划聚合根，封装来源配置与表达式快照。
     *
     * @param norm 触发规范
     * @param window 计划窗口
     * @param planExpression 计划级表达式描述
     * @param sliceStrategy 切片策略编码
     * @param configSnapshot 配置快照（规范化）
     * @return 新的计划聚合
     */
    private PlanAggregate createPlanAggregate(PlanTriggerNorm norm,
                                              PlannerWindow window,
                                              PlanExpressionDescriptor planExpression,
                                              String sliceStrategy,
                                              JsonNormalizer.Result configSnapshot) {
        String planKey = buildPlanKey(norm, window);
        String configSnapshotJson = configSnapshot == null ? null : configSnapshot.getCanonicalJson();
        String configSnapshotHash = configSnapshot == null ? null : HashUtils.sha256Hex(configSnapshot.getHashMaterial());

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
                sliceStrategy,
                buildSliceParams(sliceStrategy)
        );
    }

    /**
     * 触发对应的切片规划器生成切片草稿，并转为聚合。
     */
    /**
     * 按策略生成切片草稿并转换为聚合。
     *
     * @param norm 触发规范
     * @param window 计划窗口
     * @param planExpression 计划表达式
     * @param configSnapshot 来源配置
     * @param sliceStrategy 切片策略编码
     * @return 切片聚合及草稿集合
     */
    private SliceGenerationResult createSlices(PlanTriggerNorm norm,
                                                PlannerWindow window,
                                                PlanExpressionDescriptor planExpression,
                                                ProvenanceConfigSnapshot configSnapshot,
                                                String sliceStrategy) {
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
     * 根据切片信息派生任务聚合。
     *
     * @param norm 触发规范
     * @param window 计划窗口
     * @param sliceResult 切片生成结果
     * @return 任务聚合列表
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
     * 决定任务的计划执行时间。
     *
     * @param draft 切片草稿
     * @param window 计划窗口
     * @return 任务计划时间
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
     * 依据数据源、操作与时间窗口生成计划唯一键。
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
     * 判断当前触发是否仅需单片执行。
     */
    private String determineSliceStrategy(PlanTriggerNorm norm) {
        if (norm.isUpdate()) {
            return SLICE_STRATEGY_SINGLE;
        }
        return SLICE_STRATEGY_TIME;
    }

    /**
     * 规范化切片策略参数，保持 canonical JSON。
     */
    private String buildSliceParams(String sliceStrategy) {
        JsonNormalizer.Result normalized = DEFAULT_NORMALIZER.normalize(Map.of("strategy", sliceStrategy));
        return normalized.getCanonicalJson();
    }

    /**
     * 构造任务参数，确保 sliceNo 序号以数值形式存储，不被自动推断为布尔或时间。
     */
    private String buildTaskParamsJson(int sliceSequence) {
        JsonNormalizer.Result normalized = TASK_PARAM_NORMALIZER.normalize(Map.of("sliceNo", sliceSequence));
        return normalized.getCanonicalJson();
    }

    /**
     * 规范化配置快照，以便持久化 JSON 与哈希值。
     */
    private JsonNormalizer.Result normalizeConfigSnapshot(ProvenanceConfigSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        return DEFAULT_NORMALIZER.normalize(snapshot);
    }

    /**
     * 基于来源、操作与切片签名生成任务幂等键。
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
