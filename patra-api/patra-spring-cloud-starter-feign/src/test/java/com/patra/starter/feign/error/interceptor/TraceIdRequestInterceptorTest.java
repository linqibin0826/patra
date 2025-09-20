package com.patra.starter.feign.error.interceptor;

import com.patra.starter.core.error.config.TracingProperties;
import com.patra.starter.core.error.spi.TraceProvider;
import feign.RequestTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TraceIdRequestInterceptor class.
 * Tests the automatic addition of trace ID to outgoing Feign requests.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@ExtendWith(MockitoExtension.class)
class TraceIdRequestInterceptorTest {

    @Mock
    private TraceProvider traceProvider;

    @Mock
    private TracingProperties tracingProperties;

    private TraceIdRequestInterceptor interceptor;

    @BeforeEach
    void setUp() {
        // Create interceptor without stubbing - will be set up per test as needed
        interceptor = new TraceIdRequestInterceptor(traceProvider, tracingProperties);
    }

    /**
     * Test trace ID is added to request when available.
     */
    @Test
    void shouldAddTraceIdWhenAvailable() {
        // Given
        String traceId = "test-trace-id-123";
        when(traceProvider.getCurrentTraceId()).thenReturn(Optional.of(traceId));
        when(tracingProperties.getHeaderNames()).thenReturn(List.of("traceId"));

        RequestTemplate template = new RequestTemplate();

        // When
        interceptor.apply(template);

        // Then
        Collection<String> traceIdHeaders = template.headers().get("traceId");
        assertThat(traceIdHeaders).isNotNull();
        assertThat(traceIdHeaders).containsExactly(traceId);
    }

    /**
     * Test no trace ID header is added when trace ID is not available.
     */
    @Test
    void shouldNotAddTraceIdWhenNotAvailable() {
        // Given
        when(traceProvider.getCurrentTraceId()).thenReturn(Optional.empty());

        RequestTemplate template = new RequestTemplate();

        // When
        interceptor.apply(template);

        // Then
        Collection<String> traceIdHeaders = template.headers().get("traceId");
        assertThat(traceIdHeaders).isNull();
    }

    /**
     * Test existing headers are preserved when adding trace ID.
     */
    @Test
    void shouldPreserveExistingHeaders() {
        // Given
        String traceId = "test-trace-id-456";
        when(traceProvider.getCurrentTraceId()).thenReturn(Optional.of(traceId));
        when(tracingProperties.getHeaderNames()).thenReturn(List.of("traceId"));

        RequestTemplate template = new RequestTemplate();
        template.header("Content-Type", "application/json");
        template.header("Authorization", "Bearer token123");

        // When
        interceptor.apply(template);

        // Then
        Collection<String> contentTypeHeaders = template.headers().get("Content-Type");
        assertThat(contentTypeHeaders).containsExactly("application/json");

        Collection<String> authHeaders = template.headers().get("Authorization");
        assertThat(authHeaders).containsExactly("Bearer token123");

        Collection<String> traceIdHeaders = template.headers().get("traceId");
        assertThat(traceIdHeaders).containsExactly(traceId);
    }

    /**
     * Test existing trace ID header is not overwritten.
     */
    @Test
    void shouldNotOverwriteExistingTraceIdHeader() {
        // Given
        String existingTraceId = "existing-trace-id";
        String newTraceId = "new-trace-id";
        when(traceProvider.getCurrentTraceId()).thenReturn(Optional.of(newTraceId));
        when(tracingProperties.getHeaderNames()).thenReturn(List.of("traceId"));

        RequestTemplate template = new RequestTemplate();
        template.header("traceId", existingTraceId);

        // When
        interceptor.apply(template);

        // Then
        Collection<String> traceIdHeaders = template.headers().get("traceId");
        assertThat(traceIdHeaders).containsExactly(existingTraceId, newTraceId); // Both headers should be present
    }

    /**
     * Test trace ID is added when existing trace ID header is empty.
     */
    @Test
    void shouldAddTraceIdWhenExistingHeaderIsEmpty() {
        // Given
        String traceId = "new-trace-id-789";
        when(traceProvider.getCurrentTraceId()).thenReturn(Optional.of(traceId));
        when(tracingProperties.getHeaderNames()).thenReturn(List.of("traceId"));

        RequestTemplate template = new RequestTemplate();
        template.header("traceId", ""); // Empty existing header

        // When
        interceptor.apply(template);

        // Then
        Collection<String> traceIdHeaders = template.headers().get("traceId");
        assertThat(traceIdHeaders).contains(traceId); // Should contain the new trace ID
        assertThat(traceIdHeaders.size()).isGreaterThanOrEqualTo(1); // At least one header
    }

    /**
     * Test interceptor handles null trace provider gracefully.
     */
    @Test
    void shouldHandleNullTraceProvider() {
        // Given
        lenient().when(tracingProperties.getHeaderNames()).thenReturn(List.of("traceId"));
        TraceIdRequestInterceptor nullProviderInterceptor = new TraceIdRequestInterceptor(null, tracingProperties);
        RequestTemplate template = new RequestTemplate();

        // When & Then - Should not throw exception
        nullProviderInterceptor.apply(template);

        // Verify no trace ID header is added
        Collection<String> traceIdHeaders = template.headers().get("traceId");
        assertThat(traceIdHeaders).isNull();
    }

    /**
     * Test interceptor handles trace provider exception gracefully.
     */
    @Test
    void shouldHandleTraceProviderException() {
        // Given
        when(traceProvider.getCurrentTraceId()).thenThrow(new RuntimeException("Trace provider error"));

        RequestTemplate template = new RequestTemplate();

        // When & Then - Should not throw exception
        interceptor.apply(template);

        // Verify no trace ID header is added
        Collection<String> traceIdHeaders = template.headers().get("traceId");
        assertThat(traceIdHeaders).isNull();
    }
}