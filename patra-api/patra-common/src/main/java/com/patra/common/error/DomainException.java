package com.patra.common.error;

/**
 * Base class for all domain exceptions in the Patra platform.
 * This class provides a framework-agnostic foundation for domain-layer exceptions
 * without any dependencies on Spring or other frameworks.
 * 
 * Domain exceptions should extend this class to maintain clean separation
 * between domain logic and technical concerns.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public abstract class DomainException extends RuntimeException {
    
    /**
     * Constructs a new domain exception with the specified detail message.
     * 
     * @param message the detail message explaining the exception
     */
    protected DomainException(String message) {
        super(message);
    }
    
    /**
     * Constructs a new domain exception with the specified detail message and cause.
     * 
     * @param message the detail message explaining the exception
     * @param cause the cause of this exception
     */
    protected DomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
