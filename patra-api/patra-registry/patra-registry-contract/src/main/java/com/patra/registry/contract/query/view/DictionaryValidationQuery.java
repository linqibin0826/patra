package com.patra.registry.contract.query.view;

/**
 * Dictionary validation query object for CQRS read operations.
 * Shared between app module and contract module for validation results.
 * This immutable query object represents the outcome of dictionary validation operations
 * and is used for both internal validation and external API responses.
 * 
 * @param typeCode the dictionary type code being validated
 * @param itemCode the dictionary item code being validated
 * @param isValid whether the validation passed successfully
 * @param errorMessage detailed error message if validation failed, null if validation passed
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryValidationQuery(
    String typeCode,
    String itemCode,
    boolean isValid,
    String errorMessage
) {
    
    /**
     * Creates a new DictionaryValidationQuery with validation.
     * 
     * @param typeCode the dictionary type code being validated
     * @param itemCode the dictionary item code being validated
     * @param isValid whether the validation passed successfully
     * @param errorMessage detailed error message if validation failed
     * @throws IllegalArgumentException if typeCode or itemCode is null or empty, or if validation failed but no error message provided
     */
    public DictionaryValidationQuery {
        if (typeCode == null || typeCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary type code cannot be null or empty");
        }
        if (itemCode == null || itemCode.trim().isEmpty()) {
            throw new IllegalArgumentException("Dictionary item code cannot be null or empty");
        }
        if (!isValid && (errorMessage == null || errorMessage.trim().isEmpty())) {
            throw new IllegalArgumentException("Error message is required when validation fails");
        }
        
        // Normalize the codes and error message
        typeCode = typeCode.trim();
        itemCode = itemCode.trim();
        errorMessage = errorMessage != null ? errorMessage.trim() : null;
    }
    
    /**
     * Creates a successful validation result.
     * 
     * @param typeCode the dictionary type code that was validated
     * @param itemCode the dictionary item code that was validated
     * @return a DictionaryValidationQuery indicating successful validation
     */
    public static DictionaryValidationQuery success(String typeCode, String itemCode) {
        return new DictionaryValidationQuery(typeCode, itemCode, true, null);
    }
    
    /**
     * Creates a failed validation result with an error message.
     * 
     * @param typeCode the dictionary type code that was validated
     * @param itemCode the dictionary item code that was validated
     * @param errorMessage the error message describing why validation failed
     * @return a DictionaryValidationQuery indicating failed validation with the provided error message
     * @throws IllegalArgumentException if errorMessage is null or empty
     */
    public static DictionaryValidationQuery failure(String typeCode, String itemCode, String errorMessage) {
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("Error message cannot be null or empty for failed validation");
        }
        return new DictionaryValidationQuery(typeCode, itemCode, false, errorMessage);
    }
    
    /**
     * Creates a failed validation result for a missing dictionary item.
     * 
     * @param typeCode the dictionary type code that was not found
     * @param itemCode the dictionary item code that was not found
     * @return a DictionaryValidationQuery with a standardized "not found" error message
     */
    public static DictionaryValidationQuery notFound(String typeCode, String itemCode) {
        String message = String.format("Dictionary item not found: type='%s', item='%s'", typeCode, itemCode);
        return new DictionaryValidationQuery(typeCode, itemCode, false, message);
    }
    
    /**
     * Creates a failed validation result for a disabled dictionary item.
     * 
     * @param typeCode the dictionary type code
     * @param itemCode the dictionary item code that is disabled
     * @return a DictionaryValidationQuery with a standardized "disabled" error message
     */
    public static DictionaryValidationQuery disabled(String typeCode, String itemCode) {
        String message = String.format("Dictionary item is disabled: type='%s', item='%s'", typeCode, itemCode);
        return new DictionaryValidationQuery(typeCode, itemCode, false, message);
    }
    
    /**
     * Checks if this validation result represents a failure.
     * 
     * @return true if validation failed, false if validation passed
     */
    public boolean isFailure() {
        return !isValid;
    }
    
    /**
     * Gets a formatted reference string for the validated dictionary item.
     * 
     * @return a string in the format "typeCode:itemCode"
     */
    public String getReferenceString() {
        return typeCode + ":" + itemCode;
    }
    
    /**
     * Gets the error message if validation failed.
     * 
     * @return the error message, or null if validation passed
     */
    public String getErrorMessage() {
        return errorMessage;
    }
}