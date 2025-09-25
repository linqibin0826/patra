package com.patra.ingest.app.strategy;

import com.patra.ingest.app.strategy.model.SliceContext;
import com.patra.ingest.app.strategy.model.SliceDraft;
import com.patra.expr.Expr;
import com.patra.ingest.app.util.SliceSignatureUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SingleSliceStrategy implements SliceStrategy {
    @Autowired
    private com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    @Override
    public String code() { return "SINGLE"; }

    @Override
    public List<SliceDraft> slice(SliceContext context) {
        // UPDATE 模式：不加时间窗口，直接使用 Plan 业务表达式
        Expr base = context.planExpr().expr();
        String json = context.planExpr().jsonSnapshot();
        String hash = context.planExpr().hash();
    String specJson = "{\"strategy\":\"SINGLE\"}";
    String signatureHash = SliceSignatureUtil.signatureHash(objectMapper, specJson);
    return List.of(new SliceDraft(
        1,
        signatureHash,
        specJson,
        base,
        json,
        hash,
        context.window().from(),
        context.window().to()));
    }
}
