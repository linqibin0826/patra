package com.patra.starter.web.error.builder;

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
 * Builder for creating RFC 7807 ProblemDetail responses with sensitive data masking
 * and proxy-aware path extraction. Handles both core and web-specific field contributions.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class ProblemDetailBuilder {
    
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
     * Builds a ProblemDetail response from error resolution and HTTP context.
     * 
     * @param resolution the error resolution containing error code and HTTP status, must not be null
     * @param exception the exception being handled, must not be null
     * @param request the HTTP servlet request for context extraction, must not be null
     * @return fully populated ProblemDetail response, never null
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
     * Extracts request path with proxy-aware header support.
     * Priority: Standard Forwarded header > X-Forwarded-* > requestURI
     * 
     * @param request the HTTP servlet request, must not be null
     * @return the extracted request path, never null
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
     * Parses path from standard Forwarded header.
     * Format: for=...; proto=...; host=...; path=...
     * 
     * @param forwarded the Forwarded header value, must not be null
     * @return the parsed path or null if not found
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
     * Converts int HTTP status to HttpStatus with fallback to 500.
     * 
     * @param status the HTTP status code as int
     * @return the corresponding HttpStatus, defaults to INTERNAL_SERVER_ERROR for invalid codes
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
     * Masks sensitive data patterns in error messages.
     * 
     * @param message the error message to mask, can be null
     * @return the message with sensitive data masked, or null if input was null
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
     * Builds the type URI for ProblemDetail from error code.
     * 
     * @param errorCode the error code to build URI for, must not be null
     * @return the type URI for the error code, never null
     */
    private URI buildTypeUri(com.patra.common.error.codes.ErrorCodeLike errorCode) {
        String baseUrl = webProperties.getTypeBaseUrl();
        if (!baseUrl.endsWith("/")) {
            baseUrl += "/";
        }
        return URI.create(baseUrl + errorCode.code().toLowerCase());
    }
}