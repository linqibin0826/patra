package com.patra.registry.domain.support;

import com.patra.registry.domain.exception.DomainValidationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for RegistryKeyStandardizer.
 */
class RegistryKeyStandardizerTest {

    @Test
    void toOperationKeyOrAll_nullOrBlank_returnsALL() {
        assertEquals(RegistryKeyPlaceholders.ALL, RegistryKeyStandardizer.toOperationKeyOrAll(null));
        assertEquals(RegistryKeyPlaceholders.ALL, RegistryKeyStandardizer.toOperationKeyOrAll(" "));
    }

    @Test
    void toOperationKeyOrAll_trimsButPreservesCase() {
        assertEquals("HarVest", RegistryKeyStandardizer.toOperationKeyOrAll("  HarVest  "));
    }

    @Test
    void toUppercaseCode_null_throws() {
        assertThrows(DomainValidationException.class, () -> RegistryKeyStandardizer.toUppercaseCode(null));
    }

    @Test
    void toUppercaseCode_transforms() {
        assertEquals("ABC", RegistryKeyStandardizer.toUppercaseCode("  aBc  "));
    }

    @Test
    void toTrimmedFieldKey_null_throws() {
        assertThrows(DomainValidationException.class, () -> RegistryKeyStandardizer.toTrimmedFieldKey(null));
    }

    @Test
    void toTrimmedFieldKey_trimsPreserveCase() {
        assertEquals("FiElD", RegistryKeyStandardizer.toTrimmedFieldKey("  FiElD  "));
    }

    @Test
    void toMatchTypeKeyOrAny_nullOrBlank_returnsANY() {
        assertEquals(RegistryKeyPlaceholders.ANY, RegistryKeyStandardizer.toMatchTypeKeyOrAny(null));
        assertEquals(RegistryKeyPlaceholders.ANY, RegistryKeyStandardizer.toMatchTypeKeyOrAny(" "));
    }

    @Test
    void toMatchTypeKeyOrAny_uppercase() {
        assertEquals("EXACT", RegistryKeyStandardizer.toMatchTypeKeyOrAny("  exact  "));
    }

    @Test
    void toNegatedKeyOrAny_null_any_true_T_false_F() {
        assertEquals(RegistryKeyPlaceholders.ANY, RegistryKeyStandardizer.toNegatedKeyOrAny(null));
        assertEquals(RegistryKeyPlaceholders.NEGATED_TRUE, RegistryKeyStandardizer.toNegatedKeyOrAny(true));
        assertEquals(RegistryKeyPlaceholders.NEGATED_FALSE, RegistryKeyStandardizer.toNegatedKeyOrAny(false));
    }

    @Test
    void toValueTypeKeyOrAny_nullOrBlank_returnsANY_uppercaseOtherwise() {
        assertEquals(RegistryKeyPlaceholders.ANY, RegistryKeyStandardizer.toValueTypeKeyOrAny(null));
        assertEquals(RegistryKeyPlaceholders.ANY, RegistryKeyStandardizer.toValueTypeKeyOrAny(" "));
        assertEquals("NUMBER", RegistryKeyStandardizer.toValueTypeKeyOrAny(" number "));
    }
}
