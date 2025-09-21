package com.patra.registry.domain.model.vo.expr;

import java.util.List;
import java.util.Objects;

/**
 * Aggregated expression snapshot for a provenance scope.
 */
public record ExprSnapshot(
        List<ExprField> fields,
        List<ExprCapability> capabilities,
        List<ExprRenderRule> renderRules,
        List<ApiParamMapping> apiParamMappings
) {
    public ExprSnapshot {
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(renderRules, "renderRules");
        Objects.requireNonNull(apiParamMappings, "apiParamMappings");
    }
}
