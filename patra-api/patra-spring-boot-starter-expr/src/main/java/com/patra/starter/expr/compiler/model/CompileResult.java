package com.patra.starter.expr.compiler.model;

import com.patra.expr.Expr;

import java.util.Map;
import java.util.Objects;

public record CompileResult(String query,
                            Map<String, String> params,
                            Expr normalized,
                            ValidationReport report,
                            SnapshotRef snapshot,
                            RenderTrace trace) {
    public CompileResult {
        query = query == null ? "" : query;
        params = params == null ? Map.of() : Map.copyOf(params);
        report = report == null ? com.patra.starter.expr.compiler.model.ValidationReport.empty() : report;
        Objects.requireNonNull(snapshot, "snapshot");
    }
}
