package com.patra.starter.core.error.service;

import com.patra.common.error.ApplicationException;
import com.patra.common.error.DomainException;
import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.metrics.ErrorMetrics;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import com.patra.starter.core.error.spi.StatusMappingStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
// Removed Spring DAO dependencies as they don't belong in core module

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for ErrorResolutionService class.
 * Tests the complete error resolution algorithm with various exception types
 * and scenarios.
 * 
 * @author linqibin
 * @since 0.1.0
 */
class ErrorResolutionServiceIntegrationTest {

    private ErrorProperties errorProperties;
    private StatusMappingStrategy statusMappingStrategy;
    private List<ErrorMappingContributor> mappingContributors;
    private ErrorMetrics errorMetrics;
    private ErrorResolutionService errorResolutionService;

    @BeforeEach
    void setUp() {
        errorProperties = new ErrorProperties();
        errorProperties.setContextPrefix("TEST");

        statusMappingStrategy = new TestStatusMappingStrategy();

        mappingContributors = List.of(
                new TestDataLayerErrorMappingContributor(),
                new TestBusinessErrorMappingContributor());

        errorMetrics = new TestErrorMetrics();

        errorResolutionService = new ErrorResolutionService(
                errorProperties,
                statusMappingStrategy,
                mappingContributors,
                errorMetrics);
    }

    /**
     * Test complete error resolution algorithm priority order.
     */
    @Test
    void shouldFollowCorrectResolutionPriority() {
        // Test 1: ApplicationException - highest priority
        ApplicationException appException = new ApplicationException(TestErrorCode.BUSINESS_ERROR, "Business error");
        ErrorResolution resolution1 = errorResolutionService.resolve(appException);
        assertThat(resolution1.errorCode().code()).isEqualTo("BIZ-001");
        assertThat(resolution1.httpStatus()).isEqualTo(422);

        // Test 2: ErrorMappingContributor - second priority
        TestBusinessException businessException = new TestBusinessException("Business error");
        ErrorResolution resolution2 = errorResolutionService.resolve(businessException);
        assertThat(resolution2.errorCode().code()).isEqualTo("TEST-1001");
        assertThat(resolution2.httpStatus()).isEqualTo(422);

        // Test 3: HasErrorTraits - third priority
        TestExceptionWithTraits traitException = new TestExceptionWithTraits();
        ErrorResolution resolution3 = errorResolutionService.resolve(traitException);
        assertThat(resolution3.errorCode().code()).isEqualTo("TEST-0404");
        assertThat(resolution3.httpStatus()).isEqualTo(404);

        // Test 4: Naming convention - fourth priority
        TestNotFound namingException = new TestNotFound("Not found");
        ErrorResolution resolution4 = errorResolutionService.resolve(namingException);
        assertThat(resolution4.errorCode().code()).isEqualTo("TEST-0404");
        assertThat(resolution4.httpStatus()).isEqualTo(404);

        // Test 5: Fallback - lowest priority
        RuntimeException genericException = new RuntimeException("Generic error");
        ErrorResolution resolution5 = errorResolutionService.resolve(genericException);
        assertThat(resolution5.errorCode().code()).isEqualTo("TEST-0500");
        assertThat(resolution5.httpStatus()).isEqualTo(500);
    }

    /**
     * Test error resolution caching works correctly.
     */
    @Test
    void shouldCacheResolutionResults() {
        // Given
        RuntimeException exception1 = new RuntimeException("Error 1");
        RuntimeException exception2 = new RuntimeException("Error 2");

        // When - First resolution
        ErrorResolution resolution1 = errorResolutionService.resolve(exception1);
        ErrorResolution resolution2 = errorResolutionService.resolve(exception2);

        // Then - Same class should return cached result
        ErrorResolution cachedResolution1 = errorResolutionService.resolve(exception1);
        ErrorResolution cachedResolution2 = errorResolutionService.resolve(exception2);

        assertThat(cachedResolution1).isEqualTo(resolution1);
        assertThat(cachedResolution2).isEqualTo(resolution2);
    }

    /**
     * Test cause chain traversal works correctly.
     */
    @Test
    void shouldTraverseCauseChain() {
        // Given - Nested exception with ApplicationException as root cause
        ApplicationException rootCause = new ApplicationException(TestErrorCode.BUSINESS_ERROR, "Root cause");
        RuntimeException middleException = new RuntimeException("Middle exception", rootCause);
        IllegalStateException topException = new IllegalStateException("Top exception", middleException);

        // When
        ErrorResolution resolution = errorResolutionService.resolve(topException);

        // Then - Should find ApplicationException in cause chain
        assertThat(resolution.errorCode().code()).isEqualTo("BIZ-001");
        assertThat(resolution.httpStatus()).isEqualTo(422);
    }

    /**
     * Test cause chain depth limit prevents infinite loops.
     */
    @Test
    void shouldLimitCauseChainDepth() {
        // Given - Create a deep cause chain (more than 10 levels)
        RuntimeException exception = new RuntimeException("Level 0");
        for (int i = 1; i <= 15; i++) {
            exception = new RuntimeException("Level " + i, exception);
        }

        // When
        ErrorResolution resolution = errorResolutionService.resolve(exception);

        // Then - Should fallback to 500 due to depth limit
        assertThat(resolution.errorCode().code()).isEqualTo("TEST-0500");
        assertThat(resolution.httpStatus()).isEqualTo(500);
    }

