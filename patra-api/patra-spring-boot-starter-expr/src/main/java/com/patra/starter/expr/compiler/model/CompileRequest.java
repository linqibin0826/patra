package com.patra.starter.expr.compiler.model;

import com.patra.common.enums.ProvenanceCode;
import com.patra.expr.Expr;

import java.util.Locale;
import java.util.Objects;

public record CompileRequest(
        Expr expression,
        ProvenanceCode provenance,
        String taskType,
        String operationCode,
        CompileOptions options
) {
    public CompileRequest {
        Objects.requireNonNull(expression, "expression");
        Objects.requireNonNull(provenance, "provenance");
        operationCode = normalizeOperation(operationCode);
        options = options == null ? CompileOptions.defaults() : options;
    }

    private static String normalizeOperation(String op) {
        if (op == null || op.isBlank()) {
            return "SEARCH";
        }
        return op.trim().toUpperCase(Locale.ROOT);
    }
}
