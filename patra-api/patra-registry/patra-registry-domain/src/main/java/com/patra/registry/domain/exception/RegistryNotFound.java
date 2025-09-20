package com.patra.registry.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.Set;

/**
 * Semantic base exception for Registry resources that cannot be found.
 * This exception indicates that a requested Registry resource (namespace, catalog, schema, etc.)
 * does not exist in the system or is not accessible due to business rules.
 * 
 * All Registry "not found" exceptions should extend this class to ensure consistent
 * error trait classification and handling.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public abstract class RegistryNotFound extends RegistryException implements HasErrorTraits {
    
    /**
     * Constructs a new registry not found exception with the specified detail message.
     * 
     * @param message the detail message explaining what was not found
     */
    protected RegistryNotFound(String message) {
        super(message);
    }
    
    /**
     * Constructs a new registry not found exception with the specified detail message and cause.
     * 
     * @param message the detail message explaining what was not found
     * @param cause the cause of this exception
     */
    protected RegistryNotFound(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * Returns the error traits for not found exceptions.
     * All Registry not found exceptions are classified with the NOT_FOUND trait.
     * 
     * @return set containing the NOT_FOUND error trait
     */
    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.NOT_FOUND);
    }
}