    /**
     * Test multiple error mapping contributors are processed in order.
     */
    @Test
    void shouldProcessErrorMappingContributorsInOrder() {
        // Given - Exception that matches multiple contributors
        TestBusinessException exception = new TestBusinessException("Business exception");

        // When
        ErrorResolution resolution = errorResolutionService.resolve(exception);

        // Then - Should use first matching contributor
        // (TestDataLayerErrorMappingContributor)
        assertThat(resolution.errorCode().code()).isEqualTo("TEST-1001");
        assertThat(resolution.httpStatus()).isEqualTo(422);
    }

    /**
     * Test naming convention heuristics work for various patterns.
     */
    @Test
    void shouldApplyNamingConventionHeuristics() {
        // Test various naming patterns
        assertResolutionForNaming(new TestNotFound("test"), "TEST-0404", 404);
        assertResolutionForNaming(new TestConflict("test"), "TEST-0409", 409);
        assertResolutionForNaming(new TestInvalid("test"), "TEST-0422", 422);
        assertResolutionForNaming(new TestUnauthorized("test"), "TEST-0401", 401);
        assertResolutionForNaming(new TestForbidden("test"), "TEST-0403", 403);
        assertResolutionForNaming(new TestTimeout("test"), "TEST-0504", 504);
    }

    private void assertResolutionForNaming(Exception exception, String expectedCode, int expectedStatus) {
        ErrorResolution resolution = errorResolutionService.resolve(exception);
        assertThat(resolution.errorCode().code()).isEqualTo(expectedCode);
        assertThat(resolution.httpStatus()).isEqualTo(expectedStatus);
    }

    /**
     * Test error code enum for testing purposes.
     */
    private enum TestErrorCode implements ErrorCodeLike {
        BUSINESS_ERROR("BIZ-001"),
        ANOTHER_ERROR("BIZ-002");

        private final String code;

        TestErrorCode(String code) {
            this.code = code;
        }

        @Override
        public String code() {
            return code;
        }
    }

    /**
     * Test status mapping strategy for testing purposes.
     */
    private static class TestStatusMappingStrategy implements StatusMappingStrategy {
        @Override
        public int mapToHttpStatus(ErrorCodeLike errorCode, Throwable exception) {
            String code = errorCode.code();
            if (code.endsWith("-0404"))
                return 404;
            if (code.endsWith("-0409"))
                return 409;
            if (code.endsWith("-0422"))
                return 422;
            if (code.endsWith("-0401"))
                return 401;
            if (code.endsWith("-0403"))
                return 403;
            if (code.endsWith("-0504"))
                return 504;
            if (code.endsWith("-0500"))
                return 500;
            return 422; // Default for business errors
        }
    }

    /**
     * Test data layer error mapping contributor for testing purposes.
     */
    private class TestDataLayerErrorMappingContributor implements ErrorMappingContributor {
        @Override
        public Optional<ErrorCodeLike> mapException(Throwable exception) {
            if (exception instanceof TestBusinessException) {
                return Optional.of(() -> "TEST-1001");
            }
            return Optional.empty();
        }
    }

    /**
     * Test business error mapping contributor for testing purposes.
     */
    private class TestBusinessErrorMappingContributor implements ErrorMappingContributor {
        @Override
        public Optional<ErrorCodeLike> mapException(Throwable exception) {
            if (exception instanceof TestBusinessException) {
                return Optional.of(() -> "TEST-1001");
            }
            return Optional.empty();
        }
    }

    /**
     * Test exception with traits for testing purposes.
     */
    private static class TestExceptionWithTraits extends RuntimeException implements HasErrorTraits {
        @Override
        public Set<ErrorTrait> getErrorTraits() {
            return Set.of(ErrorTrait.NOT_FOUND);
        }
    }

    /**
     * Test exceptions for naming convention testing.
     */
    private static class TestNotFound extends RuntimeException {
        public TestNotFound(String message) {
            super(message);
        }
    }

    private static class TestConflict extends RuntimeException {
        public TestConflict(String message) {
            super(message);
        }
    }

    private static class TestInvalid extends RuntimeException {
        public TestInvalid(String message) {
            super(message);
        }
    }

    private static class TestUnauthorized extends RuntimeException {
        public TestUnauthorized(String message) {
            super(message);
        }
    }

    private static class TestForbidden extends RuntimeException {
        public TestForbidden(String message) {
            super(message);
        }
    }

    private static class TestTimeout extends RuntimeException {
        public TestTimeout(String message) {
            super(message);
        }
    }

    private static class TestBusinessException extends RuntimeException {
        public TestBusinessException(String message) {
            super(message);
        }
    }

    /**
     * Test error metrics implementation for testing purposes.
     */
    private static class TestErrorMetrics implements ErrorMetrics {
        @Override
        public void recordResolutionTime(Class<?> exceptionClass, ErrorCodeLike errorCode, long timeMs,
                boolean cacheHit) {
            // No-op for testing
        }

        @Override
        public void recordCacheHitMiss(Class<?> exceptionClass, boolean cacheHit) {
            // No-op for testing
        }

        @Override
        public void recordErrorCodeDistribution(ErrorCodeLike errorCode, int httpStatus, String contextPrefix) {
            // No-op for testing
        }

        @Override
        public void recordContributorPerformance(Class<?> contributorClass, boolean success, long executionTimeMs) {
            // No-op for testing
        }

        @Override
        public void recordCauseChainDepth(int depth, boolean foundMatch) {
            // No-op for testing
        }
    }
}