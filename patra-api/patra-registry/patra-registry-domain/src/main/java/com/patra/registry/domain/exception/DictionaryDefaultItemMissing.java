package com.patra.registry.domain.exception;

/**
 * Domain exception thrown when a dictionary type requires a default item but none is configured.
 * This exception indicates that the dictionary type has business rules requiring a default item,
 * but no item is marked as the default or the default item is missing.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public class DictionaryDefaultItemMissing extends RegistryRuleViolation {
    
    /** The dictionary type code that is missing a default item */
    private final String typeCode;
    
    /**
     * Constructs a new dictionary default item missing exception.
     * 
     * @param typeCode the dictionary type code that is missing a default item, must not be null
     * @throws IllegalArgumentException if typeCode is null or empty
     */
    public DictionaryDefaultItemMissing(String typeCode) {
        super(String.format("Dictionary type is missing a default item: %s", typeCode));
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        this.typeCode = typeCode;
    }
    
    /**
     * Constructs a new dictionary default item missing exception with a custom message.
     * 
     * @param typeCode the dictionary type code that is missing a default item, must not be null
     * @param message the detail message explaining the rule violation
     * @throws IllegalArgumentException if typeCode is null or empty
     */
    public DictionaryDefaultItemMissing(String typeCode, String message) {
        super(message);
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        this.typeCode = typeCode;
    }
    
    /**
     * Constructs a new dictionary default item missing exception with a custom message and cause.
     * 
     * @param typeCode the dictionary type code that is missing a default item, must not be null
     * @param message the detail message explaining the rule violation
     * @param cause the cause of this exception
     * @throws IllegalArgumentException if typeCode is null or empty
     */
    public DictionaryDefaultItemMissing(String typeCode, String message, Throwable cause) {
        super(message, cause);
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        this.typeCode = typeCode;
    }
    
    /**
     * Returns the dictionary type code that is missing a default item.
     * 
     * @return the type code, never null or empty
     */
    public String getTypeCode() {
        return typeCode;
    }
}