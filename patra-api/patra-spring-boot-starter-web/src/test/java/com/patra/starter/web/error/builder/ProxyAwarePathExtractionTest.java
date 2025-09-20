package com.patra.starter.web.error.builder;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.common.error.problem.ErrorKeys;
import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.spi.ProblemFieldContributor;
import com.patra.starter.core.error.spi.TraceProvider;
import com.patra.starter.web.error.config.WebErrorProperties;
import com.patra.starter.web.error.spi.WebProblemFieldContributor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for proxy-aware path extraction in ProblemDetailBuilder.
 * Validates that various proxy headers are handled correctly with proper priority.
 * 
 * @author linqibin
 * @since 0.1.0
 */
class ProxyAwarePathExtractionTest {
    
    private ProblemDetailBuilder problemDetailBuilder;
    private ErrorProperties errorProperties;
    private WebErrorProperties webProperties;
    private TraceProvider traceProvider;
    
    @BeforeEach
    void setUp() {
        errorProperties = new ErrorProperties();
        errorProperties.setContextPrefix("TEST");
        
        webProperties = new WebErrorProperties();
        webProperties.setTypeBaseUrl("https://test.errors.com/");
        
        traceProvider = () -> Optional.empty();
        
        problemDetailBuilder = new ProblemDetailBuilder(
            errorProperties,
            webProperties,
            traceProvider,
            Collections.<ProblemFieldContributor>emptyList(),
            Collections.<WebProblemFieldContributor>emptyList()
        );
    }
    
    /**
     * Test standard Forwarded header takes highest priority.
     */
    @Test
    void shouldExtractPathFromForwardedHeader() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/original/path");
        request.addHeader("Forwarded", "for=192.168.1.1; proto=https; host=example.com; path=\"/api/v1/test\"");
        request.addHeader("X-Forwarded-Path", "/x-forwarded/path");
        request.addHeader("X-Forwarded-Uri", "/x-forwarded/uri");
        
        ErrorResolution resolution = new ErrorResolution(TestErrorCode.TEST_ERROR, 404);
        RuntimeException exception = new RuntimeException("Test exception");
        
        // When
        ProblemDetail problemDetail = problemDetailBuilder.build(resolution, exception, request);
        
        // Then
        assertThat(problemDetail.getProperties().get(ErrorKeys.PATH)).isEqualTo("/api/v1/test");
    }
    
    /**
     * Test X-Forwarded-Path header when Forwarded is not present.
     */
    @Test
    void shouldExtractPathFromXForwardedPathHeader() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/original/path");
        request.addHeader("X-Forwarded-Path", "/x-forwarded/path");
        request.addHeader("X-Forwarded-Uri", "/x-forwarded/uri");
        
        ErrorResolution resolution = new ErrorResolution(TestErrorCode.TEST_ERROR, 404);
        RuntimeException exception = new RuntimeException("Test exception");
        
        // When
        ProblemDetail problemDetail = problemDetailBuilder.build(resolution, exception, request);
        
        // Then
        assertThat(problemDetail.getProperties().get(ErrorKeys.PATH)).isEqualTo("/x-forwarded/path");
    }
    
    /**
     * Test X-Forwarded-Uri header when Forwarded and X-Forwarded-Path are not present.
     */
    @Test
    void shouldExtractPathFromXForwardedUriHeader() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/original/path");
        request.addHeader("X-Forwarded-Uri", "/x-forwarded/uri");
        
        ErrorResolution resolution = new ErrorResolution(TestErrorCode.TEST_ERROR, 404);
        RuntimeException exception = new RuntimeException("Test exception");
        
        // When
        ProblemDetail problemDetail = problemDetailBuilder.build(resolution, exception, request);
        
        // Then
        assertThat(problemDetail.getProperties().get(ErrorKeys.PATH)).isEqualTo("/x-forwarded/uri");
    }
    
    /**
     * Test fallback to request URI when no proxy headers are present.
     */
    @Test
    void shouldFallbackToRequestUriWhenNoProxyHeaders() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/original/path");
        
        ErrorResolution resolution = new ErrorResolution(TestErrorCode.TEST_ERROR, 404);
        RuntimeException exception = new RuntimeException("Test exception");
        
        // When
        ProblemDetail problemDetail = problemDetailBuilder.build(resolution, exception, request);
        
        // Then
        assertThat(problemDetail.getProperties().get(ErrorKeys.PATH)).isEqualTo("/original/path");
    }
    
    /**
     * Test Forwarded header with quoted path value.
     */
    @Test
    void shouldHandleQuotedPathInForwardedHeader() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/original/path");
        request.addHeader("Forwarded", "for=192.168.1.1; path=\"/quoted/path\"");
        
        ErrorResolution resolution = new ErrorResolution(TestErrorCode.TEST_ERROR, 404);
        RuntimeException exception = new RuntimeException("Test exception");
        
        // When
        ProblemDetail problemDetail = problemDetailBuilder.build(resolution, exception, request);
        
        // Then
        assertThat(problemDetail.getProperties().get(ErrorKeys.PATH)).isEqualTo("/quoted/path");
    }
    
    /**
     * Test Forwarded header without path falls back to X-Forwarded-Path.
     */
    @Test
    void shouldFallbackWhenForwardedHeaderHasNoPath() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/original/path");
        request.addHeader("Forwarded", "for=192.168.1.1; proto=https; host=example.com");
        request.addHeader("X-Forwarded-Path", "/x-forwarded/path");
        
        ErrorResolution resolution = new ErrorResolution(TestErrorCode.TEST_ERROR, 404);
        RuntimeException exception = new RuntimeException("Test exception");
        
        // When
        ProblemDetail problemDetail = problemDetailBuilder.build(resolution, exception, request);
        
        // Then
        assertThat(problemDetail.getProperties().get(ErrorKeys.PATH)).isEqualTo("/x-forwarded/path");
    }
    
    /**
     * Test empty header values are ignored.
     */
    @Test
    void shouldIgnoreEmptyHeaderValues() {
        // Given
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/original/path");
        request.addHeader("Forwarded", "");
        request.addHeader("X-Forwarded-Path", "");
        request.addHeader("X-Forwarded-Uri", "/x-forwarded/uri");
        
        ErrorResolution resolution = new ErrorResolution(TestErrorCode.TEST_ERROR, 404);
        RuntimeException exception = new RuntimeException("Test exception");
        
        // When
        ProblemDetail problemDetail = problemDetailBuilder.build(resolution, exception, request);
        
        // Then
        assertThat(problemDetail.getProperties().get(ErrorKeys.PATH)).isEqualTo("/x-forwarded/uri");
    }
    
    /**
     * Test error codes for testing purposes.
     */
    enum TestErrorCode implements ErrorCodeLike {
        TEST_ERROR("TEST-0404");
        
        private final String code;
        
        TestErrorCode(String code) {
            this.code = code;
        }
        
        @Override
        public String code() {
            return code;
        }
    }
}