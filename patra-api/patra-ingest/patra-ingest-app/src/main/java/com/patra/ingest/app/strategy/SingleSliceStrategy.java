package com.patra.ingest.app.strategy;

import com.patra.ingest.app.strategy.model.SliceContext;
import com.patra.ingest.app.strategy.model.SliceDraft;
import com.patra.expr.Expr;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SingleSliceStrategy implements SliceStrategy {
    @Override
    public String code() { return "SINGLE"; }

    @Override
    public List<SliceDraft> slice(SliceContext context) {
        // UPDATE 模式：不加时间窗口，直接使用 Plan 业务表达式
        Expr base = context.planExpr().expr();
        String json = context.planExpr().jsonSnapshot();
        String hash = context.planExpr().hash();
        return List.of(new SliceDraft(
                1,
                "SINGLE",
                "{\"type\":\"SINGLE\"}",
                base,
                json,
                hash,
                context.window().from(),
                context.window().to()));
    }
}
