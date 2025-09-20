package com.patra.registry.domain.exception;

/**
 * Domain exception thrown when attempting to create a dictionary item that already exists.
 * This exception indicates that the specified dictionary item code is already
 * in use within the dictionary type and cannot be created again due to uniqueness constraints.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public class DictionaryItemAlreadyExists extends RegistryConflict {
    
    /** The dictionary type code */
    private final String typeCode;
    
    /** The dictionary item code that already exists */
    private final String itemCode;
    
    /**
     * Constructs a new dictionary item already exists exception.
     * 
     * @param typeCode the dictionary type code, must not be null
     * @param itemCode the dictionary item code that already exists, must not be null
     * @throws IllegalArgumentException if typeCode or itemCode is null or empty
     */
    public DictionaryItemAlreadyExists(String typeCode, String itemCode) {
        super(String.format("Dictionary item already exists: typeCode=%s, itemCode=%s", typeCode, itemCode));
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Item code cannot be null or empty");
        }
        this.typeCode = typeCode;
        this.itemCode = itemCode;
    }
    
    /**
     * Constructs a new dictionary item already exists exception with a custom message.
     * 
     * @param typeCode the dictionary type code, must not be null
     * @param itemCode the dictionary item code that already exists, must not be null
     * @param message the detail message explaining the conflict
     * @throws IllegalArgumentException if typeCode or itemCode is null or empty
     */
    public DictionaryItemAlreadyExists(String typeCode, String itemCode, String message) {
        super(message);
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Item code cannot be null or empty");
        }
        this.typeCode = typeCode;
        this.itemCode = itemCode;
    }
    
    /**
     * Constructs a new dictionary item already exists exception with a custom message and cause.
     * 
     * @param typeCode the dictionary type code, must not be null
     * @param itemCode the dictionary item code that already exists, must not be null
     * @param message the detail message explaining the conflict
     * @param cause the cause of this exception
     * @throws IllegalArgumentException if typeCode or itemCode is null or empty
     */
    public DictionaryItemAlreadyExists(String typeCode, String itemCode, String message, Throwable cause) {
        super(message, cause);
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Item code cannot be null or empty");
        }
        this.typeCode = typeCode;
        this.itemCode = itemCode;
    }
    
    /**
     * Returns the dictionary type code.
     * 
     * @return the type code, never null or empty
     */
    public String getTypeCode() {
        return typeCode;
    }
    
    /**
     * Returns the dictionary item code that already exists.
     * 
     * @return the item code, never null or empty
     */
    public String getItemCode() {
        return itemCode;
    }
}