package com.patra.starter.feign.error.decoder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.common.error.problem.ErrorKeys;
import com.patra.starter.feign.error.config.FeignErrorProperties;
import com.patra.starter.feign.error.exception.RemoteCallException;
import com.patra.starter.feign.error.metrics.DefaultFeignErrorMetrics;
import com.patra.starter.feign.error.metrics.FeignErrorMetrics;
import feign.Request;
import feign.Response;
import feign.Util;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for ProblemDetailErrorDecoder.
 * 
 * @author linqibin
 * @since 0.1.0
 */
class ProblemDetailErrorDecoderTest {
    
    private ObjectMapper objectMapper;
    private FeignErrorProperties properties;
    private ProblemDetailErrorDecoder decoder;
    private FeignErrorMetrics feignErrorMetrics;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        properties = new FeignErrorProperties();
        properties.setTolerant(true);
        feignErrorMetrics = new DefaultFeignErrorMetrics();

        decoder = new ProblemDetailErrorDecoder(objectMapper, properties, feignErrorMetrics);
    }
    
    /**
     * Test decoding valid ProblemDetail response.
     */
    @Test
    void shouldDecodeProblemDetailResponse() {
        // Given
        ProblemDetail problemDetail = ProblemDetail.forStatus(404);
        problemDetail.setType(java.net.URI.create("https://errors.example.com/reg-0404"));
        problemDetail.setTitle("REG-0404");
        problemDetail.setDetail("Resource not found");
        problemDetail.setProperty(ErrorKeys.CODE, "REG-0404");
        problemDetail.setProperty(ErrorKeys.TRACE_ID, "trace-123");
        
        String jsonBody = """
            {
                "type": "https://errors.example.com/reg-0404",
                "title": "REG-0404",
                "status": 404,
                "detail": "Resource not found",
                "code": "REG-0404",
                "traceId": "trace-123"
            }
            """;
        
        Response response = createResponse(404, jsonBody, "application/problem+json");
        
        // When
        Exception exception = decoder.decode("TestClient#getResource()", response);
        
        // Then
        assertThat(exception).isInstanceOf(RemoteCallException.class);
        RemoteCallException remoteException = (RemoteCallException) exception;
        assertThat(remoteException.getErrorCode()).isEqualTo("REG-0404");
        assertThat(remoteException.getHttpStatus()).isEqualTo(404);
        assertThat(remoteException.getTraceId()).isEqualTo("trace-123");
        assertThat(remoteException.getMessage()).isEqualTo("Resource not found");
        assertThat(remoteException.getMethodKey()).isEqualTo("TestClient#getResource()");
    }
    
    /**
     * Test decoding response with empty body in tolerant mode.
     */
    @Test
    void shouldHandleEmptyBodyInTolerantMode() {
        // Given
        Response response = createResponse(404, "", "application/problem+json");
        
        // When
        Exception exception = decoder.decode("TestClient#getResource()", response);
        
        // Then
        assertThat(exception).isInstanceOf(RemoteCallException.class);
        RemoteCallException remoteException = (RemoteCallException) exception;
        assertThat(remoteException.getErrorCode()).isNull();
        assertThat(remoteException.getHttpStatus()).isEqualTo(404);
        assertThat(remoteException.getMessage()).isEqualTo("Not Found"); // Uses response.reason()
        assertThat(remoteException.getMethodKey()).isEqualTo("TestClient#getResource()");
    }
    
    /**
     * Test decoding response with non-JSON body in tolerant mode.
     */
    @Test
    void shouldHandleNonJsonBodyInTolerantMode() {
        // Given
        Response response = createResponse(500, "Internal Server Error", "text/plain");
        
        // When
        Exception exception = decoder.decode("TestClient#getResource()", response);
        
        // Then
        assertThat(exception).isInstanceOf(RemoteCallException.class);
        RemoteCallException remoteException = (RemoteCallException) exception;
        assertThat(remoteException.getErrorCode()).isNull();
        assertThat(remoteException.getHttpStatus()).isEqualTo(500);
        assertThat(remoteException.getMessage()).isEqualTo("Internal Server Error");
        assertThat(remoteException.getMethodKey()).isEqualTo("TestClient#getResource()");
    }
    
    /**
     * Test decoding response with invalid JSON in tolerant mode.
     */
    @Test
    void shouldHandleInvalidJsonInTolerantMode() {
        // Given
        Response response = createResponse(400, "{invalid json", "application/problem+json");
        
        // When
        Exception exception = decoder.decode("TestClient#getResource()", response);
        
        // Then
        assertThat(exception).isInstanceOf(RemoteCallException.class);
        RemoteCallException remoteException = (RemoteCallException) exception;
        assertThat(remoteException.getErrorCode()).isNull();
        assertThat(remoteException.getHttpStatus()).isEqualTo(400);
        assertThat(remoteException.getMessage()).isEqualTo("Bad Request"); // Uses response.reason()
        assertThat(remoteException.getMethodKey()).isEqualTo("TestClient#getResource()");
    }
    
    /**
     * Test decoding response with trace ID in headers.
     */
    @Test
    void shouldExtractTraceIdFromHeaders() {
        // Given
        Map<String, Collection<String>> headers = new HashMap<>();
        headers.put("content-type", List.of("text/plain"));
        headers.put("traceId", List.of("header-trace-123"));
        
        Response response = Response.builder()
            .status(500)
            .reason("Internal Server Error")
            .headers(headers)
            .body("Server Error", StandardCharsets.UTF_8)
            .request(Request.create(Request.HttpMethod.GET, "http://test.com", Map.of(), null, StandardCharsets.UTF_8, null))
            .build();
        
        // When
        Exception exception = decoder.decode("TestClient#getResource()", response);
        
        // Then
        assertThat(exception).isInstanceOf(RemoteCallException.class);
        RemoteCallException remoteException = (RemoteCallException) exception;
        assertThat(remoteException.getTraceId()).isEqualTo("header-trace-123");
    }
    
    /**
     * Test decoding response with X-B3-TraceId header.
     */
    @Test
    void shouldExtractB3TraceIdFromHeaders() {
        // Given
        Map<String, Collection<String>> headers = new HashMap<>();
        headers.put("content-type", List.of("text/plain"));
        headers.put("X-B3-TraceId", List.of("b3-trace-456"));
        
        Response response = Response.builder()
            .status(500)
            .reason("Internal Server Error")
            .headers(headers)
            .body("Server Error", StandardCharsets.UTF_8)
            .request(Request.create(Request.HttpMethod.GET, "http://test.com", Map.of(), null, StandardCharsets.UTF_8, null))
            .build();
        
        // When
        Exception exception = decoder.decode("TestClient#getResource()", response);
        
        // Then
        assertThat(exception).isInstanceOf(RemoteCallException.class);
        RemoteCallException remoteException = (RemoteCallException) exception;
        assertThat(remoteException.getTraceId()).isEqualTo("b3-trace-456");
    }
    
    /**
     * Test non-tolerant mode throws FeignException for non-ProblemDetail responses.
     */
    @Test
    void shouldThrowFeignExceptionInNonTolerantMode() {
        // Given
        properties.setTolerant(false);
        decoder = new ProblemDetailErrorDecoder(objectMapper, properties, feignErrorMetrics);
        
        Response response = createResponse(404, "Not Found", "text/plain");
        
        // When & Then
        assertThatThrownBy(() -> decoder.decode("TestClient#getResource()", response))
            .isInstanceOf(feign.FeignException.class);
    }
    
    /**
     * Test content type detection is case insensitive.
     */
    @Test
    void shouldDetectContentTypeCaseInsensitive() {
        // Given
        String jsonBody = """
            {
                "type": "https://errors.example.com/reg-0404",
                "title": "REG-0404",
                "status": 404,
                "detail": "Resource not found",
                "code": "REG-0404"
            }
            """;
        
        Map<String, Collection<String>> headers = new HashMap<>();
        headers.put("Content-Type", List.of("APPLICATION/PROBLEM+JSON; charset=utf-8"));
        
        Response response = Response.builder()
            .status(404)
            .reason("Not Found")
            .headers(headers)
            .body(jsonBody, StandardCharsets.UTF_8)
            .request(Request.create(Request.HttpMethod.GET, "http://test.com", Map.of(), null, StandardCharsets.UTF_8, null))
            .build();
        
        // When
        Exception exception = decoder.decode("TestClient#getResource()", response);
        
        // Then
        assertThat(exception).isInstanceOf(RemoteCallException.class);
        RemoteCallException remoteException = (RemoteCallException) exception;
        assertThat(remoteException.getErrorCode()).isEqualTo("REG-0404");
    }
    
    /**
     * Helper method to create Response objects for testing.
     */
    private Response createResponse(int status, String body, String contentType) {
        Map<String, Collection<String>> headers = new HashMap<>();
        headers.put("content-type", List.of(contentType));
        
        return Response.builder()
            .status(status)
            .reason(status == 404 ? "Not Found" : status == 500 ? "Internal Server Error" : "Bad Request")
            .headers(headers)
            .body(body, StandardCharsets.UTF_8)
            .request(Request.create(Request.HttpMethod.GET, "http://test.com", Map.of(), null, StandardCharsets.UTF_8, null))
            .build();
    }
}
