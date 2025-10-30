package com.patra.ingest.app.usecase.plan.assembler;

import cn.hutool.core.text.CharSequenceUtil;
import com.patra.common.enums.Priority;
import com.patra.common.json.JsonNormalizer;
import com.patra.common.json.JsonNormalizerConfig;
import com.patra.common.json.JsonNormalizerResult;
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
          JsonNormalizerConfig.builder()
              .coerceBoolean(JsonNormalizerConfig.CoerceBoolean.NONE)
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

    SliceStrategy sliceStrategy = determineSliceStrategy(norm, configSnapshot);
    log.debug(
        "Determined slice strategy for provenance [{}] operation [{}]: {}",
        norm.provenanceCode(),
        norm.operationCode(),
        sliceStrategy.getCode());

    JsonNormalizerResult configCanonical = normalizeConfigSnapshot(configSnapshot);

    PlanAggregate plan =
        createPlanAggregate(norm, window, planExpression, sliceStrategy, configCanonical);
    plan.startSlicing();
    log.debug(
        "Created plan aggregate for provenance [{}] operation [{}]: planKey={}, strategy={}",
        norm.provenanceCode(),
        norm.operationCode(),
        plan.getPlanKey(),
        sliceStrategy.getCode());

    SliceGenerationResult sliceResult =
        createSlices(norm, window, planExpression, configSnapshot, sliceStrategy);
    List<PlanSliceAggregate> slices = sliceResult.aggregates();
    List<TaskAggregate> tasks =
        slices.isEmpty() ? List.of() : createTasks(norm, window, sliceResult);

    log.debug(
        "Generated {} slices and {} tasks for provenance [{}] operation [{}]",
        slices.size(),
        tasks.size(),
        norm.provenanceCode(),
        norm.operationCode());

    if (slices.isEmpty() || tasks.isEmpty()) {
      // Note: After refactoring, Plan remains in SLICING status when assembly fails.
      // No explicit failure status exists; the Plan will not progress to READY.
      log.warn(
          "Plan assembly failed for provenance [{}] operation [{}]: no slices or tasks generated. "
              + "Plan remains in SLICING status.",
          norm.provenanceCode(),
          norm.operationCode());
      return new PlanAssemblyResult(plan, slices, tasks, PlanAssemblyResult.AssemblyStatus.FAILED);
    }

    plan.markReady();
    return new PlanAssemblyResult(plan, slices, tasks, PlanAssemblyResult.AssemblyStatus.READY);
  }

  /**
   * Creates plan aggregate root.
   *
   * <p>Contains: expression hash, expression JSON snapshot, config canonical JSON + hash, window,
   * slice strategy, slice params JSON.
   */
  private PlanAggregate createPlanAggregate(
      PlanTriggerNorm norm,
      PlannerWindow window,
      PlanExpressionDescriptor planExpression,
      SliceStrategy sliceStrategy,
      JsonNormalizerResult configSnapshot) {
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

  /**
   * Generates slices by invoking strategy and converting to aggregates.
   *
   * <p>Flow: invoke planner strategy → SlicePlan list → canonical expr snapshot → PlanSlice
   * aggregates
   *
   * @param norm plan trigger norm
   * @param window planner window
   * @param planExpression plan expression descriptor
   * @param configSnapshot provenance config snapshot
   * @param sliceStrategy slice strategy
   * @return slice generation result
   */
  private SliceGenerationResult createSlices(
      PlanTriggerNorm norm,
      PlannerWindow window,
      PlanExpressionDescriptor planExpression,
      ProvenanceConfigSnapshot configSnapshot,
      SliceStrategy sliceStrategy) {
    SlicePlanner planner = slicePlannerRegistry.get(sliceStrategy);
    if (planner == null) {
      log.debug(
          "No slice planner found for strategy [{}], provenance [{}] operation [{}]",
          sliceStrategy.getCode(),
          norm.provenanceCode(),
          norm.operationCode());
      return new SliceGenerationResult(List.of(), List.of());
    }

    log.debug(
        "Invoking slice planner [{}] for provenance [{}] operation [{}]",
        sliceStrategy.getCode(),
        norm.provenanceCode(),
        norm.operationCode());

    List<SlicePlan> drafts =
        generateSlicePlans(planner, norm, window, planExpression, configSnapshot);
    if (drafts == null || drafts.isEmpty()) {
      log.debug(
          "Slice planner [{}] returned no slices for provenance [{}] operation [{}]",
          sliceStrategy.getCode(),
          norm.provenanceCode(),
          norm.operationCode());
      return new SliceGenerationResult(List.of(), List.of());
    }

    log.debug(
        "Slice planner [{}] generated {} slice drafts for provenance [{}] operation [{}]",
        sliceStrategy.getCode(),
        drafts.size(),
        norm.provenanceCode(),
        norm.operationCode());

    List<PlanSliceAggregate> slices = convertToSliceAggregates(norm, drafts);
    return new SliceGenerationResult(slices, drafts);
  }

  /**
   * Generates slice plans using planner strategy.
   *
   * @param planner slice planner
   * @param norm plan trigger norm
   * @param window planner window
   * @param planExpression plan expression descriptor
   * @param configSnapshot provenance config snapshot
   * @return list of slice plans
   */
  private List<SlicePlan> generateSlicePlans(
      SlicePlanner planner,
      PlanTriggerNorm norm,
      PlannerWindow window,
      PlanExpressionDescriptor planExpression,
      ProvenanceConfigSnapshot configSnapshot) {
    return planner.slice(new SlicePlanningContext(norm, window, planExpression, configSnapshot));
  }

  /**
   * Converts slice plans to slice aggregates.
   *
   * @param norm plan trigger norm
   * @param drafts slice plan drafts
   * @return list of slice aggregates
   */
  private List<PlanSliceAggregate> convertToSliceAggregates(
      PlanTriggerNorm norm, List<SlicePlan> drafts) {
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
    return slices;
  }

  /**
   * Generates tasks: one task per slice.
   *
   * <p>Task idempotent key material = provenance | operation | sliceSignatureHash → sha256 →
   * Base64Url.
   */
  private List<TaskAggregate> createTasks(
      PlanTriggerNorm norm, PlannerWindow window, SliceGenerationResult sliceResult) {
    List<PlanSliceAggregate> slices = sliceResult.aggregates();
    List<SlicePlan> drafts = sliceResult.drafts();
    List<TaskAggregate> tasks = new ArrayList<>(slices.size());

    Priority effectivePriority = norm.priority() == null ? Priority.NORMAL : norm.priority();
    log.debug(
        "Creating {} tasks from slices for provenance [{}] operation [{}], priority={}",
        slices.size(),
        norm.provenanceCode(),
        norm.operationCode(),
        effectivePriority);

    for (int i = 0; i < slices.size(); i++) {
      PlanSliceAggregate slice = slices.get(i);
      SlicePlan draft = drafts.get(i);
      String idemKey = computeSignature(norm, slice.getSliceSignatureHash());
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
      log.warn("Failed to parse windowFrom from windowSpecJson: {}", e.getMessage());
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

  /**
   * Selects slice strategy based on operation type and data source configuration.
   *
   * <p>Strategy selection logic:
   *
   * <ul>
   *   <li>UPDATE operations → SINGLE (no partitioning)
   *   <li>DATE-only data sources (e.g., PubMed) → DATE (day-level granularity)
   *   <li>Other sources → DATE (default, safer and more compatible)
   * </ul>
   *
   * <p>Date-only detection: checks {@code offsetDateFormat} for date-only patterns (yyyyMMdd,
   * yyyy-MM-dd) vs timestamp patterns (ISO_INSTANT, epochMillis).
   *
   * @param norm plan trigger norm containing operation type
   * @param configSnapshot provenance configuration snapshot (nullable)
   * @return selected slice strategy
   */
  private SliceStrategy determineSliceStrategy(
      PlanTriggerNorm norm, ProvenanceConfigSnapshot configSnapshot) {
    if (norm.isUpdate()) {
      return SliceStrategy.SINGLE;
    }

    // Check if the data source only supports date-level queries (no time component)
    if (configSnapshot != null && configSnapshot.windowOffset() != null) {
      String dateFormat = configSnapshot.windowOffset().offsetDateFormat();
      if (supportsDateOnly(dateFormat)) {
        log.debug(
            "Selected DATE strategy for provenance={}, offsetDateFormat={}",
            norm.provenanceCode(),
            dateFormat);
        return SliceStrategy.DATE;
      }
    }

    // Default to DATE strategy (safer and more compatible with most data sources)
    log.debug("Selected DATE strategy (default) for provenance={}", norm.provenanceCode());
    return SliceStrategy.DATE;
  }

  /**
   * Determines if the data source only supports date-level queries (no time component).
   *
   * <p>Detection heuristic:
   *
   * <ul>
   *   <li>If offsetDateFormat is blank/null → default to DATE-only (safer)
   *   <li>If contains timestamp indicators (INSTANT, MILLIS, HH, SS) → supports TIME
   *   <li>If contains only date patterns (YYYY, MM, DD) → DATE-only
   * </ul>
   *
   * @param offsetDateFormat date format string from configuration
   * @return true if only date-level queries are supported, false if time precision is supported
   */
  private boolean supportsDateOnly(String offsetDateFormat) {
    if (offsetDateFormat == null || offsetDateFormat.isBlank()) {
      return true; // Default to DATE-only for safety
    }
    String normalized = offsetDateFormat.trim().toUpperCase();
    // Check for timestamp indicators (time precision supported)
    if (normalized.contains("INSTANT")
        || normalized.contains("MILLIS")
        || normalized.contains("HH")
        || normalized.contains("SS")
        || normalized.contains("TIMESTAMP")) {
      return false;
    }
    // If it contains date patterns and no time patterns, it's date-only
    return normalized.matches(".*(?:YYYY|YY|MM|DD).*");
  }

  /** Builds slice params JSON with stable serialization (e.g., {"strategy":"time"}). */
  private String buildSliceParams(SliceStrategy sliceStrategy) {
    // TODO does not need to keep the strategy field, there is a strategy field in the plan, and the
    // parameters only need to contain specific configurations,
    //  such as step, etc., values passed by the user or configured in the registry database.
    JsonNormalizerResult normalized =
        DEFAULT_NORMALIZER.normalize(Map.of("strategy", sliceStrategy.getCode()));
    return normalized.getCanonicalJson();
  }

  /** Builds task params JSON: only sliceNo; use custom normalizer to avoid type ambiguity. */
  private String buildTaskParamsJson(int sliceSequence) {
    JsonNormalizerResult normalized =
        TASK_PARAM_NORMALIZER.normalize(Map.of("sliceNo", sliceSequence));
    return normalized.getCanonicalJson();
  }

  /** Canonicalizes config snapshot; returns null when no config (allowed in UPDATE mode). */
  private JsonNormalizerResult normalizeConfigSnapshot(ProvenanceConfigSnapshot snapshot) {
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
