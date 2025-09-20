package com.patra.starter.core.error.service;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import com.patra.common.error.ApplicationException;
import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.metrics.ErrorMetrics;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import com.patra.starter.core.error.spi.StatusMappingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ErrorResolutionService.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class ErrorResolutionServiceTest {
    
    @Mock
    private StatusMappingStrategy statusMappingStrategy;
    
    @Mock
    private ErrorMappingContributor errorMappingContributor;
    
    @Mock
    private ErrorMetrics errorMetrics;
    
    private ErrorProperties errorProperties;
    private ErrorResolutionService errorResolutionService;
    
    @BeforeEach
    void setUp() {
        errorProperties = new ErrorProperties();
        errorProperties.setContextPrefix("TEST");
        
        errorResolutionService = new ErrorResolutionService(
            errorProperties,
            statusMappingStrategy,
            List.of(errorMappingContributor),
            errorMetrics
        );
    }
    
    /**
     * Test error resolution for ApplicationException - highest priority.
     */
    @Test
    void shouldResolveApplicationExceptionDirectly() {
        // Given
        TestErrorCode errorCode = TestErrorCode.TEST_ERROR;
        ApplicationException exception = new ApplicationException(errorCode, "Test message");
        
        when(statusMappingStrategy.mapToHttpStatus(eq(errorCode), any()))
            .thenReturn(422);
        
        // When
        ErrorResolution resolution = errorResolutionService.resolve(exception);
        
        // Then
        assertThat(resolution.errorCode()).isEqualTo(errorCode);
        assertThat(resolution.httpStatus()).isEqualTo(422);
    }
    
    /**
     * Test error resolution using ErrorMappingContributor - second priority.
     */
    @Test
    void shouldResolveUsingErrorMappingContributor() {
        // Given
        RuntimeException exception = new RuntimeException("Test exception");
        TestErrorCode mappedCode = TestErrorCode.MAPPED_ERROR;
        
        when(errorMappingContributor.mapException(exception))
            .thenReturn(Optional.of(mappedCode));
        when(statusMappingStrategy.mapToHttpStatus(eq(mappedCode), any()))
            .thenReturn(409);
        
        // When
        ErrorResolution resolution = errorResolutionService.resolve(exception);
        
        // Then
        assertThat(resolution.errorCode().code()).isEqualTo("TEST-MAPPED");
        assertThat(resolution.httpStatus()).isEqualTo(409);
    }
    
    /**
     * Test error resolution using HasErrorTraits - third priority.
     */
    @Test
    void shouldResolveUsingErrorTraits() {
        // Given
        TestExceptionWithTraits exception = new TestExceptionWithTraits();
        
        when(errorMappingContributor.mapException(exception))
            .thenReturn(Optional.empty());
        
        // When
        ErrorResolution resolution = errorResolutionService.resolve(exception);
        
        // Then
        assertThat(resolution.errorCode().code()).isEqualTo("TEST-0404");
        assertThat(resolution.httpStatus()).isEqualTo(404);
    }
    
    /**
     * Test error resolution using naming convention - fourth priority.
     */
    @Test
    void shouldResolveUsingNamingConvention() {
        // Given
        ResourceNotFound exception = new ResourceNotFound("Resource not found");
        
        when(errorMappingContributor.mapException(exception))
            .thenReturn(Optional.empty());
        
        // When
        ErrorResolution resolution = errorResolutionService.resolve(exception);
        
        // Then
        assertThat(resolution.errorCode().code()).isEqualTo("TEST-0404");
        assertThat(resolution.httpStatus()).isEqualTo(404);
    }
    
    /**
     * Test error resolution using conflict naming convention.
     */
    @Test
    void shouldResolveConflictUsingNamingConvention() {
        // Given
        ResourceConflict exception = new ResourceConflict("Resource conflict");
        
        when(errorMappingContributor.mapException(exception))
            .thenReturn(Optional.empty());
        
        // When
        ErrorResolution resolution = errorResolutionService.resolve(exception);
        
        // Then
        assertThat(resolution.errorCode().code()).isEqualTo("TEST-0409");
        assertThat(resolution.httpStatus()).isEqualTo(409);
    }
    
    /**
     * Test error resolution using invalid naming convention.
     */
    @Test
    void shouldResolveInvalidUsingNamingConvention() {
        // Given
        InputInvalid exception = new InputInvalid("Invalid input");
        
        when(errorMappingContributor.mapException(exception))
            .thenReturn(Optional.empty());
        
        // When
        ErrorResolution resolution = errorResolutionService.resolve(exception);
        
        // Then
        assertThat(resolution.errorCode().code()).isEqualTo("TEST-0422");
        assertThat(resolution.httpStatus()).isEqualTo(422);
    }
    
    /**
     * Test error resolution fallback - lowest priority.
     */
    @Test
    void shouldUseFallbackResolution() {
        // Given
        RuntimeException exception = new RuntimeException("Unknown exception");
        
        when(errorMappingContributor.mapException(exception))
            .thenReturn(Optional.empty());
        
        // When
        ErrorResolution resolution = errorResolutionService.resolve(exception);
        
        // Then
        assertThat(resolution.errorCode().code()).isEqualTo("TEST-0500");
        assertThat(resolution.httpStatus()).isEqualTo(500);
    }
    
    /**
     * Test error resolution with cause chain traversal.
     */
    @Test
    void shouldTraverseCauseChain() {
        // Given
        TestErrorCode errorCode = TestErrorCode.TEST_ERROR;
        ApplicationException rootCause = new ApplicationException(errorCode, "Root cause");
        RuntimeException wrapper = new RuntimeException("Wrapper", rootCause);
        
        when(statusMappingStrategy.mapToHttpStatus(eq(errorCode), any()))
            .thenReturn(422);
        
        // When
        ErrorResolution resolution = errorResolutionService.resolve(wrapper);
        
        // Then
        assertThat(resolution.errorCode()).isEqualTo(errorCode);
        assertThat(resolution.httpStatus()).isEqualTo(422);
    }
    
    /**
     * Test error resolution caching.
     */
    @Test
    void shouldCacheResolutionResults() {
        // Given
        RuntimeException exception1 = new RuntimeException("Test exception");
        RuntimeException exception2 = new RuntimeException("Another test exception");
        TestErrorCode mappedCode = TestErrorCode.MAPPED_ERROR;
        
        when(errorMappingContributor.mapException(any()))
            .thenReturn(Optional.of(mappedCode));
        when(statusMappingStrategy.mapToHttpStatus(eq(mappedCode), any()))
            .thenReturn(409);
        
        // When
        ErrorResolution resolution1 = errorResolutionService.resolve(exception1);
        ErrorResolution resolution2 = errorResolutionService.resolve(exception2);
        
        // Then - Both should have same resolution due to same exception class
        assertThat(resolution1.errorCode().code()).isEqualTo("TEST-MAPPED");
        assertThat(resolution1.httpStatus()).isEqualTo(409);
        assertThat(resolution2.errorCode().code()).isEqualTo("TEST-MAPPED");
        assertThat(resolution2.httpStatus()).isEqualTo(409);
    }
    
    // Test helper classes
    
    private enum TestErrorCode implements ErrorCodeLike {
        TEST_ERROR("TEST-0001"),
        MAPPED_ERROR("TEST-MAPPED"),
        ANOTHER_ERROR("TEST-0002");
        
        private final String code;
        
        TestErrorCode(String code) {
            this.code = code;
        }
        
        @Override
        public String code() {
            return code;
        }
    }
    
    private static class TestExceptionWithTraits extends RuntimeException implements HasErrorTraits {
        @Override
        public Set<ErrorTrait> getErrorTraits() {
            return Set.of(ErrorTrait.NOT_FOUND);
        }
    }
    
    private static class ResourceNotFound extends RuntimeException {
        public ResourceNotFound(String message) {
            super(message);
        }
    }
    
    private static class ResourceConflict extends RuntimeException {
        public ResourceConflict(String message) {
            super(message);
        }
    }
    
    private static class InputInvalid extends RuntimeException {
        public InputInvalid(String message) {
            super(message);
        }
    }
}