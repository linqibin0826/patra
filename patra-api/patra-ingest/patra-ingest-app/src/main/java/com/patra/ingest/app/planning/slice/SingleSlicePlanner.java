package com.patra.ingest.app.planning.slice;

import com.patra.common.json.JsonNormalizer;
import com.patra.common.util.HashUtils;
import com.patra.expr.Expr;
import com.patra.ingest.app.planning.slice.model.SlicePlan;
import com.patra.ingest.app.planning.slice.model.SlicePlanningContext;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 单切片策略（Application Layer · Policy）。
 * <p>
 * 场景：无需再对窗口做时间/范围分割，或业务表达式已内嵌足够过滤条件（例如：全量回放、按外部 ID 列表驱动）。
 * 该策略仅生成序号=1 的唯一切片，并复用上游 Plan 构建好的业务表达式，保证：
 * <ul>
 *   <li>幂等性：通过 spec 的规范化 JSON + 哈希(signatureHash) 固定；</li>
 *   <li>最小额外开销：不引入循环，算法复杂度 O(1)；</li>
 *   <li>窗口语义：若上游仍传入窗口，会在切片上保留 from/to 以便后续统计。</li>
 * </ul>
 * </p>
 * <p>边界：若 window 为空，不影响切片生成（依旧返回单条），由上层判定是否允许无窗口执行。</p>
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
        // UPDATE / ID 驱动场景：不补充额外窗口约束，遵循 Plan 原有业务表达式
        Expr baseExpr = context.planExpression().expr();
        JsonNormalizer.Result specNormalized = JsonNormalizer.normalizeDefault(Map.of("strategy", code().getCode()));
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
