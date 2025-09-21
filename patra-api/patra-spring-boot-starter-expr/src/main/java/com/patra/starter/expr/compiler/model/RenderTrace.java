package com.patra.starter.expr.compiler.model;

import java.util.List;
import java.util.Objects;

public record RenderTrace(List<Hit> hits) {
    public RenderTrace {
        hits = hits == null ? List.of() : List.copyOf(hits);
    }

    public record Hit(String fieldKey, String op, int priority, String ruleId) {
        public Hit {
            Objects.requireNonNull(fieldKey, "fieldKey");
            Objects.requireNonNull(op, "op");
            Objects.requireNonNull(ruleId, "ruleId");
        }
    }
}
