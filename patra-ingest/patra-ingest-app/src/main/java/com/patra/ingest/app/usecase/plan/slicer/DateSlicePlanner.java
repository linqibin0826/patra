package com.patra.ingest.app.usecase.plan.slicer;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.patra.common.json.JsonNormalizer;
import com.patra.common.util.HashUtils;
import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import com.patra.ingest.app.usecase.plan.expression.PlanExpressionDescriptor;
import com.patra.ingest.app.usecase.plan.slicer.model.SlicePlan;
import com.patra.ingest.app.usecase.plan.slicer.model.SlicePlanningContext;
import com.patra.ingest.domain.model.enums.SliceStrategy;
import com.patra.ingest.domain.model.snapshot.ProvenanceConfigSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Date-only based slicing strategy (Application Layer · Policy).
 *
 * <p>Splits the upstream planning window [from, to) into several half-open date-only sub-windows
 * using a fixed step; each sub-window is paired with the business expression to form an independent
 * Slice.
 *
 * <p>This strategy is designed for data sources that only support date-level queries without time
 * precision (e.g., PubMed which only accepts YYYY-MM-DD format).
 *
 * <p>Design notes:
 *
 * <ul>
 *   <li>Step configuration: prefer the normalized step from the trigger context (ISO-8601
 *       Duration); fallback to the default 1 day when invalid.
 *   <li>Time field resolution: offsetFieldKey (DATE mode) > windowDateFieldKey.
 *   <li>Date conversion: Instant timestamps are converted to LocalDate using UTC zone for
 *       consistent date extraction.
 *   <li>Range semantics: Uses {@link Exprs#rangeDate} with half-open interval [from, to) to match
 *       PubMed query behavior.
 *   <li>Idempotence: build a canonical JSON and take sha256; repeated planning yields identical
 *       signatures.
 *   <li>Boundary: the last slice aligns to the window end if it is shorter than the step.
 *   <li>Complexity: O(n), n = ceil((to - from) / step).
 * </ul>
 *
 * <p>Return empty list when: window missing; from >= to; or the time field cannot be resolved.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class DateSlicePlanner implements SlicePlanner {

  /** Default slice step (1 day). */
  private static final Duration DEFAULT_STEP = Duration.ofDays(1);

  @Override
  public SliceStrategy code() {
    return SliceStrategy.DATE;
  }

  @Override
  public List<SlicePlan> slice(SlicePlanningContext context) {
    // Initialize the result to keep ordering stable even on early returns
    List<SlicePlan> result = new ArrayList<>();
    if (context.window() == null
        || context.window().from() == null
        || context.window().to() == null) {
      log.warn(
          "[INGEST][APP] Skip date slicing because planning window is missing: norm={}, window={}",
          context.norm(),
          context.window());
      return result;
    }

    // Resolve time field: prefer offsetFieldKey (DATE mode), otherwise fallback to
    // windowDateFieldKey
    String timeField = resolveTimeField(context.configSnapshot());
    if (timeField == null) {
      log.error(
          "[INGEST][APP] Cannot resolve time field from provenance snapshot, provenanceCode={}, operation={}",
          context.norm().provenanceCode(),
          context.norm().operationCode());
      return result;
    }

    Instant from = context.window().from();
    Instant to = context.window().to();
    if (!from.isBefore(to)) {
      log.warn(
          "[INGEST][APP] Skip date slicing because window is not forward, from={} to={}", from, to);
      return result;
    }

    // Use custom step from norm when present; otherwise fallback to the default
    Duration step = DEFAULT_STEP;
    if (StrUtil.isNotBlank(context.norm().step())) {
      try {
        step = Duration.parse(context.norm().step().trim());
      } catch (Exception e) {
        log.warn(
            "[INGEST][APP] Invalid step format, fallback to default, stepString={}",
            context.norm().step(),
            e);
      }
    }

    // Convert Instant to LocalDate (UTC zone for consistent date extraction)
    LocalDate cursor = from.atZone(ZoneOffset.UTC).toLocalDate();
    LocalDate endDate = to.atZone(ZoneOffset.UTC).toLocalDate();
    int index = 1;
    PlanExpressionDescriptor planExpr = context.planExpression();

    while (cursor.isBefore(endDate)) {
      // Compute the current slice upper bound; ensure the last slice aligns to the window end
      LocalDate upper = cursor.plusDays(step.toDays());
      if (upper.isAfter(endDate)) {
        upper = endDate;
      }

      // Prevent infinite loop: ensure cursor can advance (upper must be after cursor)
      if (!cursor.isBefore(upper)) {
        log.warn(
            "[INGEST][APP] Stopping date slicing: cursor cannot advance, cursor={}, upper={}, endDate={}",
            cursor,
            upper,
            endDate);
        break;
      }

      // Build the slice spec and generate a stable signature
      JsonNormalizer.Result specNormalized =
          buildSpec(
              context,
              cursor.atStartOfDay(ZoneOffset.UTC).toInstant(),
              upper.atStartOfDay(ZoneOffset.UTC).toInstant());
      String specJson = specNormalized.getCanonicalJson();
      String signatureHash = HashUtils.sha256Hex(specNormalized.getHashMaterial());

      // Combine the plan expression with the date-window constraint
      // Use half-open interval [from, to) semantics: fromBoundary=CLOSED, toBoundary=OPEN
      Expr dateConstraint = buildDateWindowConstraint(timeField, cursor, upper);
      Expr combined = Exprs.and(List.of(planExpr.expr(), dateConstraint));

      result.add(new SlicePlan(index, signatureHash, specJson, combined));

      log.debug(
          "[INGEST][APP] Date slice prepared, sliceNo={}, from={}, to={}, hash={}",
          index,
          cursor,
          upper,
          signatureHash);

      cursor = upper;
      index++;
    }
    return result;
  }

  /**
   * Build the date-window constraint expression. Half-open interval semantics: from is inclusive,
   * to is exclusive.
   *
   * @param field time field name
   * @param from slice start date (inclusive)
   * @param to slice end date (exclusive)
   * @return range expression
   */
  private Expr buildDateWindowConstraint(String field, LocalDate from, LocalDate to) {
    // Use rangeDate with explicit boundaries: [from, to)
    // includeFrom=true (CLOSED), includeTo=false (OPEN)
    return Exprs.rangeDate(field, from, to, true, false);
  }

  /**
   * Resolve the time field from the configuration snapshot. Priority: DATE mode offsetFieldKey >
   * windowDateFieldKey.
   *
   * @param snapshot provenance/source configuration snapshot
   * @return field name usable for range filtering; null when it cannot be resolved
   */
  private String resolveTimeField(ProvenanceConfigSnapshot snapshot) {
    if (snapshot == null) {
      return null;
    }
    ProvenanceConfigSnapshot.WindowOffsetConfig windowOffset = snapshot.windowOffset();
    if (windowOffset == null) {
      return null;
    }
    if (StrUtil.equalsIgnoreCase(windowOffset.offsetTypeCode(), "DATE")
        && StrUtil.isNotBlank(windowOffset.offsetFieldKey())) {
      return windowOffset.offsetFieldKey();
    }
    if (StrUtil.isNotBlank(windowOffset.windowDateFieldKey())) {
      return windowOffset.windowDateFieldKey();
    }
    return null;
  }

  /**
   * Build the slice spec JSON and normalize it. Fields: strategy, window (from/to + boundary +
   * timezone). On normalization failure, fallback to a minimal JSON to keep hashing available.
   *
   * @param context slice context
   * @param from window start (Instant for auditability)
   * @param to window end (Instant for auditability)
   * @return normalization result (canonical JSON + hash material)
   */
  private JsonNormalizer.Result buildSpec(SlicePlanningContext context, Instant from, Instant to) {
    ProvenanceConfigSnapshot configSnapshot = context.configSnapshot();
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("strategy", code().getCode());

    // Build the window node with timezone and boundary semantics for auditability
    // Note: we store Instant for auditability, but the actual query uses date-only
    ObjectNode window = root.putObject("window");
    window.put("from", from.toString());
    window.put("to", to.toString());
    ObjectNode boundary = window.putObject("boundary");
    boundary.put("from", "CLOSED");
    boundary.put("to", "OPEN");

    String timezone =
        configSnapshot != null && configSnapshot.provenance() != null
            ? StrUtil.blankToDefault(configSnapshot.provenance().timezoneDefault(), "UTC")
            : "UTC";
    window.put("timezone", timezone);

    try {
      return JsonNormalizer.normalizeDefault(root);
    } catch (JsonNormalizer.JsonNormalizationException ex) {
      log.error(
          "[INGEST][APP] Failed to normalize slice spec, fallback to minimal payload, from={}, to={}",
          from,
          to,
          ex);
      String fallback = "{\"strategy\":\"" + code().getCode() + "\"}";
      try {
        return JsonNormalizer.normalizeDefault(fallback);
      } catch (JsonNormalizer.JsonNormalizationException ignored) {
        throw ex;
      }
    }
  }
}
