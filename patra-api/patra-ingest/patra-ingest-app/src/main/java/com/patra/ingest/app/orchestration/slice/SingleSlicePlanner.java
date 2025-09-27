package com.patra.ingest.app.orchestration.slice;

import com.patra.common.json.JsonNormalizer;
import com.patra.common.util.HashUtils;
import com.patra.expr.Expr;
import com.patra.ingest.app.orchestration.slice.model.SlicePlan;
import com.patra.ingest.app.orchestration.slice.model.SlicePlanningContext;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 单切片策略（Application Layer · Policy）。
 * <p>
 * 适用于无需再细分窗口的拉取任务，如 ID 驱动或需要在上游表达式中自行控制范围的场景。
 * 直接使用 Plan 的业务表达式生成唯一切片，并确保签名稳定可幂等。
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class SingleSlicePlanner implements SlicePlanner {

    @Override
    public String code() {
        return "SINGLE";
    }

    @Override
    public List<SlicePlan> slice(SlicePlanningContext context) {
        // UPDATE / ID 驱动场景：不补充额外窗口约束，遵循 Plan 原有业务表达式
        Expr baseExpr = context.planExpression().expr();
        JsonNormalizer.Result specNormalized = JsonNormalizer.normalizeDefault(Map.of("strategy", code()));
        String specJson = specNormalized.getCanonicalJson();
        String signatureHash = HashUtils.sha256Hex(specNormalized.getHashMaterial());

        log.debug("Single slice planned, provenance={}, hash={}.",
                context.norm().provenanceCode(), signatureHash);

        return List.of(new SlicePlan(
                1,
                signatureHash,
                specJson,
                baseExpr,
                context.window() == null ? null : context.window().from(),
                context.window() == null ? null : context.window().to()));
    }
}
