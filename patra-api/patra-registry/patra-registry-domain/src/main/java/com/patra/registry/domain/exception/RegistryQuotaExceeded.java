package com.patra.registry.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.Set;

/**
 * Semantic base exception for Registry quota and limit violations.
 * This exception indicates that an operation cannot be completed due to exceeding
 * quotas, rate limits, or capacity constraints (e.g., too many namespaces, schema size limits).
 * 
 * All Registry quota exceeded exceptions should extend this class to ensure consistent
 * error trait classification and handling.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public abstract class RegistryQuotaExceeded extends RegistryException implements HasErrorTraits {
    
    /**
     * Constructs a new registry quota exceeded exception with the specified detail message.
     * 
     * @param message the detail message explaining the quota violation
     */
    protected RegistryQuotaExceeded(String message) {
        super(message);
    }
    
    /**
     * Constructs a new registry quota exceeded exception with the specified detail message and cause.
     * 
     * @param message the detail message explaining the quota violation
     * @param cause the cause of this exception
     */
    protected RegistryQuotaExceeded(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Returns the error traits for quota exceeded exceptions.
     * All Registry quota exceeded exceptions are classified with the QUOTA_EXCEEDED trait.
     * 
     * @return set containing the QUOTA_EXCEEDED error trait
     */
    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.QUOTA_EXCEEDED);
    }
}