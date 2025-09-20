package com.patra.starter.web.error.formatter;

import com.patra.starter.web.error.model.ValidationError;
import com.patra.starter.web.error.spi.ValidationErrorsFormatter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Default implementation of ValidationErrorsFormatter with sensitive data masking.
 * Masks common sensitive field patterns and applies size limits to prevent oversized responses.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class DefaultValidationErrorsFormatter implements ValidationErrorsFormatter {
    
    /** Maximum number of validation errors to include in response */
    private static final int MAX_ERRORS = 100;
    
    /** Sensitive field patterns that should be masked */
    private static final Set<String> SENSITIVE_PATTERNS = Set.of(
        "password", "token", "secret", "key", "credential", "auth", 
        "pin", "ssn", "credit", "card", "account"
    );
    
    @Override
    public List<ValidationError> formatWithMasking(BindingResult bindingResult) {
        log.debug("Formatting validation errors: errorCount={}", bindingResult.getErrorCount());
        
        List<ValidationError> errors = bindingResult.getAllErrors().stream()
            .limit(MAX_ERRORS)
            .map(this::mapToValidationError)
            .collect(Collectors.toList());
        
        if (bindingResult.getErrorCount() > MAX_ERRORS) {
            log.warn("Validation errors truncated: total={}, included={}", 
                    bindingResult.getErrorCount(), MAX_ERRORS);
        }
        
        return errors;
    }
    
    /**
     * Maps ObjectError to ValidationError with sensitive data masking.
     * 
     * @param error the object error to map, must not be null
     * @return validation error with masked sensitive data, never null
     */
    private ValidationError mapToValidationError(ObjectError error) {
        if (error instanceof FieldError fieldError) {
            String fieldName = fieldError.getField();
            Object rejectedValue = maskSensitiveValue(fieldName, fieldError.getRejectedValue());
            String message = fieldError.getDefaultMessage();
            
            return new ValidationError(fieldName, rejectedValue, message);
        } else {
            // Global errors (not field-specific)
            return new ValidationError(
                error.getObjectName(), 
                null, 
                error.getDefaultMessage()
            );
        }
    }
    
    /**
     * Masks sensitive field values based on field name patterns.
     * 
     * @param fieldName the field name to check for sensitivity, can be null
     * @param value the field value to potentially mask, can be null
     * @return masked value if field is sensitive, original value otherwise
     */
    private Object maskSensitiveValue(String fieldName, Object value) {
        if (fieldName == null || value == null) {
            return value;
        }
        
        String lowerFieldName = fieldName.toLowerCase();
        boolean isSensitive = SENSITIVE_PATTERNS.stream()
            .anyMatch(lowerFieldName::contains);
        
        if (isSensitive) {
            log.debug("Masking sensitive field: fieldName={}", fieldName);
            return "***";
        }
        
        return value;
    }
}