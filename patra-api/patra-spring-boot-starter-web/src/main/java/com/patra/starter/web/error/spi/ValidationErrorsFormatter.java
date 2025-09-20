package com.patra.starter.web.error.spi;

import com.patra.starter.web.error.model.ValidationError;
import org.springframework.validation.BindingResult;
import java.util.List;

/**
 * SPI interface for formatting validation errors with sensitive data masking.
 * Implementations should mask sensitive field values and apply size limits.
 * 
 * @author linqibin
 * @since 0.1.0
 */
public interface ValidationErrorsFormatter {
    
    /**
     * Formats validation errors from BindingResult with sensitive data masking.
     * 
     * @param bindingResult the binding result containing validation errors, must not be null
     * @return list of formatted validation errors with masked sensitive data, never null
     */
    List<ValidationError> formatWithMasking(BindingResult bindingResult);
}