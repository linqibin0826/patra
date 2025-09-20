package com.patra.registry.domain.exception;

/**
 * Domain exception thrown when attempting to use a disabled dictionary type.
 * This exception indicates that the requested dictionary type exists but is
 * currently disabled and cannot be used in business operations.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public class DictionaryTypeDisabled extends RegistryRuleViolation {
    
    /** The dictionary type code that is disabled */
    private final String typeCode;
    
    /**
     * Constructs a new dictionary type disabled exception.
     * 
     * @param typeCode the dictionary type code that is disabled, must not be null
     * @throws IllegalArgumentException if typeCode is null or empty
     */
    public DictionaryTypeDisabled(String typeCode) {
        super(String.format("Dictionary type is disabled: %s", typeCode));
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        this.typeCode = typeCode;
    }
    
    /**
     * Constructs a new dictionary type disabled exception with a custom message.
     * 
     * @param typeCode the dictionary type code that is disabled, must not be null
     * @param message the detail message explaining the rule violation
     * @throws IllegalArgumentException if typeCode is null or empty
     */
    public DictionaryTypeDisabled(String typeCode, String message) {
        super(message);
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        this.typeCode = typeCode;
    }
    
    /**
     * Constructs a new dictionary type disabled exception with a custom message and cause.
     * 
     * @param typeCode the dictionary type code that is disabled, must not be null
     * @param message the detail message explaining the rule violation
     * @param cause the cause of this exception
     * @throws IllegalArgumentException if typeCode is null or empty
     */
    public DictionaryTypeDisabled(String typeCode, String message, Throwable cause) {
        super(message, cause);
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Type code cannot be null or empty");
        }
        this.typeCode = typeCode;
    }
    
    /**
     * Returns the dictionary type code that is disabled.
     * 
     * @return the type code, never null or empty
     */
    public String getTypeCode() {
        return typeCode;
    }
}