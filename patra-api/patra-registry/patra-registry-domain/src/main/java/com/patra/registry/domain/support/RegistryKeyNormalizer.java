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
 ALL, otherwise trim and preserve original case for backward compatibility.
     *
     * @param operationType the operation type value to normalize
     * @return normalized operation type key, never null
     */
    public static String normalizeOperationKey(String operationType) {
        if (operationType == null || operationType.isBlank()) {
            return RegistryKeyPlaceholders.ALL;
        }
        return operationType.trim();
    }

    /**
     * Normalizes generic code: non-null assertion + trim + uppercase conversion.
     *
     * @param value the code value to normalize
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
     * Normalizes field key: non-null assertion + trim, preserving original case.
     *
     * @param value the field key value to normalize
     * @return normalized field key
     * @throws DomainValidationException if value is null
     */
    public static String normalizeFieldKey(String value) {
        if (value == null) {
            throw new DomainValidationException("value cannot be null");
        }
        return value.trim();
    }

    /**
 ANY, otherwise uppercase.
     *
     * @param matchTypeCode the match type code to normalize
     * @return normalized match type key, never null
     */
    public static String normalizeMatchKey(String matchTypeCode) {
        if (matchTypeCode == null || matchTypeCode.isBlank()) {
            return RegistryKeyPlaceholders.ANY;
        }
        return matchTypeCode.trim().toUpperCase(Locale.ROOT);
    }

    /**
 F.
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
 ANY, otherwise uppercase.
     *
     * @param valueTypeCode the value type code to normalize
     * @return normalized value type key, never null
     */
    public static String normalizeValueKey(String valueTypeCode) {
        if (valueTypeCode == null || valueTypeCode.isBlank()) {
            return RegistryKeyPlaceholders.ANY;
        }
        return valueTypeCode.trim().toUpperCase(Locale.ROOT);
    }
}
