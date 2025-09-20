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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

/**
 * Unit tests for ProblemDetailBuilder.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class ProblemDetailBuilderTest {
    
    @Mock
    private ErrorProperties errorProperties;
    
    @Mock
    private WebErrorProperties webErrorProperties;
    
    @Mock
    private TraceProvider traceProvider;
    
    @Mock
    private ProblemFieldContributor coreFieldContributor;
    
    @Mock
    private WebProblemFieldContributor webFieldContributor;
    
    private ProblemDetailBuilder problemDetailBuilder;
    
    @BeforeEach
    void setUp() {
        when(webErrorProperties.getTypeBaseUrl()).thenReturn("https://errors.example.com/");
        
        problemDetailBuilder = new ProblemDetailBuilder(
            errorProperties,
            webErrorProperties,
            traceProvider,
            List.of(coreFieldContributor),
            List.of(webFieldContributor)
        );
    }
    
    /**
     * Test building ProblemDetail with all standard fields.
     */
    @Test
    void shouldBuildProblemDetailWithStandardFields() {
        // Given
        TestErrorCode errorCode = TestErrorCode.TEST_ERROR;
        ErrorResolution resolution = new ErrorResolution(errorCode, 422);
        RuntimeException exception = new RuntimeException("Test exception message");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        
        when(traceProvider.getCurrentTraceId()).thenReturn(Optional.of("trace-123"));
        
        // When
        ProblemDetail problemDetail = problemDetailBuilder.build(resolution, exception, request);
        
        // Then
        assertThat(problemDetail.getType()).isEqualTo(URI.create("https://errors.example.com/test-0001"));
        assertThat(problemDetail.getTitle()).isEqualTo("TEST-0001");
        assertThat(problemDetail.getStatus()).isEqualTo(422);
        assertThat(problemDetail.getDetail()).isEqualTo("Test exception message");
        
        assertThat(problemDetail.getProperties().get(ErrorKeys.CODE)).isEqualTo("TEST-0001");
        assertThat(problemDetail.getProperties().get(ErrorKeys.PATH)).isEqualTo("/api/test");
        assertThat(problemDetail.getProperties().get(ErrorKeys.TRACE_ID)).isEqualTo("trace-123");
        assertThat(problemDetail.getProperties().get(ErrorKeys.TIMESTAMP)).isNotNull();
    }
    
    /**
     * Test building ProblemDetail without trace ID.
     */
    @Test
    void shouldBuildProblemDetailWithoutTraceId() {
        // Given
        TestErrorCode errorCode = TestErrorCode.TEST_ERROR;
        ErrorResolution resolution = new ErrorResolution(errorCode, 422);
        RuntimeException exception = new RuntimeException("Test exception message");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        
        when(traceProvider.getCurrentTraceId()).thenReturn(Optional.empty());
        
        // When
        ProblemDetail problemDetail = problemDetailBuilder.build(resolution, exception, request);
        
        // Then
        assertThat(problemDetail.getProperties().get(ErrorKeys.TRACE_ID)).isNull();
    }
    
    /**
     * Test sensitive data masking in error messages.
     */
    @Test
    void shouldMaskSensitiveDataInErrorMessages() {
        // Given
        TestErrorCode errorCode = TestErrorCode.TEST_ERROR;
        ErrorResolution resolution = new ErrorResolution(errorCode, 422);
        RuntimeException exception = new RuntimeException("Error with password=secret123 and token=abc123def");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        
        // When
        ProblemDetail problemDetail = problemDetailBuilder.build(resolution, exception, request);
        
        // Then
        assertThat(problemDetail.getDetail()).isEqualTo("Error with password=*** and token=***");
    }
    
    /**
     * Test proxy-aware path extraction with X-Forwarded-Path header.
     */
    @Test
    void shouldExtractPathFromXForwardedPathHeader() {
        // Given
        TestErrorCode errorCode = TestErrorCode.TEST_ERROR;
        ErrorResolution resolution = new ErrorResolution(errorCode, 422);
        RuntimeException exception = new RuntimeException("Test exception");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/test");
        request.addHeader("X-Forwarded-Path", "/api/v1/test");
        
        // When
        ProblemDetail problemDetail = problemDetailBuilder.build(resolution, exception, request);
        
        // Then
        assertThat(problemDetail.getProperties().get(ErrorKeys.PATH)).isEqualTo("/api/v1/test");
    }
    
    /**
     * Test proxy-aware path extraction with X-Forwarded-Uri header.
     */
    @Test
    void shouldExtractPathFromXForwardedUriHeader() {
        // Given
        TestErrorCode errorCode = TestErrorCode.TEST_ERROR;
        ErrorResolution resolution = new ErrorResolution(errorCode, 422);
        RuntimeException exception = new RuntimeException("Test exception");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/test");
        request.addHeader("X-Forwarded-Uri", "/api/v1/test");
        
        // When
        ProblemDetail problemDetail = problemDetailBuilder.build(resolution, exception, request);
        
        // Then
        assertThat(problemDetail.getProperties().get(ErrorKeys.PATH)).isEqualTo("/api/v1/test");
    }
    
    /**
     * Test proxy-aware path extraction with standard Forwarded header.
     */
    @Test
    void shouldExtractPathFromStandardForwardedHeader() {
        // Given
        TestErrorCode errorCode = TestErrorCode.TEST_ERROR;
        ErrorResolution resolution = new ErrorResolution(errorCode, 422);
        RuntimeException exception = new RuntimeException("Test exception");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/internal/test");
        request.addHeader("Forwarded", "for=192.168.1.1; proto=https; host=example.com; path=\"/api/v1/test\"");
        
        // When
        ProblemDetail problemDetail = problemDetailBuilder.build(resolution, exception, request);
        
        // Then
        assertThat(problemDetail.getProperties().get(ErrorKeys.PATH)).isEqualTo("/api/v1/test");
    }
    
    /**
     * Test field contributors are called correctly.
     */
    @Test
    void shouldCallFieldContributors() {
        // Given
        TestErrorCode errorCode = TestErrorCode.TEST_ERROR;
        ErrorResolution resolution = new ErrorResolution(errorCode, 422);
        RuntimeException exception = new RuntimeException("Test exception");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        
        doAnswer(invocation -> {
            Map<String, Object> fields = invocation.getArgument(0);
            fields.put("coreField", "coreValue");
            return null;
        }).when(coreFieldContributor).contribute(any(Map.class), eq(exception));
        
        doAnswer(invocation -> {
            Map<String, Object> fields = invocation.getArgument(0);
            fields.put("webField", "webValue");
            return null;
        }).when(webFieldContributor).contribute(any(Map.class), eq(exception), eq(request));
        
        // When
        ProblemDetail problemDetail = problemDetailBuilder.build(resolution, exception, request);
        
        // Then
        assertThat(problemDetail.getProperties().get("coreField")).isEqualTo("coreValue");
        assertThat(problemDetail.getProperties().get("webField")).isEqualTo("webValue");
    }
    
    /**
     * Test HTTP status code conversion handles invalid codes by falling back to 500.
     */
    @Test
    void shouldFallbackToInternalServerErrorForInvalidStatus() {
        // Given - 599 is not a valid HttpStatus enum value in Spring
        TestErrorCode errorCode = TestErrorCode.TEST_ERROR;
        ErrorResolution resolution = new ErrorResolution(errorCode, 599); 
        RuntimeException exception = new RuntimeException("Test exception");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        
        // When
        ProblemDetail problemDetail = problemDetailBuilder.build(resolution, exception, request);
        
        // Then - Should fallback to 500 for invalid status codes
        assertThat(problemDetail.getStatus()).isEqualTo(500);
    }
    
    /**
     * Test timestamp format is ISO 8601 UTC.
     */
    @Test
    void shouldUseIso8601UtcTimestampFormat() {
        // Given
        TestErrorCode errorCode = TestErrorCode.TEST_ERROR;
        ErrorResolution resolution = new ErrorResolution(errorCode, 422);
        RuntimeException exception = new RuntimeException("Test exception");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        
        Instant before = Instant.now();
        
        // When
        ProblemDetail problemDetail = problemDetailBuilder.build(resolution, exception, request);
        
        Instant after = Instant.now();
        
        // Then
        String timestamp = (String) problemDetail.getProperties().get(ErrorKeys.TIMESTAMP);
        assertThat(timestamp).isNotNull();
        assertThat(timestamp).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?Z");
        
        Instant parsedTimestamp = Instant.parse(timestamp);
        assertThat(parsedTimestamp).isBetween(before, after);
    }
    
    /**
     * Test error codes for testing purposes.
     */
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
}