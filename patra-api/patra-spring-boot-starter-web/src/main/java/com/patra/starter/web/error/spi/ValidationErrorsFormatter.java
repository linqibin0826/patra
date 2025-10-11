package com.patra.starter.web.error.spi;

import com.patra.starter.web.error.model.ValidationError;
import org.springframework.validation.BindingResult;
import java.util.List;

/**
 * SPI contract for formatting validation errors while masking sensitive values and
 * enforcing output limits.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ValidationErrorsFormatter {
    
    /**
     * Transform the {@link BindingResult} into a masked list of validation errors.
     *
     * @param bindingResult source of validation errors
     * @return sanitized validation errors suitable for exposure to clients
     */
    List<ValidationError> formatWithMasking(BindingResult bindingResult);
}
