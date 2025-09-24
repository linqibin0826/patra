package com.patra.ingest.app.strategy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.expr.Expr;
import com.patra.expr.Exprs;
import com.patra.ingest.app.model.PlanBusinessExpr;
import com.patra.ingest.app.strategy.model.SliceContext;
import com.patra.ingest.app.strategy.model.SliceDraft;
import com.patra.ingest.app.util.ExprHashUtil;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
public class TimeSliceStrategy implements SliceStrategy {

    private static final Duration DEFAULT_STEP = Duration.ofHours(1);

    @Override
    public String code() {
        return "TIME";
    }

    @Override
    public List<SliceDraft> slice(SliceContext context) {
        List<SliceDraft> result = new ArrayList<>();
        if (context.window() == null || context.window().from() == null || context.window().to() == null) return result;
        Instant from = context.window().from();
        Instant to = context.window().to();
        if (!from.isBefore(to)) return result;
        Duration step = DEFAULT_STEP;
        if (context.norm().step() != null && !context.norm().step().isBlank()) {
            try {
                step = Duration.parse(context.norm().step());
            } catch (Exception e) {
                // 保留默认步长
            }
        }
        Instant cursor = from;
        int index = 1;
        PlanBusinessExpr planExpr = context.planExpr();
        while (cursor.isBefore(to)) {
            Instant upper = cursor.plus(step);
            if (upper.isAfter(to)) upper = to;
            String specJson = "{\"type\":\"TIME\",\"from\":\"" + cursor + "\",\"to\":\"" + upper + "\"}";
            Expr timeConstraint = buildTimeWindowConstraint(cursor, upper);
            Expr combined = Exprs.and(List.of(planExpr.expr(), timeConstraint));
            String combinedJson;
            try {
                combinedJson = serializeExprToJson(combined);
            } catch (JsonProcessingException e) {
                // 回退为恒真表达式 JSON
                combinedJson = planExpr.jsonSnapshot();
            }
            String combinedHash = ExprHashUtil.sha256Hex(combinedJson);
            result.add(new SliceDraft(
                    index,
                    specJson,
                    specJson,
                    combined,
                    combinedJson,
                    combinedHash,
                    cursor,
                    upper));
            cursor = upper;
            index++;
        }
        return result;
    }

    /**
     * 构建时间窗口约束表达式
     */
    private Expr buildTimeWindowConstraint(Instant from, Instant to) {
        // 构建 RANGE(updated_at, from, to) 表达式
        return Exprs.rangeDateTime("updated_at", from, to);
    }

    /**
     * 序列化表达式为 JSON
     */
    private String serializeExprToJson(Expr expr) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.writeValueAsString(expr);
    }
}
