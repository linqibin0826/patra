package com.patra.registry.domain.model.read.expr;

import java.util.List;
import java.util.Objects;

/**
 * Aggregated expression snapshot query view.
 *
 * <p>Read-optimized projection that bundles fields, capabilities, render rules and API parameter
 * mappings for expression-related configuration.
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ExprSnapshotQuery(
    List<ExprFieldQuery> fields,
    List<ExprCapabilityQuery> capabilities,
    List<ExprRenderRuleQuery> renderRules,
    List<ApiParamMappingQuery> apiParamMappings) {
  public ExprSnapshotQuery {
    Objects.requireNonNull(fields, "fields");
    Objects.requireNonNull(capabilities, "capabilities");
    Objects.requireNonNull(renderRules, "renderRules");
    Objects.requireNonNull(apiParamMappings, "apiParamMappings");
  }
}
