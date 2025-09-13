package com.patra.starter.expr.compiler.render;

import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Expr;
import com.patra.starter.expr.compiler.ExprCompiler.RenderTrace;
import com.patra.starter.expr.compiler.ExprCompiler.Issue;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;

import java.util.List;
import java.util.Map;

/**
 * 表达式渲染器：将 Expr 基于渲染规则转为 query/params。
 * 最小实现仅支持 TERM / IN；其余类型先跳过。
 */
public interface ExprRenderer {

    Outcome render(Expr expr,
                   ProvenanceSnapshot snapshot,
                   ProvenanceCode provenance,
                   String operation,
                   boolean traceEnabled);

    record Outcome(String query, Map<String, String> params, RenderTrace trace, List<Issue> warnings) {
    }
}
