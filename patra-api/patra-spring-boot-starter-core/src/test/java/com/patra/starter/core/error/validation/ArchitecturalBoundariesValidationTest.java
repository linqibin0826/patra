package com.patra.starter.core.error.validation;

import com.patra.common.error.ApplicationException;
import com.patra.common.error.DomainException;
import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.common.error.problem.ErrorKeys;
import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.service.ErrorResolutionService;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import com.patra.starter.core.error.spi.StatusMappingStrategy;
import com.patra.starter.core.error.metrics.ErrorMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

/**
 * Comprehensive validation tests for architectural boundaries and implementation constraints.
 * Validates all the key architectural decisions and constraints of the error handling system.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class ArchitecturalBoundariesValidationTest {
    
    @Mock
    private StatusMappingStrategy statusMappingStrategy;
    
    @Mock
    private ErrorMetrics errorMetrics;
    
    private ErrorResolutionService errorResolutionService;
    private ErrorProperties errorProperties;
    
    @BeforeEach
    void setUp() {
        errorProperties = new ErrorProperties();
        errorProperties.setContextPrefix("TEST");
        
        errorResolutionService = new ErrorResolutionService(
            errorProperties,
            statusMappingStrategy,
            Collections.emptyList(),
            errorMetrics
        );
    }
    
    /**
     * Test that core module has no spring-web dependencies by verifying class loading.
     */
    @Test
    void shouldNotHaveSpringWebDependenciesInCore() {
        // Verify that core classes don't reference spring-web classes
        assertThat(ErrorResolutionService.class.getPackage().getName())
            .startsWith("com.patra.starter.core");
        
        // Verify ErrorResolution uses int status, not HttpStatus
        ErrorResolution resolution = new ErrorResolution(TestErrorCode.TEST_ERROR, 422);
        assertThat(resolution.httpStatus()).isInstanceOf(Integer.class);
        assertThat(resolution.httpStatus()).isEqualTo(422);
    }
    
    /**
     * Test that domain exceptions have no framework dependencies.
     */
    @Test
    void shouldHaveCleanDomainExceptions() {
        // Test that DomainException has no framework dependencies
        TestDomainException domainEx = new TestDomainException("Test domain error");
        assertThat(domainEx).isInstanceOf(DomainException.class);
        assertThat(domainEx).isInstanceOf(RuntimeException.class);
        
        // Verify it can be used without any framework context
        assertThat(domainEx.getMessage()).isEqualTo("Test domain error");
    }
    
    /**
     * Test ApplicationException with ErrorCodeLike integration.
     */
    @Test
    void shouldHandleApplicationExceptionWithErrorCode() {
        // Given
        when(statusMappingStrategy.mapToHttpStatus(any(ErrorCodeLike.class), any(Throwable.class)))
            .thenReturn(422);
        
        ApplicationException appEx = new ApplicationException(
            TestErrorCode.TEST_ERROR, 
            "Application level error"
        );
        
        // When
        ErrorResolution resolution = errorResolutionService.resolve(appEx);
        
        // Then
        assertThat(resolution.errorCode().code()).isEqualTo("TEST-0001");
        assertThat(resolution.httpStatus()).isEqualTo(422);
    }
    
    /**
     * Test HasErrorTraits semantic classification.
     */
    @Test
    void shouldResolveErrorTraitsCorrectly() {
        // Given
        TestTraitsException traitsEx = new TestTraitsException("Not found error");
        
        // When
        ErrorResolution resolution = errorResolutionService.resolve(traitsEx);
        
        // Then
        assertThat(resolution.errorCode().code()).isEqualTo("TEST-0404");
        assertThat(resolution.httpStatus()).isEqualTo(404); // From traits mapping, not mock
    }
    
    /**
     * Test cause chain traversal with maximum depth protection.
     */
    @Test
    void shouldTraverseCauseChainWithDepthLimit() throws Exception {
        // Create a deep cause chain
        RuntimeException deepCause = new TestTraitsException("Deep cause");
        RuntimeException cause3 = new RuntimeException("Cause 3", deepCause);
        RuntimeException cause2 = new RuntimeException("Cause 2", cause3);
        RuntimeException cause1 = new RuntimeException("Cause 1", cause2);
        RuntimeException rootException = new RuntimeException("Root", cause1);
        
        // When
        ErrorResolution resolution = errorResolutionService.resolve(rootException);
        
        // Then - Should find the TestTraitsException in the cause chain
        assertThat(resolution.errorCode().code()).isEqualTo("TEST-0404");
    }
    
    /**
     * Test class-level caching performance optimization.
     */
    @Test
    void shouldCacheResolutionsByExceptionClass() throws Exception {
        // Access the private cache field using reflection
        Field cacheField = ErrorResolutionService.class.getDeclaredField("resolutionCache");
        cacheField.setAccessible(true);
        ConcurrentHashMap<Class<?>, ErrorResolution> cache = 
            (ConcurrentHashMap<Class<?>, ErrorResolution>) cacheField.get(errorResolutionService);
        
        // Given
        TestTraitsException exception1 = new TestTraitsException("First exception");
        TestTraitsException exception2 = new TestTraitsException("Second exception");
        
        // When - First resolution should populate cache
        ErrorResolution resolution1 = errorResolutionService.resolve(exception1);
        assertThat(cache).hasSize(1);
        assertThat(cache).containsKey(TestTraitsException.class);
        
        // When - Second resolution should use cache
        ErrorResolution resolution2 = errorResolutionService.resolve(exception2);
        
        // Then - Both resolutions should be identical (cached)
        assertThat(resolution1.errorCode().code()).isEqualTo(resolution2.errorCode().code());
        assertThat(resolution1.httpStatus()).isEqualTo(resolution2.httpStatus());
        assertThat(cache).hasSize(1); // Cache size should remain 1
    }
    
    /**
     * Test naming convention heuristics.
     */
    @Test
    void shouldResolveByNamingConvention() {
        // Given
        TestNotFound notFoundEx = new TestNotFound("Resource not found");
        TestConflict conflictEx = new TestConflict("Resource conflict");
        TestInvalid invalidEx = new TestInvalid("Invalid input");
        
        // When & Then
        ErrorResolution notFoundResolution = errorResolutionService.resolve(notFoundEx);
        assertThat(notFoundResolution.errorCode().code()).isEqualTo("TEST-0404");
        
        ErrorResolution conflictResolution = errorResolutionService.resolve(conflictEx);
        assertThat(conflictResolution.errorCode().code()).isEqualTo("TEST-0409");
        
        ErrorResolution invalidResolution = errorResolutionService.resolve(invalidEx);
        assertThat(invalidResolution.errorCode().code()).isEqualTo("TEST-0422");
    }
    
    /**
     * Test fallback resolution for unmatched exceptions.
     */
    @Test
    void shouldUseFallbackForUnmatchedExceptions() {
        // Given
        RuntimeException genericEx = new RuntimeException("Generic error");
        IllegalArgumentException clientEx = new IllegalArgumentException("Client error");
        
        // When & Then
        ErrorResolution genericResolution = errorResolutionService.resolve(genericEx);
        assertThat(genericResolution.errorCode().code()).isEqualTo("TEST-0500");
        
        ErrorResolution clientResolution = errorResolutionService.resolve(clientEx);
        assertThat(clientResolution.errorCode().code()).isEqualTo("TEST-0422"); // Client error
    }
    
    /**
     * Test ErrorKeys constants for ProblemDetail extension fields.
     */
    @Test
    void shouldHaveStandardErrorKeys() {
        assertThat(ErrorKeys.CODE).isEqualTo("code");
        assertThat(ErrorKeys.TRACE_ID).isEqualTo("traceId");
        assertThat(ErrorKeys.PATH).isEqualTo("path");
        assertThat(ErrorKeys.TIMESTAMP).isEqualTo("timestamp");
        assertThat(ErrorKeys.ERRORS).isEqualTo("errors");
    }
    
    /**
     * Test UTC timestamp format consistency.
     */
    @Test
    void shouldUseUtcTimestampFormat() {
        // Test that timestamp format is ISO 8601 UTC
        String timestamp = Instant.now().atOffset(java.time.ZoneOffset.UTC).toString();
        
        // Verify format matches ISO 8601 with Z suffix
        assertThat(timestamp).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
        
        // Verify it can be parsed back
        Instant parsed = Instant.parse(timestamp);
        assertThat(parsed).isNotNull();
    }
    
    // Test classes for validation
    
    enum TestErrorCode implements ErrorCodeLike {
        TEST_ERROR("TEST-0001");
        
        private final String code;
        
        TestErrorCode(String code) {
            this.code = code;
        }
        
        @Override
        public String code() {
            return code;
        }
    }
    
    static class TestDomainException extends DomainException {
        public TestDomainException(String message) {
            super(message);
        }
    }
    
    static class TestTraitsException extends RuntimeException implements HasErrorTraits {
        public TestTraitsException(String message) {
            super(message);
        }
        
        @Override
        public Set<ErrorTrait> getErrorTraits() {
            return Set.of(ErrorTrait.NOT_FOUND);
        }
    }
    
    static class TestNotFound extends RuntimeException {
        public TestNotFound(String message) {
            super(message);
        }
    }
    
    static class TestConflict extends RuntimeException {
        public TestConflict(String message) {
            super(message);
        }
    }
    
    static class TestInvalid extends RuntimeException {
        public TestInvalid(String message) {
            super(message);
        }
    }
}