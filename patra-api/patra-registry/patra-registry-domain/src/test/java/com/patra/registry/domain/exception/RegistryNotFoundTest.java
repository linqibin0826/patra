package com.patra.registry.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RegistryNotFound semantic base exception.
 * 
 * @author linqibin
 * @since 0.1.0
 */
class RegistryNotFoundTest {
    
    /**
     * Test registry not found exception with message constructor.
     */
    @Test
    void shouldCreateRegistryNotFoundWithMessage() {
        // Given
        String message = "Test registry resource not found";
        
        // When
        TestRegistryNotFound exception = new TestRegistryNotFound(message);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
    }
    
    /**
     * Test registry not found exception with message and cause constructor.
     */
    @Test
    void shouldCreateRegistryNotFoundWithMessageAndCause() {
        // Given
        String message = "Test registry resource not found";
        RuntimeException cause = new RuntimeException("Root cause");
        
        // When
        TestRegistryNotFound exception = new TestRegistryNotFound(message, cause);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
    }
    
    /**
     * Test registry not found exception inheritance hierarchy.
     */
    @Test
    void shouldMaintainCorrectInheritanceHierarchy() {
        // Given
        TestRegistryNotFound exception = new TestRegistryNotFound("test");
        
        // Then
        assertThat(exception).isInstanceOf(RegistryNotFound.class);
        assertThat(exception).isInstanceOf(RegistryException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
    
    /**
     * Test registry not found exception error traits.
     */
    @Test
    void shouldHaveCorrectErrorTraits() {
        // Given
        TestRegistryNotFound exception = new TestRegistryNotFound("test");
        
        // When
        Set<ErrorTrait> traits = exception.getErrorTraits();
        
        // Then
        assertThat(traits).containsExactly(ErrorTrait.NOT_FOUND);
        assertThat(traits).isUnmodifiable();
    }
    
    /**
     * Test concrete implementation of RegistryNotFound for testing purposes.
     */
    private static class TestRegistryNotFound extends RegistryNotFound {
        public TestRegistryNotFound(String message) {
            super(message);
        }
        
        public TestRegistryNotFound(String message, Throwable cause) {
            super(message, cause);
        }
    }
}