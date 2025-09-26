package com.patra.starter.web.error.builder;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.common.error.problem.ErrorKeys;
import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.spi.ProblemFieldContributor;
import com.patra.starter.core.error.spi.TraceProvider;
import com.patra.starter.web.error.config.WebErrorProperties;
import com.patra.starter.web.error.spi.WebProblemFieldContributor;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 构建 RFC 7807 {@link org.springframework.http.ProblemDetail} 响应的构造器。
 *
 * <p>特性：
 * - 支持敏感信息脱敏
 * - 代理友好的路径提取（Forwarded / X-Forwarded-Uri 等）
 * - 同时处理核心与 Web 维度的扩展字段
 *
 * @author linqibin
 * @since 0.1.0
 * @see com.patra.starter.web.error.config.WebErrorAutoConfiguration
 */
@Slf4j
@Component
public class ProblemDetailBuilder {
    
    @SuppressWarnings("unused")
    private final ErrorProperties errorProperties;
    private final WebErrorProperties webProperties;
    private final TraceProvider traceProvider;
    private final List<ProblemFieldContributor> coreFieldContributors;
    private final List<WebProblemFieldContributor> webFieldContributors;
    
    public ProblemDetailBuilder(
            ErrorProperties errorProperties,
            WebErrorProperties webProperties,
            TraceProvider traceProvider,
            List<ProblemFieldContributor> coreFieldContributors,
            List<WebProblemFieldContributor> webFieldContributors) {
        this.errorProperties = errorProperties;
        this.webProperties = webProperties;
        this.traceProvider = traceProvider;
        this.coreFieldContributors = coreFieldContributors;
        this.webFieldContributors = webFieldContributors;
    }
    
    /**
     * 根据解析结果与 HTTP 上下文构建 ProblemDetail。
     *
     * @param resolution 解析结果（含错误码与状态码）
     * @param exception 当前处理的异常
     * @param request HTTP 请求
     * @return 填充完成的 ProblemDetail
     */
    public ProblemDetail build(ErrorResolution resolution, Throwable exception, HttpServletRequest request) {
        log.debug("Building ProblemDetail: errorCode={}, httpStatus={}", 
                resolution.errorCode().code(), resolution.httpStatus());
        
        // Convert int status to HttpStatus for ProblemDetail creation
        HttpStatus httpStatus = convertToHttpStatus(resolution.httpStatus());
        ProblemDetail problemDetail = ProblemDetail.forStatus(httpStatus);
        
        // Standard RFC 7807 fields
        problemDetail.setType(buildTypeUri(resolution.errorCode()));
        problemDetail.setTitle(resolution.errorCode().code());
        problemDetail.setDetail(maskSensitiveData(exception.getMessage()));
        
        // Extension fields
        problemDetail.setProperty(ErrorKeys.CODE, resolution.errorCode().code());
        problemDetail.setProperty(ErrorKeys.PATH, extractPath(request));
        problemDetail.setProperty(ErrorKeys.TIMESTAMP, Instant.now().atOffset(ZoneOffset.UTC).toString());

        // 读取 web 配置以决定是否附加其他通用字段（此处仅作为引用，避免未使用字段告警）
        if (!webProperties.isIncludeStack()) {
            // 当不包含堆栈时，不额外添加 detailStack 字段
        }
        
        // Add trace ID if available
        traceProvider.getCurrentTraceId()
            .ifPresent(traceId -> {
                log.debug("Adding traceId to ProblemDetail: traceId={}", traceId);
                problemDetail.setProperty(ErrorKeys.TRACE_ID, traceId);
            });
        
        // Core field contributors (no request dependency)
        Map<String, Object> coreFields = new HashMap<>();
        coreFieldContributors.forEach(contributor -> {
            try {
                contributor.contribute(coreFields, exception);
            } catch (Exception e) {
                log.warn("Core field contributor failed: contributor={}, error={}", 
                        contributor.getClass().getSimpleName(), e.getMessage());
            }
        });
        coreFields.forEach(problemDetail::setProperty);
        
        // Web-specific field contributors (with request access)
        Map<String, Object> webFields = new HashMap<>();
        webFieldContributors.forEach(contributor -> {
            try {
                contributor.contribute(webFields, exception, request);
            } catch (Exception e) {
                log.warn("Web field contributor failed: contributor={}, error={}", 
                        contributor.getClass().getSimpleName(), e.getMessage());
            }
        });
        webFields.forEach(problemDetail::setProperty);
        
        log.debug("ProblemDetail built successfully: type={}, code={}", 
                problemDetail.getType(), resolution.errorCode().code());
        
        return problemDetail;
    }
    
    /**
     * 代理友好地提取请求路径。
     * 优先级：Forwarded > X-Forwarded-* > requestURI。
     *
     * @param request HTTP 请求
     * @return 路径字符串
     */
    private String extractPath(HttpServletRequest request) {
        // Priority: Standard Forwarded header > X-Forwarded-* > requestURI
        String forwarded = request.getHeader("Forwarded");
        if (forwarded != null && !forwarded.isEmpty()) {
            String path = parseForwardedPath(forwarded);
            if (path != null) {
                log.debug("Extracted path from Forwarded header: path={}", path);
                return path;
            }
        }
        
        String forwardedPath = request.getHeader("X-Forwarded-Path");
        if (forwardedPath != null && !forwardedPath.isEmpty()) {
            log.debug("Extracted path from X-Forwarded-Path: path={}", forwardedPath);
            return forwardedPath;
        }
        
        String forwardedUri = request.getHeader("X-Forwarded-Uri");
        if (forwardedUri != null && !forwardedUri.isEmpty()) {
            log.debug("Extracted path from X-Forwarded-Uri: path={}", forwardedUri);
            return forwardedUri;
        }
        
        String requestUri = request.getRequestURI();
        log.debug("Using request URI as path: path={}", requestUri);
        return requestUri;
    }
    
    /**
     * 从标准 Forwarded 头中解析 path 字段（for/ proto/ host/ path）。
     *
     * @param forwarded Forwarded 头值
     * @return 解析出的路径；未找到返回 null
     */
    private String parseForwardedPath(String forwarded) {
        String[] parts = forwarded.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.startsWith("path=")) {
                return trimmed.substring(5).replaceAll("^\"|\"$", "");
            }
        }
        return null;
    }
    
    /**
     * 将 int 状态码转换为 HttpStatus（非法值回退 500）。
     *
     * @param status 整型状态码
     * @return HttpStatus
     */
    private HttpStatus convertToHttpStatus(int status) {
        try {
            return HttpStatus.valueOf(status);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid HTTP status code: {}, falling back to 500", status);
            return HttpStatus.INTERNAL_SERVER_ERROR;
        }
    }
    
    /**
     * 对错误消息中的敏感信息进行脱敏。
     *
     * @param message 原始消息
     * @return 脱敏后的消息
     */
    private String maskSensitiveData(String message) {
        if (message == null) {
            return null;
        }
        
        // Mask common sensitive patterns
        return message
            .replaceAll("(?i)(password|token|secret|key)=[^\\s,}]+", "$1=***")
            .replaceAll("(?i)(password|token|secret|key)\":\\s*\"[^\"]+\"", "$1\":\"***\"");
    }
    
    /**
     * 根据错误码构建 ProblemDetail 的 type URI。
     *
     * @param errorCode 错误码
     * @return type URI
     */
    private URI buildTypeUri(ErrorCodeLike errorCode) {
        String baseUrl = webProperties.getTypeBaseUrl();
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        return URI.create(baseUrl + errorCode.code().toLowerCase());
    }
}
