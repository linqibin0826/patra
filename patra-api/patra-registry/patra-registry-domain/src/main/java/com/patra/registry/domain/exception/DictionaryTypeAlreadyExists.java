package com.patra.registry.domain.exception;

/**
 * Domain exception thrown when attempting to create a dictionary type that already exists.
 * This exception indicates that the specified dictionary type code is already
 * in use and cannot be created again due to uniqueness constraints.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public class DictionaryTypeAlreadyExists extends RegistryConflict {
    
    /** The dictionary type code that already exists */
    private final String typeCode;
    
    /**
     * Constructs a new dictionary type already exists exception for the specified type code.
     * 
     * @param typeCode the dictionary type code that already exists, must not be null
     * @throws IllegalArgumentException if typeCode is null or empty
     */
    public DictionaryTypeAlreadyExists(String typeCode) {
        super(String.format("Dictionary type already exists: %s", typeCode));
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        this.typeCode = typeCode;
    }
    
    /**
     * Constructs a new dictionary type already exists exception with a custom message.
     * 
     * @param typeCode the dictionary type code that already exists, must not be null
     * @param message the detail message explaining the conflict
     * @throws IllegalArgumentException if typeCode is null or empty
     */
    public DictionaryTypeAlreadyExists(String typeCode, String message) {
        super(message);
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        this.typeCode = typeCode;
    }
    
    /**
     * Constructs a new dictionary type already exists exception with a custom message and cause.
     * 
     * @param typeCode the dictionary type code that already exists, must not be null
     * @param message the detail message explaining the conflict
     * @param cause the cause of this exception
     * @throws IllegalArgumentException if typeCode is null or empty
     */
    public DictionaryTypeAlreadyExists(String typeCode, String message, Throwable cause) {
        super(message, cause);
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        this.typeCode = typeCode;
    }
    
    /**
     * Returns the dictionary type code that already exists.
     * 
     * @return the type code, never null or empty
     */
    public String getTypeCode() {
        return typeCode;
    }
}