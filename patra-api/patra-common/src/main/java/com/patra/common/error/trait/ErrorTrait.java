package com.patra.common.error.trait;

/**
 * Semantic classification traits for exceptions to enable consistent error handling
 * across different exception types. These traits are used by the error resolution
 * algorithm to map exceptions to appropriate HTTP status codes and error responses.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public enum ErrorTrait {
    
    /** Resource or entity not found */
    NOT_FOUND,
    
    /** Conflict with existing resource or business rule */
    CONFLICT,
    
    /** Business rule or validation violation */
    RULE_VIOLATION,
    
    /** Quota, rate limit, or capacity exceeded */
    QUOTA_EXCEEDED,
    
    /** Authentication required or failed */
    UNAUTHORIZED,
    
    /** Access forbidden for authenticated user */
    FORBIDDEN,
    
    /** Operation timeout or deadline exceeded */
    TIMEOUT,
    
    /** Dependency or external service unavailable */
    DEP_UNAVAILABLE
}