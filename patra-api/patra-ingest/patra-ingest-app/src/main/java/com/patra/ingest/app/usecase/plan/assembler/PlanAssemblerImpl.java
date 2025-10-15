package com.patra.ingest.app.usecase.plan.assembler;

import cn.hutool.core.text.CharSequenceUtil;
import com.patra.common.enums.Priority;
import com.patra.common.json.JsonNormalizer;
import com.patra.common.util.HashUtils;
import com.patra.expr.canonical.ExprCanonicalSnapshot;
import com.patra.expr.canonical.ExprCanonicalizer;
import com.patra.ingest.app.usecase.plan.dto.PlanAssemblyResult;
import com.patra.ingest.app.usecase.plan.expression.PlanExpressionDescriptor;
import com.patra.ingest.app.usecase.plan.slicer.SlicePlanner;
import com.patra.ingest.app.usecase.plan.slicer.SlicePlannerRegistry;
import com.patra.ingest.app.usecase.plan.slicer.model.SlicePlan;
import com.patra.ingest.app.usecase.plan.slicer.model.SlicePlanningContext;
import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import com.patra.ingest.domain.model.aggregate.PlanSliceAggregate;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.enums.SliceStrategy;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import com.patra.ingest.domain.model.vo.PlanTriggerNorm;
import com.patra.ingest.domain.model.vo.PlannerWindow;
import com.patra.ingest.domain.model.vo.WindowSpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Plan assembler implementation.
 *
 * <p>Transforms {@link PlanAssemblyRequest} into {@link PlanAssemblyResult} aggregates:
 *
 * <ol>
 *   <li>Decide slice strategy (UPDATE → SINGLE; otherwise TIME)
 *   <li>Canonicalize configuration (canonical JSON + hash material)
 *   <li>Create Plan aggregate (expr snapshot / config signature / window / strategy code)
 *   <li>Invoke slicing to produce SlicePlan drafts and convert to PlanSlice aggregates (canonical
 *       expr + hash)
 *   <li>Derive Task aggregates per slice (idempotent key, priority, scheduled time)
 *   <li>Mark Plan status as FAILED or READY based on presence of slices/tasks
 * </ol>
 *
 * <h4>Idempotency</h4>
 *
 * <p>PlanKey is generated in createPlanAggregate from (provenance, operation, endpoint?,
 * windowFrom-windowTo?). Task idempotent key uses (provenance | operation | sliceSignatureHash).
 * This class does not duplicate-check; upper layers enforce via persistence and unique indexes.
 *
 * <h4>Failure conditions</h4>
 *
 * <ul>
 *   <li>No slice strategy implementation (registry returns null) → FAILED (empty collections)
 *   <li>Slice strategy returns empty list → FAILED
 *   <li>Slices exist but derived tasks are empty (should not happen) → FAILED
 * </ul>
 *
 * <h4>Complexity</h4>
 *
 * <p>O(n), n = number of slices; canonicalization and hashing are linear.
 *
 * <h4>Thread safety</h4>
 *
 * <p>No shared mutable state; registry lookups are read-only and singleton-safe.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class PlanAssemblerImpl implements PlanAssembler {

  /** Default JSON normalizer for stable serialization of config and strategy params. */
  private static final JsonNormalizer DEFAULT_NORMALIZER = JsonNormalizer.usingDefault();

  /**
   * Normalizer for task params: disable boolean/time coercion to avoid 0/1 becoming booleans or
   * timestamps.
   */
  private static final JsonNormalizer TASK_PARAM_NORMALIZER =
      JsonNormalizer.withConfig(
          JsonNormalizer.Config.builder()
              .coerceBoolean(JsonNormalizer.Config.CoerceBoolean.NONE)
              .coerceTime(false)
              .build());

  private final SlicePlannerRegistry slicePlannerRegistry;

  public PlanAssemblerImpl(SlicePlannerRegistry slicePlannerRegistry) {
    this.slicePlannerRegistry = slicePlannerRegistry;
  }

  /**
   * Assembly entrypoint.
   *
   * <p>Side-effect free (apart from Instant.now and canonicalization); no persistence here.
   */
  @Override
  public PlanAssemblyResult assemble(PlanAssemblyRequest request) {
    PlanTriggerNorm norm = request.triggerNorm();
    PlannerWindow window = request.window();
    PlanExpressionDescriptor planExpression = request.planExpression();
    ProvenanceConfigSnapshot configSnapshot = request.configSnapshot();

    SliceStrategy sliceStrategy = determineSliceStrategy(norm);
    JsonNormalizer.Result configCanonical = normalizeConfigSnapshot(configSnapshot);

    PlanAggregate plan =
        createPlanAggregate(norm, window, planExpression, sliceStrategy, configCanonical);
    plan.startSlicing();

    SliceGenerationResult sliceResult =
        createSlices(norm, window, planExpression, configSnapshot, sliceStrategy);
    List<PlanSliceAggregate> slices = sliceResult.aggregates();
    List<TaskAggregate> tasks =
        slices.isEmpty() ? List.of() : createTasks(norm, window, sliceResult);

    if (slices.isEmpty() || tasks.isEmpty()) {
      plan.markFailed();
      return new PlanAssemblyResult(plan, slices, tasks, PlanAssemblyResult.AssemblyStatus.FAILED);
    }

    plan.markReady();
    return new PlanAssemblyResult(plan, slices, tasks, PlanAssemblyResult.AssemblyStatus.READY);
  }

  /**
   * 构建 Plan 聚合根。
   *
   * <p>包含：表达式哈希、表达式 JSON 快照、配置 canonical JSON + hash、窗口、切片策略、切片参数 JSON。
   */
  private PlanAggregate createPlanAggregate(
      PlanTriggerNorm norm,
      PlannerWindow window,
      PlanExpressionDescriptor planExpression,
      SliceStrategy sliceStrategy,
      JsonNormalizer.Result configSnapshot) {
    String planKey = buildPlanKey(norm, window);
    String configSnapshotJson = configSnapshot == null ? null : configSnapshot.getCanonicalJson();
    String configSnapshotHash =
        configSnapshot == null ? null : HashUtils.sha256Hex(configSnapshot.getHashMaterial());

    String sliceStrategyCode = sliceStrategy.getCode();
    WindowSpec windowSpec = WindowSpec.ofTime(window.from(), window.to());
    return PlanAggregate.create(
        norm.scheduleInstanceId(),
        planKey,
        norm.provenanceCode().getCode(),
        norm.operationCode() == null ? null : norm.operationCode().getCode(),
        planExpression.hash(),
        planExpression.jsonSnapshot(),
        configSnapshotJson,
        configSnapshotHash,
        windowSpec,
        sliceStrategyCode,
        buildSliceParams(sliceStrategy));
  }

  /** 生成切片：调用策略 → SlicePlan 列表 → canonical 表达式快照 → PlanSlice 聚合。 */
  private SliceGenerationResult createSlices(
      PlanTriggerNorm norm,
      PlannerWindow window,
      PlanExpressionDescriptor planExpression,
      ProvenanceConfigSnapshot configSnapshot,
      SliceStrategy sliceStrategy) {
    SlicePlanner planner = slicePlannerRegistry.get(sliceStrategy);
    if (planner == null) {
      return new SliceGenerationResult(List.of(), List.of());
    }

    List<SlicePlan> drafts =
        planner.slice(new SlicePlanningContext(norm, window, planExpression, configSnapshot));
    if (drafts == null || drafts.isEmpty()) {
      return new SliceGenerationResult(List.of(), List.of());
    }

    List<PlanSliceAggregate> slices = new ArrayList<>(drafts.size());
    for (SlicePlan draft : drafts) {
      ExprCanonicalSnapshot sliceSnapshot = ExprCanonicalizer.canonicalize(draft.sliceExpr());
      slices.add(
          PlanSliceAggregate.create(
              null,
              norm.provenanceCode().getCode(),
              draft.sliceNo(),
              draft.sliceSignatureSeed(),
              draft.windowSpecJson(),
              sliceSnapshot.hash(),
              sliceSnapshot.canonicalJson()));
    }
    return new SliceGenerationResult(slices, drafts);
  }

  /** 为每个切片派生任务。此处暂以切片序号作为占位 sliceId，后续持久化时再绑定真实 ID。 */
  /**
   * 生成任务：一切片一任务。
   *
   * <p>任务幂等键 material = provenance | operation | sliceSignatureHash → sha256 → Base64Url。
   */
  private List<TaskAggregate> createTasks(
      PlanTriggerNorm norm, PlannerWindow window, SliceGenerationResult sliceResult) {
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

      tasks.add(
          TaskAggregate.create(
              norm.scheduleInstanceId(),
              null,
              (long) slice.getSliceNo(),
              norm.provenanceCode().getCode(),
              norm.operationCode().getCode(),
              buildTaskParamsJson(slice.getSliceNo()),
              idemKey,
              slice.getExprHash(),
              priorityVal,
              scheduledAt));
    }
    return tasks;
  }

  /**
   * Compute task scheduled time: prefer slice windowFrom (parsed), else overall window.from, else
   * now.
   */
  private Instant determineScheduledAt(SlicePlan draft, PlannerWindow window) {
    // Try to extract windowFrom from windowSpecJson
    Instant windowFrom = extractWindowFrom(draft.windowSpecJson());
    if (windowFrom != null) {
      return windowFrom;
    }
    if (window != null && window.from() != null) {
      return window.from();
    }
    // Fallback to now when no window is available
    return Instant.now();
  }

  /** Extracts window.from from windowSpecJson when present. */
  private Instant extractWindowFrom(String windowSpecJson) {
    try {
      com.fasterxml.jackson.databind.ObjectMapper mapper =
          new com.fasterxml.jackson.databind.ObjectMapper();
      com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(windowSpecJson);
      com.fasterxml.jackson.databind.JsonNode window = root.get("window");
      if (window != null && window.has("from")) {
        String fromStr = window.get("from").asText();
        return Instant.parse(fromStr);
      }
    } catch (Exception e) {
      log.warn("[INGEST][APP] Failed to parse windowFrom from windowSpecJson: {}", e.getMessage());
    }
    return null;
  }

  /** Builds PlanKey: provenance:operation[:endpoint][:from-toMillis]. */
  private String buildPlanKey(PlanTriggerNorm norm, PlannerWindow window) {
    StringBuilder builder = new StringBuilder();
    builder
        .append(norm.provenanceCode().getCode())
        .append(":")
        .append(norm.operationCode().getCode());
    if (window.from() != null && window.to() != null) {
      builder
          .append(":")
          .append(window.from().toEpochMilli())
          .append("-")
          .append(window.to().toEpochMilli());
    }
    return builder.toString();
  }

  /** Selects slice strategy (extension point): UPDATE → SINGLE, otherwise TIME. */
  private SliceStrategy determineSliceStrategy(PlanTriggerNorm norm) {
    if (norm.isUpdate()) {
      return SliceStrategy.SINGLE;
    }
    return SliceStrategy.TIME;
  }

  /** Builds slice params JSON with stable serialization (e.g., {"strategy":"time"}). */
  private String buildSliceParams(SliceStrategy sliceStrategy) {
    // TODO does not need to keep the strategy field, there is a strategy field in the plan, and the
    // parameters only need to contain specific configurations,
    //  such as step, etc., values passed by the user or configured in the registry database.
    JsonNormalizer.Result normalized =
        DEFAULT_NORMALIZER.normalize(Map.of("strategy", sliceStrategy.getCode()));
    return normalized.getCanonicalJson();
  }

  /** Builds task params JSON: only sliceNo; use custom normalizer to avoid type ambiguity. */
  private String buildTaskParamsJson(int sliceSequence) {
    JsonNormalizer.Result normalized =
        TASK_PARAM_NORMALIZER.normalize(Map.of("sliceNo", sliceSequence));
    return normalized.getCanonicalJson();
  }

  /** Canonicalizes config snapshot; returns null when no config (allowed in UPDATE mode). */
  private JsonNormalizer.Result normalizeConfigSnapshot(ProvenanceConfigSnapshot snapshot) {
    if (snapshot == null) {
      return null;
    }
    return DEFAULT_NORMALIZER.normalize(snapshot);
  }

  /**
   * Builds task idempotent key: sha256(provenance|operation|sliceHash) → Base64Url without padding.
   */
  private String computeSignature(PlanTriggerNorm norm, String payload) {
    String material =
        CharSequenceUtil.join(
            "|",
            norm.provenanceCode().getCode(),
            norm.operationCode().getCode(),
            payload == null ? CharSequenceUtil.EMPTY : payload);
    byte[] digest = HashUtils.sha256(material);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
  }

  private record SliceGenerationResult(
      List<PlanSliceAggregate> aggregates, List<SlicePlan> drafts) {}
}
