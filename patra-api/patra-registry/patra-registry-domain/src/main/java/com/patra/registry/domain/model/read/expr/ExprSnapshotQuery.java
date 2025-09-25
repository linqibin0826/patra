package com.patra.registry.domain.model.read.expr;

import java.util.List;
import java.util.Objects;

/**
 * Aggregated expression snapshot query view.
 */
public record ExprSnapshotQuery(
        List<ExprFieldQuery> fields,
        List<ExprCapabilityQuery> capabilities,
        List<ExprRenderRuleQuery> renderRules,
        List<ApiParamMappingQuery> apiParamMappings
) {
    public ExprSnapshotQuery {
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(renderRules, "renderRules");
        Objects.requireNonNull(apiParamMappings, "apiParamMappings");
    }
}
