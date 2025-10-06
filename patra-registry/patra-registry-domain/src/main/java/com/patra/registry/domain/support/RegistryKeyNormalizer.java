package com.patra.registry.domain.support;

import com.patra.registry.domain.exception.DomainValidationException;

import java.util.Locale;

/**
 * Normalization utility for Registry dimension/condition keys.
 *
 * <p>Ensures dimensional keys meet constraints across layers (operation_type/field_key/code/etc.),
 * enabling stable hash/lookup, avoiding NULL ambiguity, and supporting schema versioning compatibility.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class RegistryKeyNormalizer {

    private RegistryKeyNormalizer() {
    }

    /**
     * Normalizes operation type: null/blank maps to ALL, otherwise trim and preserve original case.
     *
     * <p>Preserves original case for backward compatibility with existing configuration.
     *
     * @param operationType the operation type value to normalize
     * @return normalized operation type key, never null (returns ALL for null/blank)
     */
    public static String normalizeOperationKey(String operationType) {
        if (operationType == null || operationType.isBlank()) {
            return RegistryKeyPlaceholders.ALL;
        }
        return operationType.trim();
    }

    /**
     * Normalizes generic code by trimming and converting to uppercase.
     *
     * <p>Used for dictionary codes and status codes that require case-insensitive comparison.
     *
     * @param value the code value to normalize; must not be null
     * @return normalized code in uppercase
     * @throws DomainValidationException if value is null
     */
    public static String normalizeCode(String value) {
        if (value == null) {
            throw new DomainValidationException("value cannot be null");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Normalizes field key by trimming while preserving original case.
     *
     * <p>Field keys are case-sensitive and should match the canonical definitions
     * in the expression field dictionary.
     *
     * @param value the field key value to normalize; must not be null
     * @return normalized field key with original case preserved
     * @throws DomainValidationException if value is null
     */
    public static String normalizeFieldKey(String value) {
        if (value == null) {
            throw new DomainValidationException("value cannot be null");
        }
        return value.trim();
    }

    /**
     * Normalizes match type code: null/blank maps to ANY, otherwise trim and uppercase.
     *
     * <p>Used for TERM match strategies (PHRASE, EXACT, ANY) in expression render rules.
     *
     * @param matchTypeCode the match type code to normalize
     * @return normalized match type key, never null (returns ANY for null/blank)
     */
    public static String normalizeMatchKey(String matchTypeCode) {
        if (matchTypeCode == null || matchTypeCode.isBlank()) {
            return RegistryKeyPlaceholders.ANY;
        }
        return matchTypeCode.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Normalizes negated flag: null maps to ANY, true to T, false to F.
     *
     * <p>Used for NOT operator handling in expression render rule selection.
     *
     * @param negated the negated flag to normalize
     * @return normalized negated key (ANY/T/F), never null
     */
    public static String normalizeNegatedKey(Boolean negated) {
        if (negated == null) {
            return RegistryKeyPlaceholders.ANY;
        }
        return negated ? RegistryKeyPlaceholders.NEGATED_TRUE : RegistryKeyPlaceholders.NEGATED_FALSE;
    }

    /**
     * Normalizes value type code: null/blank maps to ANY, otherwise trim and uppercase.
     *
     * <p>Used for RANGE value types (STRING/DATE/DATETIME/NUMBER) in expression render rules.
     *
     * @param valueTypeCode the value type code to normalize
     * @return normalized value type key, never null (returns ANY for null/blank)
     */
    public static String normalizeValueKey(String valueTypeCode) {
        if (valueTypeCode == null || valueTypeCode.isBlank()) {
            return RegistryKeyPlaceholders.ANY;
        }
        return valueTypeCode.trim().toUpperCase(Locale.ROOT);
    }
}
