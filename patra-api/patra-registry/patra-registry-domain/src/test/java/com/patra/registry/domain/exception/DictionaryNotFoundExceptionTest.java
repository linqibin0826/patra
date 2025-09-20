package com.patra.registry.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for DictionaryNotFoundException class.
 * Tests the creation and behavior of dictionary not found exceptions.
 * 
 * @author linqibin
 * @since 0.1.0
 */
class DictionaryNotFoundExceptionTest {
    
    /**
     * Test dictionary type not found exception with type code.
     */
    @Test
    void shouldCreateDictionaryTypeNotFoundWithTypeCode() {
        // Given
        String typeCode = "USER_TYPE";
        
        // When
        DictionaryNotFoundException exception = new DictionaryNotFoundException(typeCode);
        
        // Then
        assertThat(exception.getTypeCode()).isEqualTo(typeCode);
        assertThat(exception.getItemCode()).isNull();
        assertThat(exception.getMessage()).isEqualTo("Dictionary type not found: USER_TYPE");
        assertThat(exception.getCause()).isNull();
    }
    
    /**
     * Test dictionary item not found exception with type code and item code.
     */
    @Test
    void shouldCreateDictionaryItemNotFoundWithTypeAndItemCode() {
        // Given
        String typeCode = "USER_TYPE";
        String itemCode = "ADMIN";
        
        // When
        DictionaryNotFoundException exception = new DictionaryNotFoundException(typeCode, itemCode);
        
        // Then
        assertThat(exception.getTypeCode()).isEqualTo(typeCode);
        assertThat(exception.getItemCode()).isEqualTo(itemCode);
        assertThat(exception.getMessage()).isEqualTo("Dictionary item not found: typeCode=USER_TYPE, itemCode=ADMIN");
        assertThat(exception.getCause()).isNull();
    }
    
    /**
     * Test dictionary not found exception with null type code.
     */
    @Test
    void shouldHandleNullTypeCode() {
        // Given & When
        DictionaryNotFoundException exception = new DictionaryNotFoundException((String) null);
        
        // Then
        assertThat(exception.getTypeCode()).isNull();
        assertThat(exception.getItemCode()).isNull();
        assertThat(exception.getMessage()).isEqualTo("Dictionary type not found: null");
    }
    
    /**
     * Test dictionary not found exception with empty type code.
     */
    @Test
    void shouldHandleEmptyTypeCode() {
        // Given & When
        DictionaryNotFoundException exception = new DictionaryNotFoundException("");
        
        // Then
        assertThat(exception.getTypeCode()).isEqualTo("");
        assertThat(exception.getItemCode()).isNull();
        assertThat(exception.getMessage()).isEqualTo("Dictionary type not found: ");
    }
    
    /**
     * Test dictionary item not found exception with null type code.
     */
    @Test
    void shouldHandleNullTypeCodeInItemConstructor() {
        // Given & When
        DictionaryNotFoundException exception = new DictionaryNotFoundException(null, "ADMIN");
        
        // Then
        assertThat(exception.getTypeCode()).isNull();
        assertThat(exception.getItemCode()).isEqualTo("ADMIN");
        assertThat(exception.getMessage()).isEqualTo("Dictionary item not found: typeCode=null, itemCode=ADMIN");
    }
    
    /**
     * Test dictionary item not found exception with null item code.
     */
    @Test
    void shouldHandleNullItemCode() {
        // Given & When
        DictionaryNotFoundException exception = new DictionaryNotFoundException("USER_TYPE", null);
        
        // Then
        assertThat(exception.getTypeCode()).isEqualTo("USER_TYPE");
        assertThat(exception.getItemCode()).isNull();
        assertThat(exception.getMessage()).isEqualTo("Dictionary item not found: typeCode=USER_TYPE, itemCode=null");
    }
    
    /**
     * Test dictionary item not found exception with empty item code.
     */
    @Test
    void shouldHandleEmptyItemCode() {
        // Given & When
        DictionaryNotFoundException exception = new DictionaryNotFoundException("USER_TYPE", "");
        
        // Then
        assertThat(exception.getTypeCode()).isEqualTo("USER_TYPE");
        assertThat(exception.getItemCode()).isEqualTo("");
        assertThat(exception.getMessage()).isEqualTo("Dictionary item not found: typeCode=USER_TYPE, itemCode=");
    }
    
    /**
     * Test dictionary not found exception inheritance hierarchy.
     */
    @Test
    void shouldMaintainCorrectInheritanceHierarchy() {
        // Given
        DictionaryNotFoundException exception = new DictionaryNotFoundException("USER_TYPE");
        
        // Then
        assertThat(exception).isInstanceOf(DictionaryNotFoundException.class);
        assertThat(exception).isInstanceOf(RegistryNotFound.class);
        assertThat(exception).isInstanceOf(RegistryException.class);
        assertThat(exception).isInstanceOf(com.patra.common.error.DomainException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
    
    /**
     * Test dictionary not found exception error traits.
     */
    @Test
    void shouldHaveCorrectErrorTraits() {
        // Given
        DictionaryNotFoundException exception = new DictionaryNotFoundException("USER_TYPE");
        
        // When
        Set<ErrorTrait> traits = exception.getErrorTraits();
        
        // Then
        assertThat(traits).containsExactly(ErrorTrait.NOT_FOUND);
        assertThat(traits).isUnmodifiable();
    }
    
    /**
     * Test dictionary not found exception with whitespace type code.
     */
    @Test
    void shouldHandleWhitespaceTypeCode() {
        // Given & When
        DictionaryNotFoundException exception = new DictionaryNotFoundException("   ");
        
        // Then
        assertThat(exception.getTypeCode()).isEqualTo("   ");
        assertThat(exception.getItemCode()).isNull();
        assertThat(exception.getMessage()).isEqualTo("Dictionary type not found:    ");
    }
    
    /**
     * Test dictionary item not found exception with whitespace item code.
     */
    @Test
    void shouldHandleWhitespaceItemCode() {
        // Given & When
        DictionaryNotFoundException exception = new DictionaryNotFoundException("USER_TYPE", "   ");
        
        // Then
        assertThat(exception.getTypeCode()).isEqualTo("USER_TYPE");
        assertThat(exception.getItemCode()).isEqualTo("   ");
        assertThat(exception.getMessage()).isEqualTo("Dictionary item not found: typeCode=USER_TYPE, itemCode=   ");
    }
}