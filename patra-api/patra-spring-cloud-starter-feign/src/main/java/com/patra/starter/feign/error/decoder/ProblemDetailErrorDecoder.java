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
 * 将下游服务错误响应解码为 {@link com.patra.starter.feign.error.exception.RemoteCallException} 的 Feign 错误解码器。
 *
 * <p>支持符合 RFC 7807 的 {@link org.springframework.http.ProblemDetail} 响应；
 * 对非标准/畸形响应在“宽容模式”下进行优雅兜底；必要时退回 {@link feign.FeignException}。
 *
 * <p>特性：
 * - 自动解析 {@link ProblemDetail} 并转换为领域内统一异常
 * - 宽容模式（tolerant）保障异常/非 JSON/空体的健壮处理
 * - 从响应头提取 TraceId，便于调用链路关联
 * - 受配置限制的响应体读取大小，避免内存风险
 * - 全程打点 {@link com.patra.starter.feign.error.metrics.FeignErrorMetrics}
 *
 * @author linqibin
 * @since 0.1.0
 * @see com.patra.starter.feign.error.config.FeignErrorProperties 配置项
 * @see com.patra.starter.feign.error.metrics.FeignErrorMetrics 指标采集
 * @see feign.codec.ErrorDecoder Feign 解码 SPI
 */
@Slf4j
public class ProblemDetailErrorDecoder implements ErrorDecoder {
    
    private final ObjectMapper objectMapper;
    private final FeignErrorProperties properties;
    private final FeignErrorMetrics feignErrorMetrics;
    
    /**
     * 构造函数。
     *
     * @param objectMapper Jackson 的 JSON 解析器
     * @param properties Feign 错误处理配置
     * @param feignErrorMetrics Feign 错误处理指标采集器
     */
    public ProblemDetailErrorDecoder(ObjectMapper objectMapper, FeignErrorProperties properties, 
                                   FeignErrorMetrics feignErrorMetrics) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.feignErrorMetrics = feignErrorMetrics;
    }
    
    /**
     * 将 Feign 错误响应解码为合适的异常对象。
     *
     * @param methodKey 触发本次调用的 Feign 方法键
     * @param response 下游服务返回的错误响应
     * @return 代表错误语义的异常实例
     */
    @Override
    public Exception decode(String methodKey, Response response) {
        log.debug("Decoding remote error response: method={}, status={}", methodKey, response.status());
        
        boolean decodingSuccess = false;
        boolean tolerantModeUsed = false;
        
        try {
            // Detect content type and record metrics
            String contentType = getContentType(response);
            boolean isProblemDetail = isProblemDetailResponse(response);
            feignErrorMetrics.recordContentTypeDetection(methodKey, contentType, isProblemDetail);
            
            // Try ProblemDetail parsing first
            if (isProblemDetail) {
                long parseStartTime = System.currentTimeMillis();
                ProblemDetail problemDetail = parseProblemDetailSafely(response);
                long parseTime = System.currentTimeMillis() - parseStartTime;
                
                boolean parseSuccess = problemDetail != null;
                feignErrorMetrics.recordProblemDetailParsing(methodKey, response.status(), parseSuccess, parseTime);
                
                if (parseSuccess) {
                    log.debug("Parsed ProblemDetail successfully: method={}", methodKey);
                    decodingSuccess = true;
                    
                    // Record trace id extraction
                    String traceId = extractTraceId(response);
                    feignErrorMetrics.recordTraceIdExtraction(methodKey, traceId != null, 
                                                            traceId != null ? "response_header" : null);
                    
                    return new RemoteCallException(problemDetail, methodKey);
                }
            }
            
            // Non-ProblemDetail response: handle based on tolerant/strict mode
            if (properties.isTolerant()) {
                tolerantModeUsed = true;
                decodingSuccess = true;
                return handleTolerantMode(methodKey, response);
            } else {
                log.debug("Strict mode: fallback to FeignException, method={}", methodKey);
                return FeignException.errorStatus(methodKey, response);
            }
            
        } catch (Exception e) {
            log.warn("Failed to decode remote error response, method={}, error={}", methodKey, e.getMessage());
            
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
            // Record overall decoding success
            feignErrorMetrics.recordErrorDecodingSuccess(methodKey, response.status(), 
                                                       decodingSuccess, tolerantModeUsed);
        }
    }
    
    /**
     * 宽容模式下的兜底处理，针对多种非标准响应提供温和降级。
     */
    private RemoteCallException handleTolerantMode(String methodKey, Response response) {
        String traceId = extractTraceId(response);
        String message = buildFallbackMessage(response);
        
        log.debug("Tolerant mode: creating RemoteCallException, method={}, status={}", methodKey, response.status());
        
        return new RemoteCallException(response.status(), message, methodKey, traceId);
    }
    
    /**
     * 从响应中构建兜底的错误消息。
     */
    private String buildFallbackMessage(Response response) {
        String reason = response.reason();
        if (reason != null && !reason.trim().isEmpty()) {
            return reason;
        }
        
        // Read a small portion of body for context
        try {
            String body = readResponseBodySafely(response);
            if (body != null && !body.trim().isEmpty()) {
                // Truncate long body for readability
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
     * 安全地从响应体解析 {@link ProblemDetail}。
     * 若解析失败或响应体为空/非法，返回 {@code null}。
     */
    private ProblemDetail parseProblemDetailSafely(Response response) {
        try {
            String body = readResponseBodySafely(response);
            if (body == null || body.trim().isEmpty()) {
            log.debug("Empty response body; cannot parse ProblemDetail");
                return null;
            }
            
            ProblemDetail problemDetail = objectMapper.readValue(body, ProblemDetail.class);
            log.debug("Successfully parsed ProblemDetail with status={}", problemDetail.getStatus());
            return problemDetail;
            
        } catch (Exception e) {
            log.debug("Failed to parse ProblemDetail: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * 在大小限制与错误处理保护下安全读取响应体。
     */
    private String readResponseBodySafely(Response response) throws IOException {
        if (response.body() == null) {
            return null;
        }
        
        long readStartTime = System.currentTimeMillis();
        
        // Read with configured max bytes to avoid memory risk
        byte[] bodyBytes = response.body().asInputStream().readNBytes(properties.getMaxErrorBodySize());
        String body = new String(bodyBytes, StandardCharsets.UTF_8);
        
        long readTime = System.currentTimeMillis() - readStartTime;
        boolean truncated = bodyBytes.length >= properties.getMaxErrorBodySize();
        
        // Record response body reading metrics
        feignErrorMetrics.recordResponseBodyReading("unknown", bodyBytes.length, readTime, truncated);
        
        return body;
    }
    
    /**
     * Determine if response is ProblemDetail by Content-Type.
     */
    private boolean isProblemDetailResponse(Response response) {
        String contentType = getContentType(response);
        return contentType != null && contentType.toLowerCase().contains("application/problem+json");
    }
    
    /**
     * Get Content-Type from response headers.
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
     * Extract TraceId from common response headers for correlation.
     */
    private String extractTraceId(Response response) {
        // Try common trace headers in order
        String[] traceHeaders = {"traceId", "X-B3-TraceId", "traceparent", "X-Trace-Id"};
        
        for (String header : traceHeaders) {
            Collection<String> values = response.headers().get(header);
            if (values != null && !values.isEmpty()) {
                String traceId = values.iterator().next();
                if (traceId != null && !traceId.trim().isEmpty()) {
                    log.debug("Extracted TraceId from header {}: {}", header, traceId);
                    return traceId.trim();
                }
            }
        }
        
        log.debug("No TraceId found in response headers");
        return null;
    }
}
