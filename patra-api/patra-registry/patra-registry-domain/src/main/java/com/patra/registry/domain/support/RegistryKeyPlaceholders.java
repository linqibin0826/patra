package com.patra.registry.domain.support;

/**
 * Registry dimension/condition key placeholder constants.
 *
 * <p>Unified reserved words for SOURCE/TASK scopes used in snapshots or configuration merging,
 * avoiding duplicate definitions across layers.</p>
 *
 * <p>Reference: docs/patra-registry/expr/Registry-expr-schema-design.md</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class RegistryKeyPlaceholders {

    private RegistryKeyPlaceholders() {
    }

    /**
     * Represents "all tasks/sources", used as fallback key in scope merging.
     */
    public static final String ALL = "ALL";

    /**
     * Represents "any value", typically used for wildcard/agnostic dimension matching.
     */
    public static final String ANY = "ANY";

    /**
     * Normalized marker for negated rule (negated = true).
     */
    public static final String NEGATED_TRUE = "T";

    /**
     * Normalized marker for non-negated rule (negated = false or explicit false).
     */
    public static final String NEGATED_FALSE = "F";
}
