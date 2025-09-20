package com.patra.registry.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for DictionaryValidationException class.
 * Tests the creation and behavior of dictionary validation exceptions.
 * 
 * @author linqibin
 * @since 0.1.0
 */
class DictionaryValidationExceptionTest {
    
    /**
     * Test dictionary validation exception with message, type code and item code.
     */
    @Test
    void shouldCreateDictionaryValidationExceptionWithMessageTypeCodeAndItemCode() {
        // Given
        String message = "Dictionary validation failed";
        String typeCode = "USER_TYPE";
        String itemCode = "ADMIN";
        
        // When
        DictionaryValidationException exception = new DictionaryValidationException(message, typeCode, itemCode);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getTypeCode()).isEqualTo(typeCode);
        assertThat(exception.getItemCode()).isEqualTo(itemCode);
        assertThat(exception.getValidationErrors()).containsExactly(message);
        assertThat(exception.getCause()).isNull();
    }
    
    /**
     * Test dictionary validation exception with validation errors list.
     */
    @Test
    void shouldCreateDictionaryValidationExceptionWithValidationErrorsList() {
        // Given
        String typeCode = "USER_TYPE";
        java.util.List<String> validationErrors = java.util.List.of(
            "Field 'name' is required",
            "Field 'code' must be unique"
        );
        
        // When
        DictionaryValidationException exception = new DictionaryValidationException(validationErrors, typeCode);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo("Dictionary validation failed for type USER_TYPE: Field 'name' is required, Field 'code' must be unique");
        assertThat(exception.getTypeCode()).isEqualTo(typeCode);
        assertThat(exception.getItemCode()).isNull();
        assertThat(exception.getValidationErrors()).containsExactlyElementsOf(validationErrors);
        assertThat(exception.getCause()).isNull();
    }
    
    /**
     * Test dictionary validation exception with message, validation errors, type code and item code.
     */
    @Test
    void shouldCreateDictionaryValidationExceptionWithMessageValidationErrorsTypeCodeAndItemCode() {
        // Given
        String message = "Dictionary validation failed with multiple errors";
        String typeCode = "USER_TYPE";
        String itemCode = "ADMIN";
        java.util.List<String> validationErrors = java.util.List.of(
            "Field 'name' is required",
            "Field 'code' must be unique"
        );
        
        // When
        DictionaryValidationException exception = new DictionaryValidationException(message, validationErrors, typeCode, itemCode);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getTypeCode()).isEqualTo(typeCode);
        assertThat(exception.getItemCode()).isEqualTo(itemCode);
        assertThat(exception.getValidationErrors()).containsExactlyElementsOf(validationErrors);
        assertThat(exception.getCause()).isNull();
    }
    
    /**
     * Test dictionary validation exception with null message is allowed.
     */
    @Test
    void shouldAllowNullMessage() {
        // Given & When
        DictionaryValidationException exception = new DictionaryValidationException("", "USER_TYPE", "ADMIN");
        
        // Then
        assertThat(exception.getMessage()).isEqualTo("");
        assertThat(exception.getTypeCode()).isEqualTo("USER_TYPE");
        assertThat(exception.getItemCode()).isEqualTo("ADMIN");
        assertThat(exception.getValidationErrors()).containsExactly("");
    }
    
    /**
     * Test dictionary validation exception with null validation errors list.
     */
    @Test
    void shouldHandleNullValidationErrorsList() {
        // Given
        String message = "Dictionary validation failed";
        String typeCode = "USER_TYPE";
        String itemCode = "ADMIN";
        
        // When
        DictionaryValidationException exception = new DictionaryValidationException(message, null, typeCode, itemCode);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getTypeCode()).isEqualTo(typeCode);
        assertThat(exception.getItemCode()).isEqualTo(itemCode);
        assertThat(exception.getValidationErrors()).isEmpty(); // Should be empty, not null
    }
    
    /**
     * Test dictionary validation exception inheritance hierarchy.
     */
    @Test
    void shouldMaintainCorrectInheritanceHierarchy() {
        // Given
        DictionaryValidationException exception = new DictionaryValidationException("test", "USER_TYPE", "ADMIN");
        
        // Then
        assertThat(exception).isInstanceOf(DictionaryValidationException.class);
        assertThat(exception).isInstanceOf(RegistryRuleViolation.class);
        assertThat(exception).isInstanceOf(RegistryException.class);
        assertThat(exception).isInstanceOf(com.patra.common.error.DomainException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
    
    /**
     * Test dictionary validation exception error traits.
     */
    @Test
    void shouldHaveCorrectErrorTraits() {
        // Given
        DictionaryValidationException exception = new DictionaryValidationException("test", "USER_TYPE", "ADMIN");
        
        // When
        Set<ErrorTrait> traits = exception.getErrorTraits();
        
        // Then
        assertThat(traits).containsExactly(ErrorTrait.RULE_VIOLATION);
        assertThat(traits).isUnmodifiable();
    }
    
    /**
     * Test dictionary validation exception with empty validation errors list.
     */
    @Test
    void shouldHandleEmptyValidationErrorsList() {
        // Given
        String typeCode = "USER_TYPE";
        java.util.List<String> emptyErrors = java.util.List.of();
        
        // When
        DictionaryValidationException exception = new DictionaryValidationException(emptyErrors, typeCode);
        
        // Then
        assertThat(exception.getValidationErrors()).isEmpty();
        assertThat(exception.getTypeCode()).isEqualTo(typeCode);
        assertThat(exception.getItemCode()).isNull();
    }
}