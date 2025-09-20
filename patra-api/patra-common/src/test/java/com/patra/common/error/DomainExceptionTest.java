package com.patra.common.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DomainException base class.
 * 
 * @author linqibin
 * @since 0.1.0
 */
class DomainExceptionTest {
    
    /**
     * Test domain exception with message constructor.
     */
    @Test
    void shouldCreateDomainExceptionWithMessage() {
        // Given
        String message = "Test domain exception message";
        
        // When
        TestDomainException exception = new TestDomainException(message);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isNull();
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
    
    /**
     * Test domain exception with message and cause constructor.
     */
    @Test
    void shouldCreateDomainExceptionWithMessageAndCause() {
        // Given
        String message = "Test domain exception message";
        RuntimeException cause = new RuntimeException("Root cause");
        
        // When
        TestDomainException exception = new TestDomainException(message, cause);
        
        // Then
        assertThat(exception.getMessage()).isEqualTo(message);
        assertThat(exception.getCause()).isEqualTo(cause);
        assertThat(exception).isInstanceOf(RuntimeException.class);
    }
    
    /**
     * Test domain exception inheritance hierarchy.
     */
    @Test
    void shouldMaintainInheritanceHierarchy() {
        // Given
        TestDomainException exception = new TestDomainException("test");
        
        // Then
        assertThat(exception).isInstanceOf(DomainException.class);
        assertThat(exception).isInstanceOf(RuntimeException.class);
        assertThat(exception).isInstanceOf(Exception.class);
        assertThat(exception).isInstanceOf(Throwable.class);
    }
    
    /**
     * Test concrete implementation of DomainException for testing purposes.
     */
    private static class TestDomainException extends DomainException {
        public TestDomainException(String message) {
            super(message);
        }
        
        public TestDomainException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}