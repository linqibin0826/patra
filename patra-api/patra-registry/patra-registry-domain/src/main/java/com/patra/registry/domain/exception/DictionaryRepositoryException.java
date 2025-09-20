package com.patra.registry.domain.exception;

import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;

import java.util.Set;

/**
 * Repository exception thrown when dictionary repository operations fail.
 * This exception represents infrastructure-level errors such as database connectivity issues,
 * SQL execution failures, or data access problems that occur in the repository layer.
 * 
 * As part of the domain layer, this exception defines the contract for repository failures
 * without coupling to specific infrastructure implementations.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public class DictionaryRepositoryException extends RegistryException implements HasErrorTraits {
    
    /** The operation that was being performed when the exception occurred */
    private final String operation;
    
    /** The dictionary type code associated with the failed operation, if applicable */
    private final String typeCode;
    
    /** The dictionary item code associated with the failed operation, if applicable */
    private final String itemCode;
    
    /**
     * Constructs a new dictionary repository exception with the specified detail message.
     * 
     * @param message the detail message explaining the repository failure
     */
    public DictionaryRepositoryException(String message) {
        super(message);
        this.operation = null;
        this.typeCode = null;
        this.itemCode = null;
    }
    
    /**
     * Constructs a new dictionary repository exception with the specified detail message and cause.
     * 
     * @param message the detail message explaining the repository failure
     * @param cause the underlying cause of the repository failure
     */
    public DictionaryRepositoryException(String message, Throwable cause) {
        super(message, cause);
        this.operation = null;
        this.typeCode = null;
        this.itemCode = null;
    }
    
    /**
     * Constructs a new dictionary repository exception with operation context.
     * 
     * @param message the detail message explaining the repository failure
     * @param operation the repository operation that failed
     * @param cause the underlying cause of the repository failure
     */
    public DictionaryRepositoryException(String message, String operation, Throwable cause) {
        super(message, cause);
        this.operation = operation;
        this.typeCode = null;
        this.itemCode = null;
    }
    
    /**
     * Constructs a new dictionary repository exception with full context.
     * 
     * @param message the detail message explaining the repository failure
     * @param operation the repository operation that failed
     * @param typeCode the dictionary type code associated with the failed operation
     * @param itemCode the dictionary item code associated with the failed operation
     * @param cause the underlying cause of the repository failure
     */
    public DictionaryRepositoryException(String message, String operation, 
                                       String typeCode, String itemCode, Throwable cause) {
        super(message, cause);
        this.operation = operation;
        this.typeCode = typeCode;
        this.itemCode = itemCode;
    }
    
    /**
     * Gets the repository operation that failed.
     * 
     * @return the operation name, or null if not specified
     */
    public String getOperation() {
        return operation;
    }
    
    /**
     * Gets the dictionary type code associated with the failed operation.
     * 
     * @return the type code, or null if not applicable
     */
    public String getTypeCode() {
        return typeCode;
    }
    
    /**
     * Gets the dictionary item code associated with the failed operation.
     * 
     * @return the item code, or null if not applicable
     */
    public String getItemCode() {
        return itemCode;
    }
    
    /**
     * Returns the error traits for repository exceptions.
     * Repository exceptions are typically classified as dependency unavailable.
     * 
     * @return set containing the DEP_UNAVAILABLE error trait
     */
    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.DEP_UNAVAILABLE);
    }
}