package com.patra.ingest.app.orchestration.slice;

import com.patra.expr.Expr;
import com.patra.ingest.app.orchestration.slice.model.SlicePlanningContext;
import com.patra.ingest.app.orchestration.slice.model.SlicePlan;
import com.patra.common.json.JsonNormalizer;
import com.patra.common.util.HashUtils;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class SingleSlicePlanner implements SlicePlanner {
    @Override
    public String code() { return "SINGLE"; }

    @Override
    public List<SlicePlan> slice(SlicePlanningContext context) {
        // UPDATE 模式：不加时间窗口，直接使用 Plan 业务表达式
        Expr base = context.planExpression().expr();
        JsonNormalizer.Result specNormalized = JsonNormalizer.normalizeDefault(Map.of("strategy", code()));
        String specJson = specNormalized.getCanonicalJson();
        String signatureHash = HashUtils.sha256Hex(specNormalized.getHashMaterial());
        return List.of(new SlicePlan(
                1,
                signatureHash,
                specJson,
                base,
                context.window().from(),
                context.window().to()));
    }
}
