package com.patra.registry.domain.model.vo.expr;

import java.util.List;
import java.util.Objects;

/**
 * Aggregated expression snapshot for a provenance scope.
 *
 * <p>This record captures a complete, immutable snapshot of all expression-related
 * configuration for a specific provenance at a given point in time. It includes:
 * <ul>
 *   <li>Field dictionary entries - canonical field definitions</li>
 *   <li>Field capabilities - allowed operations and constraints per field</li>
 *   <li>Render rules - how to transform expression atoms into query fragments or parameters</li>
 *   <li>API parameter mappings - standard key to provider parameter name mappings</li>
 * </ul>
 *
 * <p>This snapshot is typically used by the expression rendering engine to:
 * <ol>
 *   <li>Validate user input expressions against declared capabilities</li>
 *   <li>Select appropriate render rules based on field/operation/match/negation/value-type dimensions</li>
 *   <li>Transform rendered standard keys into provider-specific parameter names</li>
 * </ol>
 *
 * @author linqibin
 * @since 0.1.0
 */
public record ExprSnapshot(
        /* List of canonical expression field definitions; never {@code null}, may be empty */
        List<ExprField> fields,
        /* List of field capability declarations; never {@code null}, may be empty */
        List<ExprCapability> capabilities,
        /* List of render rules for expression atoms; never {@code null}, may be empty */
        List<ExprRenderRule> renderRules,
        /* List of API parameter name mappings; never {@code null}, may be empty */
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
