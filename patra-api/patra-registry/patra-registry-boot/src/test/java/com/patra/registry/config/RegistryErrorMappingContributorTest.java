package com.patra.registry.config;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.registry.api.error.RegistryErrorCode;
import com.patra.registry.domain.exception.DictionaryItemDisabled;
import com.patra.registry.domain.exception.RegistryNotFound;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RegistryErrorMappingContributor class.
 * Tests the mapping of Registry domain exceptions to specific error codes.
 * 
 * @author linqibin
 * @since 0.1.0
 */
class RegistryErrorMappingContributorTest {
    
    private RegistryErrorMappingContributor contributor;
    
    @BeforeEach
    void setUp() {
        contributor = new RegistryErrorMappingContributor();
    }
    
    /**
     * Test mapping DictionaryItemDisabled to specific error code.
     */
    @Test
    void shouldMapDictionaryItemDisabledToSpecificErrorCode() {
        // Given
        DictionaryItemDisabled exception = new DictionaryItemDisabled("USER_TYPE", "ADMIN");
        
        // When
        Optional<ErrorCodeLike> result = contributor.mapException(exception);
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(RegistryErrorCode.REG_1403);
        assertThat(result.get().code()).isEqualTo("REG-1403");
    }
    
    /**
     * Test mapping concrete RegistryNotFound implementation.
     */
    @Test
    void shouldMapConcreteRegistryNotFoundException() {
        // Given
        TestRegistryNotFound exception = new TestRegistryNotFound("Test registry not found");
        
        // When
        Optional<ErrorCodeLike> result = contributor.mapException(exception);
        
        // Then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(RegistryErrorCode.REG_0404);
        assertThat(result.get().code()).isEqualTo("REG-0404");
    }
    
    /**
     * Test mapping non-Registry exception returns empty.
     */
    @Test
    void shouldReturnEmptyForNonRegistryException() {
        // Given
        RuntimeException exception = new RuntimeException("Generic runtime exception");
        
        // When
        Optional<ErrorCodeLike> result = contributor.mapException(exception);
        
        // Then
        assertThat(result).isEmpty();
    }
    
    /**
     * Test mapping null exception returns empty.
     */
    @Test
    void shouldReturnEmptyForNullException() {
        // Given
        Throwable exception = null;
        
        // When
        Optional<ErrorCodeLike> result = contributor.mapException(exception);
        
        // Then
        assertThat(result).isEmpty();
    }
    
    /**
     * Test mapping unknown Registry exception falls back to generic mapping.
     */
    @Test
    void shouldReturnEmptyForUnknownRegistryException() {
        // Given
        TestUnknownRegistryException exception = new TestUnknownRegistryException("Unknown registry exception");
        
        // When
        Optional<ErrorCodeLike> result = contributor.mapException(exception);
        
        // Then
        assertThat(result).isEmpty();
    }
    
    /**
     * Test concrete implementation of RegistryNotFound for testing purposes.
     */
    private static class TestRegistryNotFound extends RegistryNotFound {
        public TestRegistryNotFound(String message) {
            super(message);
        }
    }
    
    /**
     * Test unknown registry exception for testing purposes.
     */
    private static class TestUnknownRegistryException extends RuntimeException {
        public TestUnknownRegistryException(String message) {
            super(message);
        }
    }
}