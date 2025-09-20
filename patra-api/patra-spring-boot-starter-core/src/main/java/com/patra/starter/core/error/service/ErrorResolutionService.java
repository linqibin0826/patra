package com.patra.starter.core.error.service;

import com.patra.common.error.ApplicationException;
import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import com.patra.starter.core.error.config.ErrorProperties;
import com.patra.starter.core.error.metrics.ErrorMetrics;
import com.patra.starter.core.error.model.ErrorResolution;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import com.patra.starter.core.error.spi.StatusMappingStrategy;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for resolving exceptions to error codes and HTTP status codes.
 * Implements the error resolution algorithm with cause chain traversal and class-level caching.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class ErrorResolutionService {
    
    /** Maximum depth for cause chain traversal to prevent infinite loops */
    private static final int MAX_CAUSE_DEPTH = 10;
    
    /** Error handling configuration */
    private final ErrorProperties errorProperties;
    
    /** Status mapping strategy */
    private final StatusMappingStrategy statusMappingStrategy;
    
    /** List of error mapping contributors */
    private final List<ErrorMappingContributor> mappingContributors;
    
    /** Error metrics collector */
    private final ErrorMetrics errorMetrics;
    
    /** Cache for resolved error resolutions by exception class */
    private final ConcurrentHashMap<Class<?>, ErrorResolution> resolutionCache = new ConcurrentHashMap<>();
    
    /**
     * Creates a new ErrorResolutionService.
     * 
     * @param errorProperties error configuration properties, must not be null
     * @param statusMappingStrategy status mapping strategy, must not be null
     * @param mappingContributors list of error mapping contributors, must not be null
     * @param errorMetrics error metrics collector, must not be null
     */
    public ErrorResolutionService(ErrorProperties errorProperties,
                                StatusMappingStrategy statusMappingStrategy,
                                List<ErrorMappingContributor> mappingContributors,
                                ErrorMetrics errorMetrics) {
        this.errorProperties = errorProperties;
        this.statusMappingStrategy = statusMappingStrategy;
        this.mappingContributors = mappingContributors;
        this.errorMetrics = errorMetrics;
    }
    
    /**
     * Resolves an exception to an error code and HTTP status.
     * Uses class-level caching for performance optimization and includes monitoring.
     * 
     * @param exception the exception to resolve, must not be null
     * @return error resolution containing error code and HTTP status
     */
    public ErrorResolution resolve(Throwable exception) {
        long startTime = System.currentTimeMillis();
        
        if (exception == null) {
            log.warn("Null exception passed to resolve, using fallback");
            return createFallbackResolution();
        }
        
        // Check cache first for performance
        Class<?> exceptionClass = exception.getClass();
        ErrorResolution cached = resolutionCache.get(exceptionClass);
        boolean cacheHit = cached != null;
        
        ErrorResolution resolution;
        if (cacheHit) {
            log.debug("Using cached resolution for exception class: {}", exceptionClass.getSimpleName());
            resolution = cached;
        } else {
            // Resolve with cause chain traversal
            resolution = resolveWithCauseChain(exception, 0);
            
            // Cache the resolution for this exception class
            resolutionCache.put(exceptionClass, resolution);
            log.debug("Cached resolution for exception class: {} -> {}", 
                     exceptionClass.getSimpleName(), resolution);
        }
        
        // Record metrics
        long resolutionTime = System.currentTimeMillis() - startTime;
        errorMetrics.recordResolutionTime(exceptionClass, resolution.errorCode(), resolutionTime, cacheHit);
        errorMetrics.recordCacheHitMiss(exceptionClass, cacheHit);
        errorMetrics.recordErrorCodeDistribution(resolution.errorCode(), resolution.httpStatus(), 
                                                errorProperties.getContextPrefix());
        
        return resolution;
    }
    
    /**
     * Resolves exception with cause chain traversal up to maximum depth.
     * 
     * @param exception the exception to resolve
     * @param depth current traversal depth
     * @return error resolution
     */
    private ErrorResolution resolveWithCauseChain(Throwable exception, int depth) {
        if (depth > MAX_CAUSE_DEPTH) {
            log.warn("Maximum cause chain depth {} exceeded, using server error fallback", MAX_CAUSE_DEPTH);
            return new ErrorResolution(createCode("0500"), 500);
        }
        
        log.debug("Resolving exception at depth {}: {}", depth, exception.getClass().getSimpleName());
        
        // 1. ApplicationException - highest priority
        if (exception instanceof ApplicationException appEx) {
            ErrorCodeLike code = appEx.getErrorCode();
            int status = statusMappingStrategy.mapToHttpStatus(code, exception);
            log.debug("Resolved ApplicationException to code: {}, status: {}", code.code(), status);
            return new ErrorResolution(code, status);
        }
        
        // 2. ErrorMappingContributor - explicit overrides
        for (ErrorMappingContributor contributor : mappingContributors) {
            long contributorStartTime = System.currentTimeMillis();
            boolean success = false;
            
            try {
                var mapped = contributor.mapException(exception);
                if (mapped.isPresent()) {
                    success = true;
                    long executionTime = System.currentTimeMillis() - contributorStartTime;
                    errorMetrics.recordContributorPerformance(contributor.getClass(), success, executionTime);
                    
                    ErrorCodeLike code = mapped.get();
                    int status = statusMappingStrategy.mapToHttpStatus(code, exception);
                    log.debug("Resolved via ErrorMappingContributor to code: {}, status: {}", 
                             code.code(), status);
                    return new ErrorResolution(code, status);
                }
                success = true; // No mapping found is still considered success
            } catch (Exception e) {
                log.warn("ErrorMappingContributor {} failed: {}", 
                        contributor.getClass().getSimpleName(), e.getMessage());
            } finally {
                long executionTime = System.currentTimeMillis() - contributorStartTime;
                errorMetrics.recordContributorPerformance(contributor.getClass(), success, executionTime);
            }
        }
        
        // 3. HasErrorTraits - semantic classification
        if (exception instanceof HasErrorTraits traitsEx) {
            Set<ErrorTrait> traits = traitsEx.getErrorTraits();
            if (traits != null && !traits.isEmpty()) {
                ErrorCodeLike code = mapTraitsToCode(traits);
                int status = mapTraitsToStatus(traits);
                log.debug("Resolved via ErrorTraits {} to code: {}, status: {}", 
                         traits, code.code(), status);
                return new ErrorResolution(code, status);
            }
        }
        
        // 4. Naming convention heuristics
        String className = exception.getClass().getSimpleName();
        ErrorResolution namingResolution = resolveByNamingConvention(className);
        if (namingResolution != null) {
            log.debug("Resolved via naming convention '{}' to code: {}, status: {}", 
                     className, namingResolution.errorCode().code(), namingResolution.httpStatus());
            return namingResolution;
        }
        
        // Try cause chain if current exception doesn't match
        Throwable cause = exception.getCause();
        if (cause != null && cause != exception) {
            log.debug("Trying cause chain for: {}", cause.getClass().getSimpleName());
            ErrorResolution causeResolution = resolveWithCauseChain(cause, depth + 1);
            errorMetrics.recordCauseChainDepth(depth + 1, true);
            return causeResolution;
        }
        
        // 5. Final fallback
        ErrorResolution fallback = isClientError(exception) ? 
            new ErrorResolution(createCode("0422"), 422) : 
            new ErrorResolution(createCode("0500"), 500);
        
        log.debug("Using fallback resolution for {}: code: {}, status: {}", 
                 className, fallback.errorCode().code(), fallback.httpStatus());
        
        // Record that we reached fallback resolution
        errorMetrics.recordCauseChainDepth(depth, false);
        
        return fallback;
    }
    
    /**
     * Maps error traits to error code.
     */
    private ErrorCodeLike mapTraitsToCode(Set<ErrorTrait> traits) {
        if (traits.contains(ErrorTrait.NOT_FOUND)) {
            return createCode("0404");
        } else if (traits.contains(ErrorTrait.CONFLICT)) {
            return createCode("0409");
        } else if (traits.contains(ErrorTrait.RULE_VIOLATION)) {
            return createCode("0422");
        } else if (traits.contains(ErrorTrait.QUOTA_EXCEEDED)) {
            return createCode("0429");
        } else if (traits.contains(ErrorTrait.UNAUTHORIZED)) {
            return createCode("0401");
        } else if (traits.contains(ErrorTrait.FORBIDDEN)) {
            return createCode("0403");
        } else if (traits.contains(ErrorTrait.TIMEOUT)) {
            return createCode("0504");
        } else if (traits.contains(ErrorTrait.DEP_UNAVAILABLE)) {
            return createCode("0503");
        }
        return createCode("0500");
    }
    
    /**
     * Maps error traits to HTTP status.
     */
    private int mapTraitsToStatus(Set<ErrorTrait> traits) {
        if (traits.contains(ErrorTrait.NOT_FOUND)) {
            return 404;
        } else if (traits.contains(ErrorTrait.CONFLICT)) {
            return 409;
        } else if (traits.contains(ErrorTrait.RULE_VIOLATION)) {
            return 422;
        } else if (traits.contains(ErrorTrait.QUOTA_EXCEEDED)) {
            return 429;
        } else if (traits.contains(ErrorTrait.UNAUTHORIZED)) {
            return 401;
        } else if (traits.contains(ErrorTrait.FORBIDDEN)) {
            return 403;
        } else if (traits.contains(ErrorTrait.TIMEOUT)) {
            return 504;
        } else if (traits.contains(ErrorTrait.DEP_UNAVAILABLE)) {
            return 503;
        }
        return 500;
    }
    
    /**
     * Resolves exception by naming convention heuristics.
     */
    private ErrorResolution resolveByNamingConvention(String className) {
        if (className.endsWith("NotFound")) {
            return new ErrorResolution(createCode("0404"), 404);
        } else if (className.endsWith("Conflict") || className.endsWith("AlreadyExists")) {
            return new ErrorResolution(createCode("0409"), 409);
        } else if (className.endsWith("Invalid") || className.endsWith("Validation")) {
            return new ErrorResolution(createCode("0422"), 422);
        } else if (className.endsWith("QuotaExceeded")) {
            return new ErrorResolution(createCode("0429"), 429);
        } else if (className.endsWith("Unauthorized")) {
            return new ErrorResolution(createCode("0401"), 401);
        } else if (className.endsWith("Forbidden")) {
            return new ErrorResolution(createCode("0403"), 403);
        } else if (className.endsWith("Timeout")) {
            return new ErrorResolution(createCode("0504"), 504);
        }
        return null;
    }
    
    /**
     * Determines if an exception represents a client error.
     */
    private boolean isClientError(Throwable exception) {
        String className = exception.getClass().getSimpleName().toLowerCase();
        return className.contains("validation") || 
               className.contains("illegal") || 
               className.contains("invalid") ||
               className.contains("bad") ||
               className.contains("malformed");
    }
    
    /**
     * Creates an error code with the configured context prefix.
     */
    private ErrorCodeLike createCode(String suffix) {
        String contextPrefix = errorProperties.getContextPrefix();
        if (contextPrefix == null || contextPrefix.isEmpty()) {
            log.warn("Context prefix not configured, using 'UNKNOWN'");
            contextPrefix = "UNKNOWN";
        }
        final String finalContextPrefix = contextPrefix;
        return () -> finalContextPrefix + "-" + suffix;
    }
    
    /**
     * Creates a fallback error resolution for severe error cases.
     */
    private ErrorResolution createFallbackResolution() {
        return new ErrorResolution(createCode("0500"), 500);
    }
}