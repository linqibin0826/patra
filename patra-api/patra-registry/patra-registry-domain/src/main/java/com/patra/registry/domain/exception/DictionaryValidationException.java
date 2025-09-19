package com.patra.registry.domain.exception;

import java.util.List;

/**
 * Domain exception thrown when dictionary validation fails due to business rule violations.
 * This exception indicates that dictionary data or references violate domain constraints
 * such as disabled items, missing defaults, or invalid references.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public class DictionaryValidationException extends DictionaryDomainException {
    
    /** List of validation error messages providing detailed failure information */
    private final List<String> validationErrors;
    
    /**
     * Constructs a new dictionary validation exception with a single validation error.
     * 
     * @param message the validation error message
     * @param typeCode the dictionary type code associated with the validation failure
     * @param itemCode the dictionary item code associated with the validation failure
     */
    public DictionaryValidationException(String message, String typeCode, String itemCode) {
        super(message, typeCode, itemCode);
        this.validationErrors = List.of(message);
    }
    
    /**
     * Constructs a new dictionary validation exception with multiple validation errors.
     * 
     * @param validationErrors list of validation error messages
     * @param typeCode the dictionary type code associated with the validation failures
     */
    public DictionaryValidationException(List<String> validationErrors, String typeCode) {
        super(String.format("Dictionary validation failed for type %s: %s", 
                           typeCode, String.join(", ", validationErrors)), typeCode, null);
        this.validationErrors = List.copyOf(validationErrors);
    }
    
    /**
     * Constructs a new dictionary validation exception with multiple validation errors and custom message.
     * 
     * @param message the main validation error message
     * @param validationErrors list of detailed validation error messages
     * @param typeCode the dictionary type code associated with the validation failures
     * @param itemCode the dictionary item code associated with the validation failures
     */
    public DictionaryValidationException(String message, List<String> validationErrors, 
                                       String typeCode, String itemCode) {
        super(message, typeCode, itemCode);
        this.validationErrors = validationErrors != null ? List.copyOf(validationErrors) : List.of();
    }
    
    /**
     * Gets the list of validation error messages.
     * 
     * @return immutable list of validation error messages
     */
    public List<String> getValidationErrors() {
        return validationErrors;
    }
}