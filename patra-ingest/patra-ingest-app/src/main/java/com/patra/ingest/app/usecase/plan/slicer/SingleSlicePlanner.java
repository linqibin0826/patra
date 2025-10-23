package com.patra.ingest.app.usecase.plan.slicer;

import com.patra.common.json.JsonNormalizer;
import com.patra.common.util.HashUtils;
import com.patra.expr.Expr;
import com.patra.ingest.app.usecase.plan.slicer.model.SlicePlan;
import com.patra.ingest.app.usecase.plan.slicer.model.SlicePlanningContext;
import com.patra.ingest.domain.model.enums.SliceStrategy;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Single-slice strategy (Application Layer · Policy).
 *
 * <p>Use when the window does not need to be further partitioned or the business expression already
 * embeds sufficient filtering (e.g., full replay or driven by an external ID list). This strategy
 * produces exactly one slice with sliceNo = 1 and reuses the upstream plan expression. Guarantees:
 *
 * <ul>
 *   <li>Idempotence: stable signature via canonical JSON spec + hash.
 *   <li>Minimal overhead: no loops; O(1) complexity.
 *   <li>Window semantics: if a window is provided upstream, the from/to will be recorded in the
 *       slice spec for auditing.
 * </ul>
 *
 * <p>Boundary: if the window is null, slicing still returns a single item; the caller decides
 * whether windowless execution is allowed.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class SingleSlicePlanner implements SlicePlanner {

  @Override
  public SliceStrategy code() {
    return SliceStrategy.SINGLE;
  }

  @Override
  public List<SlicePlan> slice(SlicePlanningContext context) {
    // UPDATE / ID-driven cases: do not add extra window constraints here; respect the plan's base
    // expression
    Expr baseExpr = context.planExpression().expr();

    // Build window spec JSON including window information if present
    Map<String, Object> specMap = new java.util.HashMap<>();
    specMap.put("strategy", code().getCode());
    if (context.window() != null) {
      Map<String, String> windowMap = new java.util.HashMap<>();
      if (context.window().from() != null) {
        windowMap.put("from", context.window().from().toString());
      }
      if (context.window().to() != null) {
        windowMap.put("to", context.window().to().toString());
      }
      if (!windowMap.isEmpty()) {
        specMap.put("window", windowMap);
      }
    }

    JsonNormalizer.Result specNormalized = JsonNormalizer.normalizeDefault(specMap);
    String specJson = specNormalized.getCanonicalJson();
    String signatureHash = HashUtils.sha256Hex(specNormalized.getHashMaterial());

    log.debug(
        "Single slice planned, provenance={}, hash={}",
        context.norm().provenanceCode(),
        signatureHash);

    return List.of(new SlicePlan(1, signatureHash, specJson, baseExpr));
  }
}
