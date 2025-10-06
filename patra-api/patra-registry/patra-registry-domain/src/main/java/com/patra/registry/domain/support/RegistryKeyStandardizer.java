package com.patra.registry.domain.support;

import com.patra.registry.domain.exception.DomainValidationException;

import java.util.Locale;

/**
 * Standardization utility for Registry dimension/condition keys.
 *
 * <p>Ensures dimensional keys meet constraints across layers (operation_type/field_key/code/etc.),
 * enabling stable hash/lookup, avoiding NULL ambiguity, and supporting schema versioning compatibility.</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class RegistryKeyStandardizer {

    private RegistryKeyStandardizer() {
    }

    // -----------------------------
    // Descriptive API (preferred)
    // -----------------------------

    /**
     * Returns normalized operation key or {@code ALL} when input is null/blank.
     *
     * <p>Preserves original case for backward compatibility with existing configuration.</p>
     *
     * @param operationType raw operation type
     * @return trimmed operation key or {@code ALL}
     */
    public static String toOperationKeyOrAll(String operationType) {
        if (operationType == null || operationType.isBlank()) {
            return RegistryKeyPlaceholders.ALL;
        }
        return operationType.trim();
    }

    /**
     * Returns trimmed uppercase code, throws when input is null.
     *
     * <p>Use for dictionary/status codes that require case-insensitive comparison.</p>
     *
     * @param value raw code value
     * @return uppercase code (trimmed)
     * @throws DomainValidationException when value is null
     */
    public static String toUppercaseCode(String value) {
        if (value == null) {
            throw new DomainValidationException("value cannot be null");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Returns trimmed field key, throws when input is null. Case is preserved.
     *
     * @param value raw field key
     * @return trimmed field key
     * @throws DomainValidationException when value is null
     */
    public static String toTrimmedFieldKey(String value) {
        if (value == null) {
            throw new DomainValidationException("value cannot be null");
        }
        return value.trim();
    }

    /**
     * Returns normalized match type key or {@code ANY} when input is null/blank.
     *
     * @param matchTypeCode raw match type code
     * @return uppercase match type key or {@code ANY}
     */
    public static String toMatchTypeKeyOrAny(String matchTypeCode) {
        if (matchTypeCode == null || matchTypeCode.isBlank()) {
            return RegistryKeyPlaceholders.ANY;
        }
        return matchTypeCode.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Returns normalized negated key: {@code ANY} for null, {@code T} for true, {@code F} for false.
     *
     * @param negated raw negated flag
     * @return {@code ANY}/{@code T}/{@code F}
     */
    public static String toNegatedKeyOrAny(Boolean negated) {
        if (negated == null) {
            return RegistryKeyPlaceholders.ANY;
        }
        return Boolean.TRUE.equals(negated) ? RegistryKeyPlaceholders.NEGATED_TRUE : RegistryKeyPlaceholders.NEGATED_FALSE;
    }

    /**
     * Returns normalized value type key or {@code ANY} when input is null/blank.
     *
     * @param valueTypeCode raw value type code
     * @return uppercase value type key or {@code ANY}
     */
    public static String toValueTypeKeyOrAny(String valueTypeCode) {
        if (valueTypeCode == null || valueTypeCode.isBlank()) {
            return RegistryKeyPlaceholders.ANY;
        }
        return valueTypeCode.trim().toUpperCase(Locale.ROOT);
    }

}
