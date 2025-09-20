package com.patra.registry.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for DictionaryItemDisabled domain exception.
 * 
 * @author linqibin
 * @since 0.1.0
 */
class DictionaryItemDisabledTest {
    
    /**
     * Test dictionary item disabled exception with valid parameters.
     */
    @Test
    void shouldCreateDictionaryItemDisabledWithValidParameters() {
        // Given
        String typeCode = "COUNTRY";
        String itemCode = "US";
        
        // When
        DictionaryItemDisabled exception = new DictionaryItemDisabled(typeCode, itemCode);
        
        // Then
        assertThat(exception.getTypeCode()).isEqualTo(typeCode);
        assertThat(exception.getItemCode()).isEqualTo(itemCode);
        assertThat(exception.getMessage()).isEqualTo("Dictionary item is disabled: typeCode=COUNTRY, itemCode=US");
        assertThat(exception.getCause()).isNull();
    }
    
    /**
     * Test dictionary item disabled exception with custom message.
     */
    @Test
    void shouldCreateDictionaryItemDisabledWithCustomMessage() {
        // Given
        String typeCode = "COUNTRY";
        String itemCode = "US";
        String customMessage = "Custom disabled message";
        
        // When
        DictionaryItemDisabled exception = new DictionaryItemDisabled(typeCode, itemCode, customMessage);
        
        // Then
        assertThat(exception.getTypeCode()).isEqualTo(typeCode);
        assertThat(exception.getItemCode()).isEqualTo(itemCode);
        assertThat(exception.getMessage()).isEqualTo(customMessage);
        assertThat(exception.getCause()).isNull();
    }
    
    /**
     * Test dictionary item disabled exception with custom message and cause.
     */
    @Test
    void shouldCreateDictionaryItemDisabledWithCustomMessageAndCause() {
        // Given
        String typeCode = "COUNTRY";
        String itemCode = "US";
        String customMessage = "Custom disabled message";
        RuntimeException cause = new RuntimeException("Root cause");
        
        // When
        DictionaryItemDisabled exception = new DictionaryItemDisabled(typeCode, itemCode, customMessage, cause);
        
        // Then
        assertThat(exception.getTypeCode()).isEqualTo(typeCode);
        assertThat(exception.getItemCode()).isEqualTo(itemCode);
        assertThat(exception.getMessage()).isEqualTo(customMessage);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
    
    /**
     * Test dictionary item disabled exception with null type code should throw IllegalArgumentException.
     */
    @Test
    void shouldThrowIllegalArgumentExceptionForNullTypeCode() {
        // Given
        String itemCode = "US";
        
        // When & Then
        assertThatThrownBy(() -> new DictionaryItemDisabled(null, itemCode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Type code cannot be null or empty");
    }
    
    /**
     * Test dictionary item disabled exception with empty type code should throw IllegalArgumentException.
     */
    @Test
    void shouldThrowIllegalArgumentExceptionForEmptyTypeCode() {
        // Given
        String itemCode = "US";
        
        // When & Then
        assertThatThrownBy(() -> new DictionaryItemDisabled("", itemCode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Type code cannot be null or empty");
        
        assertThatThrownBy(() -> new DictionaryItemDisabled("   ", itemCode))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Type code cannot be null or empty");
    }
    
    /**
     * Test dictionary item disabled exception with null item code should throw IllegalArgumentException.
     */
    @Test
    void shouldThrowIllegalArgumentExceptionForNullItemCode() {
        // Given
        String typeCode = "COUNTRY";
        
        // When & Then
        assertThatThrownBy(() -> new DictionaryItemDisabled(typeCode, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Item code cannot be null or empty");
    }
    
    /**
     * Test dictionary item disabled exception with empty item code should throw IllegalArgumentException.
     */
    @Test
    void shouldThrowIllegalArgumentExceptionForEmptyItemCode() {
        // Given
        String typeCode = "COUNTRY";
        
        // When & Then
        assertThatThrownBy(() -> new DictionaryItemDisabled(typeCode, ""))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Item code cannot be null or empty");
        
        assertThatThrownBy(() -> new DictionaryItemDisabled(typeCode, "   "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Item code cannot be null or empty");
    }
    
    /**
     * Test dictionary item disabled exception inheritance hierarchy.
     */
    @Test
    void shouldMaintainCorrectInheritanceHierarchy() {
        // Given
        DictionaryItemDisabled exception = new DictionaryItemDisabled("COUNTRY", "US");
        
        // Then
        assertThat(exception).isInstanceOf(DictionaryItemDisabled.class);
        assertThat(exception).isInstanceOf(RegistryRuleViolation.class);
        assertThat(exception).isInstanceOf(RegistryException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
    
    /**
     * Test dictionary item disabled exception error traits.
     */
    @Test
    void shouldHaveCorrectErrorTraits() {
        // Given
        DictionaryItemDisabled exception = new DictionaryItemDisabled("COUNTRY", "US");
        
        // When
        Set<ErrorTrait> traits = exception.getErrorTraits();
        
        // Then
        assertThat(traits).containsExactly(ErrorTrait.RULE_VIOLATION);
        assertThat(traits).isUnmodifiable();
    }
}