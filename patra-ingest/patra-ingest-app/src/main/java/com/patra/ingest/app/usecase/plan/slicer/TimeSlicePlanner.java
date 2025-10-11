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
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Time-window based slicing strategy (Application Layer · Policy).
 * <p>
 * Splits the upstream planning window [from, to) into several half-open sub-windows using
 * a fixed step; each sub-window is paired with the business expression to form an independent Slice.
 * </p>
 * <p>Design notes:
 * <ul>
 *   <li>Step configuration: prefer the normalized step from the trigger context (ISO-8601 Duration);
 *       fallback to the default 1h when invalid.</li>
 *   <li>Time field resolution: offsetFieldName (DATE mode) > defaultDateFieldName.</li>
 *   <li>Idempotence: build a canonical JSON and take sha256; repeated planning yields identical signatures.</li>
 *   <li>Boundary: the last slice aligns to the window end if it is shorter than the step.</li>
 *   <li>Complexity: O(n), n = ceil((to - from) / step).</li>
 * </ul>
 * </p>
 * <p>Return empty list when: window missing; from >= to; or the time field cannot be resolved.</p>
 */
@Slf4j
@Component
public class TimeSlicePlanner implements SlicePlanner {

    /**
     * Default slice step (1 hour).
     */
    private static final Duration DEFAULT_STEP = Duration.ofHours(1);

    @Override
    public SliceStrategy code() {
        return SliceStrategy.TIME;
    }

    @Override
    public List<SlicePlan> slice(SlicePlanningContext context) {
        // Initialize the result to keep ordering stable even on early returns
        List<SlicePlan> result = new ArrayList<>();
        if (context.window() == null || context.window().from() == null || context.window().to() == null) {
            log.warn("[INGEST][APP] Skip time slicing because planning window is missing: norm={}, window=.",
                    context.norm(), context.window());
            return result;
        }

        // Resolve time field: prefer offsetFieldName (DATE mode), otherwise fallback to defaultDateFieldName
        String timeField = resolveTimeField(context.configSnapshot());
        if (timeField == null) {
            log.error("[INGEST][APP] Cannot resolve time field from provenance snapshot, provenanceCode={}, operation={}",
                    context.norm().provenanceCode(),
                    context.norm().operationCode());
            return result;
        }

        Instant from = context.window().from();
        Instant to = context.window().to();
        if (!from.isBefore(to)) {
            log.warn("[INGEST][APP] Skip time slicing because window is not forward, from={} to=.", from, to);
            return result;
        }

        // Use custom step from norm when present; otherwise fallback to the default
        Duration step = DEFAULT_STEP;
        if (StrUtil.isNotBlank(context.norm().step())) {
            try {
                step = Duration.parse(context.norm().step().trim());
            } catch (Exception e) {
                log.warn("[INGEST][APP] Invalid step format, fallback to default, stepString=.", context.norm().step(), e);
            }
        }

        Instant cursor = from;
        int index = 1;
        PlanExpressionDescriptor planExpr = context.planExpression();
        while (cursor.isBefore(to)) {
            // Compute the current slice upper bound; ensure the last slice aligns to the window end
            Instant upper = cursor.plus(step);
            if (upper.isAfter(to)) {
                upper = to;
            }

            // Build the slice spec and generate a stable signature
            JsonNormalizer.Result specNormalized = buildSpec(context, cursor, upper);
            String specJson = specNormalized.getCanonicalJson();
            String signatureHash = HashUtils.sha256Hex(specNormalized.getHashMaterial());

            // Combine the plan expression with the time-window constraint
            Expr timeConstraint = buildTimeWindowConstraint(timeField, cursor, upper);
            Expr combined = Exprs.and(List.of(planExpr.expr(), timeConstraint));

            result.add(new SlicePlan(
                    index,
                    signatureHash,
                    specJson,
                    combined));

            log.debug("[INGEST][APP] Time slice prepared, sliceNo={}, from={}, to={}, hash=", index, cursor, upper, signatureHash);

            cursor = upper;
            index++;
        }
        return result;
    }

    /**
     * Build the time-window constraint expression. Half-open interval semantics: from is inclusive, to is exclusive.
     *
     * @param field time field name
     * @param from  slice start (inclusive)
     * @param to    slice end (exclusive)
     * @return range expression
     */
    private Expr buildTimeWindowConstraint(String field, Instant from, Instant to) {
        return Exprs.rangeDateTime(field, from, to);
    }

    /**
     * Resolve the time field from the configuration snapshot. Priority: DATE mode offsetFieldName > defaultDateFieldName.
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
                && StrUtil.isNotBlank(windowOffset.offsetFieldName())) {
            return windowOffset.offsetFieldName();
        }
        if (StrUtil.isNotBlank(windowOffset.defaultDateFieldName())) {
            return windowOffset.defaultDateFieldName();
        }
        return null;
    }

    /**
     * Build the slice spec JSON and normalize it. Fields: strategy, window (from/to + boundary + timezone).
     * On normalization failure, fallback to a minimal JSON to keep hashing available.
     *
     * @param context slice context
     * @param from    window start
     * @param to      window end
     * @return normalization result (canonical JSON + hash material)
     */
    private JsonNormalizer.Result buildSpec(SlicePlanningContext context, Instant from, Instant to) {
        ProvenanceConfigSnapshot configSnapshot = context.configSnapshot();
        ObjectNode root = JsonNodeFactory.instance.objectNode();
        root.put("strategy", code().getCode());

        // Build the window node with timezone and boundary semantics for auditability
        ObjectNode window = root.putObject("window");
        window.put("from", from.toString());
        window.put("to", to.toString());
        ObjectNode boundary = window.putObject("boundary");
        boundary.put("from", "CLOSED");
        boundary.put("to", "OPEN");

        String timezone = configSnapshot != null && configSnapshot.provenance() != null
                ? StrUtil.blankToDefault(configSnapshot.provenance().timezoneDefault(), "UTC")
                : "UTC";
        window.put("timezone", timezone);

        try {
            return JsonNormalizer.normalizeDefault(root);
        } catch (JsonNormalizer.JsonNormalizationException ex) {
            log.error("[INGEST][APP] Failed to normalize slice spec, fallback to minimal payload, from={}, to=", from, to, ex);
            String fallback = "{\"strategy\":\"" + code().getCode() + "\"}";
            try {
                return JsonNormalizer.normalizeDefault(fallback);
            } catch (JsonNormalizer.JsonNormalizationException ignored) {
                throw ex;
            }
        }
    }
}
