package com.patra.registry.domain.exception;

/**
 * Base domain exception for dictionary-related business rule violations.
 * This exception represents domain-level errors that occur during dictionary operations
 * and should be handled appropriately by the application layer.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public class DictionaryDomainException extends RuntimeException {
    
    /** The dictionary type code associated with this exception, if applicable */
    private final String typeCode;
    
    /** The dictionary item code associated with this exception, if applicable */
    private final String itemCode;
    
    /**
     * Constructs a new dictionary domain exception with the specified detail message.
     * 
     * @param message the detail message explaining the exception
     */
    public DictionaryDomainException(String message) {
        super(message);
        this.typeCode = null;
        this.itemCode = null;
    }
    
    /**
     * Constructs a new dictionary domain exception with the specified detail message and cause.
     * 
     * @param message the detail message explaining the exception
     * @param cause the cause of this exception
     */
    public DictionaryDomainException(String message, Throwable cause) {
        super(message, cause);
        this.typeCode = null;
        this.itemCode = null;
    }
    
    /**
     * Constructs a new dictionary domain exception with dictionary context.
     * 
     * @param message the detail message explaining the exception
     * @param typeCode the dictionary type code associated with this exception
     * @param itemCode the dictionary item code associated with this exception
     */
    public DictionaryDomainException(String message, String typeCode, String itemCode) {
        super(message);
        this.typeCode = typeCode;
        this.itemCode = itemCode;
    }
    
    /**
     * Constructs a new dictionary domain exception with dictionary context and cause.
     * 
     * @param message the detail message explaining the exception
     * @param typeCode the dictionary type code associated with this exception
     * @param itemCode the dictionary item code associated with this exception
     * @param cause the cause of this exception
     */
    public DictionaryDomainException(String message, String typeCode, String itemCode, Throwable cause) {
        super(message, cause);
        this.typeCode = typeCode;
        this.itemCode = itemCode;
    }
    
    /**
     * Gets the dictionary type code associated with this exception.
     * 
     * @return the type code, or null if not applicable
     */
    public String getTypeCode() {
        return typeCode;
    }
    
    /**
     * Gets the dictionary item code associated with this exception.
     * 
     * @return the item code, or null if not applicable
     */
    public String getItemCode() {
        return itemCode;
    }
}