package com.patra.starter.expr.compiler.render;

import com.patra.expr.Expr;
import com.patra.starter.expr.compiler.model.Issue;
import com.patra.starter.expr.compiler.model.RenderTrace;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;

import java.util.List;
import java.util.Map;

public interface ExprRenderer {
    RenderOutcome render(Expr expression, ProvenanceSnapshot snapshot, boolean traceEnabled);

    record RenderOutcome(String query,
                         Map<String, String> params,
                         List<Issue> warnings,
                         RenderTrace trace) {
    }
}
