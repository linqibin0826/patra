package com.patra.registry.domain.model.vo;

/**
 * Validation result value object containing validation outcome.
 * This immutable value object encapsulates the result of dictionary validation operations,
 * providing both the validation status and detailed error information when validation fails.
 * 
 * @param isValid whether the validation passed successfully
 * @param errorMessage detailed error message if validation failed, null if validation passed
 * @author linqibin
 * @since 0.1.0
 */
public record ValidationResult(
    boolean isValid,
    String errorMessage
) {
    
    /**
     * Creates a new ValidationResult with validation.
     * 
     * @param isValid whether the validation passed successfully
     * @param errorMessage detailed error message if validation failed
     * @throws IllegalArgumentException if validation failed but no error message provided
     */
    public ValidationResult {
        if (!isValid && (errorMessage == null || errorMessage.trim().isEmpty())) {
            throw new IllegalArgumentException("Error message is required when validation fails");
        }
        // Normalize error message
        errorMessage = errorMessage != null ? errorMessage.trim() : null;
    }
    
    /**
     * Creates a successful validation result.
     * 
     * @return a ValidationResult indicating successful validation
     */
    public static ValidationResult success() {
        return new ValidationResult(true, null);
    }
    
    /**
     * Creates a failed validation result with an error message.
     * 
     * @param errorMessage the error message describing why validation failed
     * @return a ValidationResult indicating failed validation with the provided error message
     * @throws IllegalArgumentException if errorMessage is null or empty
     */
    public static ValidationResult failure(String errorMessage) {
        if (errorMessage == null || errorMessage.trim().isEmpty()) {
            throw new IllegalArgumentException("Error message cannot be null or empty for failed validation");
        }
        return new ValidationResult(false, errorMessage);
    }
    
    /**
     * Creates a failed validation result for a missing dictionary item.
     * 
     * @param typeCode the dictionary type code that was not found
     * @param itemCode the dictionary item code that was not found
     * @return a ValidationResult with a standardized "not found" error message
     */
    public static ValidationResult notFound(String typeCode, String itemCode) {
        String message = String.format("Dictionary item not found: type='%s', item='%s'", typeCode, itemCode);
        return new ValidationResult(false, message);
    }
    
    /**
     * Creates a failed validation result for a disabled dictionary item.
     * 
     * @param typeCode the dictionary type code
     * @param itemCode the dictionary item code that is disabled
     * @return a ValidationResult with a standardized "disabled" error message
     */
    public static ValidationResult disabled(String typeCode, String itemCode) {
        String message = String.format("Dictionary item is disabled: type='%s', item='%s'", typeCode, itemCode);
        return new ValidationResult(false, message);
    }
    
    /**
     * Creates a failed validation result for a deleted dictionary item.
     * 
     * @param typeCode the dictionary type code
     * @param itemCode the dictionary item code that is deleted
     * @return a ValidationResult with a standardized "deleted" error message
     */
    public static ValidationResult deleted(String typeCode, String itemCode) {
        String message = String.format("Dictionary item is deleted: type='%s', item='%s'", typeCode, itemCode);
        return new ValidationResult(false, message);
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
     * Gets the error message if validation failed.
     * 
     * @return the error message, or null if validation passed
     */
    public String getErrorMessage() {
        return errorMessage;
    }
}