package com.patra.registry.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.Set;

/**
 * Semantic base exception for Registry resource conflicts.
 * This exception indicates that an operation cannot be completed due to a conflict
 * with existing resources or business rules (e.g., duplicate names, version conflicts).
 * 
 * All Registry conflict exceptions should extend this class to ensure consistent
 * error trait classification and handling.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public abstract class RegistryConflict extends RegistryException implements HasErrorTraits {
    
    /**
     * Constructs a new registry conflict exception with the specified detail message.
     * 
     * @param message the detail message explaining the conflict
     */
    protected RegistryConflict(String message) {
        super(message);
    }
    
    /**
     * Constructs a new registry conflict exception with the specified detail message and cause.
     * 
     * @param message the detail message explaining the conflict
     * @param cause the cause of this exception
     */
    protected RegistryConflict(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Returns the error traits for conflict exceptions.
     * All Registry conflict exceptions are classified with the CONFLICT trait.
     * 
     * @return set containing the CONFLICT error trait
     */
    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.CONFLICT);
    }
}