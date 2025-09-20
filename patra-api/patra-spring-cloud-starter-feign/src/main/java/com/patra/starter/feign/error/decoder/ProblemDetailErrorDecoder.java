package com.patra.starter.feign.error.decoder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.starter.feign.error.config.FeignErrorProperties;
import com.patra.starter.feign.error.exception.RemoteCallException;
import com.patra.starter.feign.error.metrics.FeignErrorMetrics;
import feign.FeignException;
import feign.Response;
import feign.codec.ErrorDecoder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;

/**
 * Feign error decoder that converts remote service error responses into RemoteCallException instances.
 * Supports RFC 7807 ProblemDetail responses with fallback handling for non-standard responses.
 * 
 * Features:
 * - Automatic ProblemDetail parsing and conversion
 * - Tolerant mode for graceful handling of malformed responses
 * - Trace ID extraction from response headers
 * - Configurable response body size limits
 * - Fallback to standard FeignException when appropriate
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class ProblemDetailErrorDecoder implements ErrorDecoder {
    
    private final ObjectMapper objectMapper;
    private final FeignErrorProperties properties;
    private final FeignErrorMetrics feignErrorMetrics;
    
    /**
     * Constructs a new ProblemDetailErrorDecoder with the specified configuration.
     * 
     * @param objectMapper the Jackson ObjectMapper for JSON parsing
     * @param properties the Feign error handling configuration
     * @param feignErrorMetrics the metrics collector for Feign error handling
     */
    public ProblemDetailErrorDecoder(ObjectMapper objectMapper, FeignErrorProperties properties, 
                                   FeignErrorMetrics feignErrorMetrics) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.feignErrorMetrics = feignErrorMetrics;
    }
    
    /**
     * Decodes a Feign error response into an appropriate exception.
     * 
     * @param methodKey the Feign method key that triggered this error
     * @param response the error response from the remote service
     * @return an exception representing the error condition
     */
    @Override
    public Exception decode(String methodKey, Response response) {
        log.debug("Decoding error response for method: {}, status: {}", methodKey, response.status());
        
        boolean decodingSuccess = false;
        boolean tolerantModeUsed = false;
        
        try {
            // Check content type and record metrics
            String contentType = getContentType(response);
            boolean isProblemDetail = isProblemDetailResponse(response);
            feignErrorMetrics.recordContentTypeDetection(methodKey, contentType, isProblemDetail);
            
            // Try to parse as ProblemDetail first
            if (isProblemDetail) {
                long parseStartTime = System.currentTimeMillis();
                ProblemDetail problemDetail = parseProblemDetailSafely(response);
                long parseTime = System.currentTimeMillis() - parseStartTime;
                
                boolean parseSuccess = problemDetail != null;
                feignErrorMetrics.recordProblemDetailParsing(methodKey, response.status(), parseSuccess, parseTime);
                
                if (parseSuccess) {
                    log.debug("Successfully parsed ProblemDetail for method: {}", methodKey);
                    decodingSuccess = true;
                    
                    // Record trace ID extraction
                    String traceId = extractTraceId(response);
                    feignErrorMetrics.recordTraceIdExtraction(methodKey, traceId != null, 
                                                            traceId != null ? "response_header" : null);
                    
                    return new RemoteCallException(problemDetail, methodKey);
                }
            }
            
            // Handle non-ProblemDetail responses based on tolerant mode
            if (properties.isTolerant()) {
                tolerantModeUsed = true;
                decodingSuccess = true;
                return handleTolerantMode(methodKey, response);
            } else {
                log.debug("Strict mode: falling back to FeignException for method: {}", methodKey);
                return FeignException.errorStatus(methodKey, response);
            }
            
        } catch (Exception e) {
            log.warn("Failed to decode error response for method: {}, error: {}", methodKey, e.getMessage());
            
            if (properties.isTolerant()) {
                tolerantModeUsed = true;
                decodingSuccess = true;
                
                String traceId = extractTraceId(response);
                feignErrorMetrics.recordTraceIdExtraction(methodKey, traceId != null, 
                                                        traceId != null ? "response_header" : null);
                
                return new RemoteCallException(
                    response.status(),
                    "Error decoding failed: " + e.getMessage(),
                    methodKey,
                    traceId
                );
            } else {
                return FeignException.errorStatus(methodKey, response);
            }
        } finally {
            // Record overall decoding success metrics
            feignErrorMetrics.recordErrorDecodingSuccess(methodKey, response.status(), 
                                                       decodingSuccess, tolerantModeUsed);
        }
    }
    
    /**
     * Handles error responses in tolerant mode, providing graceful fallbacks
     * for various non-standard response conditions.
     */
    private RemoteCallException handleTolerantMode(String methodKey, Response response) {
        String traceId = extractTraceId(response);
        String message = buildFallbackMessage(response);
        
        log.debug("Tolerant mode: creating RemoteCallException for method: {}, status: {}", 
                 methodKey, response.status());
        
        return new RemoteCallException(response.status(), message, methodKey, traceId);
    }
    
    /**
     * Builds a fallback error message from the response.
     */
    private String buildFallbackMessage(Response response) {
        String reason = response.reason();
        if (reason != null && !reason.trim().isEmpty()) {
            return reason;
        }
        
        // Try to read a small portion of the response body for context
        try {
            String body = readResponseBodySafely(response);
            if (body != null && !body.trim().isEmpty()) {
                // Truncate long bodies for readability
                if (body.length() > 200) {
                    body = body.substring(0, 200) + "...";
                }
                return "HTTP " + response.status() + ": " + body;
            }
        } catch (Exception e) {
            log.debug("Failed to read response body for fallback message: {}", e.getMessage());
        }
        
        return "HTTP " + response.status();
    }
    
    /**
     * Safely parses a ProblemDetail from the response body.
     * Returns null if parsing fails or body is empty/invalid.
     */
    private ProblemDetail parseProblemDetailSafely(Response response) {
        try {
            String body = readResponseBodySafely(response);
            if (body == null || body.trim().isEmpty()) {
                log.debug("Empty response body, cannot parse ProblemDetail");
                return null;
            }
            
            ProblemDetail problemDetail = objectMapper.readValue(body, ProblemDetail.class);
            log.debug("Successfully parsed ProblemDetail with status: {}", problemDetail.getStatus());
            return problemDetail;
            
        } catch (Exception e) {
            log.debug("Failed to parse ProblemDetail: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Safely reads the response body with size limits and error handling.
     */
    private String readResponseBodySafely(Response response) throws IOException {
        if (response.body() == null) {
            return null;
        }
        
        long readStartTime = System.currentTimeMillis();
        
        // Read with size limit to prevent memory issues
        byte[] bodyBytes = response.body().asInputStream().readNBytes(properties.getMaxErrorBodySize());
        String body = new String(bodyBytes, StandardCharsets.UTF_8);
        
        long readTime = System.currentTimeMillis() - readStartTime;
        boolean truncated = bodyBytes.length >= properties.getMaxErrorBodySize();
        
        // Record response body reading metrics
        feignErrorMetrics.recordResponseBodyReading("unknown", bodyBytes.length, readTime, truncated);
        
        return body;
    }
    
    /**
     * Checks if the response appears to be a ProblemDetail response
     * based on the Content-Type header.
     */
    private boolean isProblemDetailResponse(Response response) {
        String contentType = getContentType(response);
        return contentType != null && contentType.toLowerCase().contains("application/problem+json");
    }
    
    /**
     * Gets the content type from response headers.
     */
    private String getContentType(Response response) {
        Collection<String> contentTypes = response.headers().get("content-type");
        if (contentTypes == null) {
            contentTypes = response.headers().get("Content-Type");
        }
        
        if (contentTypes != null && !contentTypes.isEmpty()) {
            return contentTypes.iterator().next();
        }
        
        return null;
    }
    
    /**
     * Extracts trace ID from common response headers for correlation.
     */
    private String extractTraceId(Response response) {
        // Try common trace headers in order of preference
        String[] traceHeaders = {"traceId", "X-B3-TraceId", "traceparent", "X-Trace-Id"};
        
        for (String header : traceHeaders) {
            Collection<String> values = response.headers().get(header);
            if (values != null && !values.isEmpty()) {
                String traceId = values.iterator().next();
                if (traceId != null && !traceId.trim().isEmpty()) {
                    log.debug("Extracted trace ID from header {}: {}", header, traceId);
                    return traceId.trim();
                }
            }
        }
        
        log.debug("No trace ID found in response headers");
        return null;
    }
}