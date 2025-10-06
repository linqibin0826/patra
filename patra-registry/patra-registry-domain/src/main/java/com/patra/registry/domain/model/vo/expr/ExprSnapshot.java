package com.patra.registry.domain.model.vo.expr;

import java.util.List;
import java.util.Objects;

/**
 * Aggregated expression snapshot for a provenance scope.
 *
 * <p>This record captures a complete, immutable snapshot of all expression-related
 * configuration for a specific provenance at a given point in time. It is typically used by
 * the expression rendering engine to validate user input expressions, select appropriate render
 * rules, and transform standard keys into provider-specific parameter names.
 *
 * <p>Field descriptions:
 * <ol>
 *   <li>fields - canonical expression field definitions; never null, may be empty</li>
 *   <li>capabilities - field capability declarations specifying allowed operations and constraints; never null</li>
 *   <li>renderRules - render rules for transforming expression atoms into query fragments or parameters; never null</li>
 *   <li>apiParamMappings - mappings from standard keys to provider-specific parameter names; never null</li>
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ExprSnapshot(
        List<ExprField> fields,
        List<ExprCapability> capabilities,
        List<ExprRenderRule> renderRules,
        List<ApiParamMapping> apiParamMappings
) {
    /**
     * Compact canonical constructor enforcing non-null invariants.
     *
     * @throws NullPointerException if any parameter is {@code null}
     */
    public ExprSnapshot {
        Objects.requireNonNull(fields, "fields");
        Objects.requireNonNull(capabilities, "capabilities");
        Objects.requireNonNull(renderRules, "renderRules");
        Objects.requireNonNull(apiParamMappings, "apiParamMappings");
    }
}
