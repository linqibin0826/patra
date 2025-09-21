package com.patra.starter.expr.compiler.model;

import java.util.Collections;
import java.util.List;

public record ValidationReport(List<Issue> warnings, List<Issue> errors) {
    public ValidationReport {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public boolean ok() {
        return errors.isEmpty();
    }

    public static ValidationReport empty() {
        return new ValidationReport(Collections.emptyList(), Collections.emptyList());
    }
}
