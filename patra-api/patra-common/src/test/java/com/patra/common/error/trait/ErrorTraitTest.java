package com.patra.common.error.trait;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for ErrorTrait enum and HasErrorTraits interface.
 * 
 * @author linqibin
 * @since 0.1.0
 */
class ErrorTraitTest {
    
    /**
     * Test all error traits are defined correctly.
     */
    @Test
    void shouldHaveAllExpectedErrorTraits() {
        // Given & When
        ErrorTrait[] traits = ErrorTrait.values();
        
        // Then
        assertThat(traits).containsExactlyInAnyOrder(
            ErrorTrait.NOT_FOUND,
            ErrorTrait.CONFLICT,
            ErrorTrait.RULE_VIOLATION,
            ErrorTrait.QUOTA_EXCEEDED,
            ErrorTrait.UNAUTHORIZED,
            ErrorTrait.FORBIDDEN,
            ErrorTrait.TIMEOUT,
            ErrorTrait.DEP_UNAVAILABLE
        );
    }
    
    /**
     * Test error trait enum values.
     */
    @Test
    void shouldHaveCorrectErrorTraitValues() {
        // Then
        assertThat(ErrorTrait.NOT_FOUND.name()).isEqualTo("NOT_FOUND");
        assertThat(ErrorTrait.CONFLICT.name()).isEqualTo("CONFLICT");
        assertThat(ErrorTrait.RULE_VIOLATION.name()).isEqualTo("RULE_VIOLATION");
        assertThat(ErrorTrait.QUOTA_EXCEEDED.name()).isEqualTo("QUOTA_EXCEEDED");
        assertThat(ErrorTrait.UNAUTHORIZED.name()).isEqualTo("UNAUTHORIZED");
        assertThat(ErrorTrait.FORBIDDEN.name()).isEqualTo("FORBIDDEN");
        assertThat(ErrorTrait.TIMEOUT.name()).isEqualTo("TIMEOUT");
        assertThat(ErrorTrait.DEP_UNAVAILABLE.name()).isEqualTo("DEP_UNAVAILABLE");
    }
    
    /**
     * Test HasErrorTraits implementation.
     */
    @Test
    void shouldImplementHasErrorTraitsCorrectly() {
        // Given
        TestExceptionWithTraits exception = new TestExceptionWithTraits();
        
        // When
        Set<ErrorTrait> traits = exception.getErrorTraits();
        
        // Then
        assertThat(traits).containsExactlyInAnyOrder(
            ErrorTrait.NOT_FOUND,
            ErrorTrait.RULE_VIOLATION
        );
        assertThat(traits).isUnmodifiable();
    }
    
    /**
     * Test single trait implementation.
     */
    @Test
    void shouldImplementSingleTraitCorrectly() {
        // Given
        TestExceptionWithSingleTrait exception = new TestExceptionWithSingleTrait();
        
        // When
        Set<ErrorTrait> traits = exception.getErrorTraits();
        
        // Then
        assertThat(traits).containsExactly(ErrorTrait.CONFLICT);
        assertThat(traits).isUnmodifiable();
    }
    
    /**
     * Test empty traits implementation.
     */
    @Test
    void shouldImplementEmptyTraitsCorrectly() {
        // Given
        TestExceptionWithNoTraits exception = new TestExceptionWithNoTraits();
        
        // When
        Set<ErrorTrait> traits = exception.getErrorTraits();
        
        // Then
        assertThat(traits).isEmpty();
        assertThat(traits).isUnmodifiable();
    }
    
    /**
     * Test exception with multiple traits for testing purposes.
     */
    private static class TestExceptionWithTraits implements HasErrorTraits {
        @Override
        public Set<ErrorTrait> getErrorTraits() {
            return Set.of(ErrorTrait.NOT_FOUND, ErrorTrait.RULE_VIOLATION);
        }
    }
    
    /**
     * Test exception with single trait for testing purposes.
     */
    private static class TestExceptionWithSingleTrait implements HasErrorTraits {
        @Override
        public Set<ErrorTrait> getErrorTraits() {
            return Set.of(ErrorTrait.CONFLICT);
        }
    }
    
    /**
     * Test exception with no traits for testing purposes.
     */
    private static class TestExceptionWithNoTraits implements HasErrorTraits {
        @Override
        public Set<ErrorTrait> getErrorTraits() {
            return Set.of();
        }
    }
}