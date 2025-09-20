package com.patra.registry.domain.exception;

import com.patra.common.error.DomainException;

/**
 * Base domain exception for all Registry service business rule violations.
 * This exception represents domain-level errors that occur during Registry operations
 * and should be handled appropriately by the application layer.
 * 
 * All Registry domain exceptions should extend this class to maintain consistency
 * and enable proper error handling throughout the Registry service.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public abstract class RegistryException extends DomainException {
    
    /**
     * Constructs a new registry domain exception with the specified detail message.
     * 
     * @param message the detail message explaining the exception
     */
    protected RegistryException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new registry domain exception with the specified detail message and cause.
     * 
     * @param message the detail message explaining the exception
     * @param cause the cause of this exception
     */
    protected RegistryException(String message, Throwable cause) {
        super(message, cause);
    }
}