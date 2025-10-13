package com.patra.starter.web.error.formatter;

import com.patra.starter.web.error.model.ValidationError;
import com.patra.starter.web.error.spi.ValidationErrorsFormatter;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;

/**
 * Default {@link ValidationErrorsFormatter} that masks sensitive values and caps the number of
 * validation errors returned to clients.
 *
 * @author linqibin
 * @since 0.1.0
 * @see com.patra.starter.web.error.model.ValidationError
 */
@Slf4j
public class DefaultValidationErrorsFormatter implements ValidationErrorsFormatter {

  /** Maximum number of validation errors included in the response. */
  private static final int MAX_ERRORS = 100;

  /** Field name patterns (case insensitive, substring match) that should be masked. */
  private static final Set<String> SENSITIVE_PATTERNS =
      Set.of(
          "password",
          "token",
          "secret",
          "key",
          "credential",
          "auth",
          "pin",
          "ssn",
          "credit",
          "card",
          "account");

  @Override
  public List<ValidationError> formatWithMasking(BindingResult bindingResult) {
    log.debug("Formatting validation errors: errorCount={}", bindingResult.getErrorCount());

    List<ValidationError> errors =
        bindingResult.getAllErrors().stream()
            .limit(MAX_ERRORS)
            .map(this::mapToValidationError)
            .collect(Collectors.toList());

    if (bindingResult.getErrorCount() > MAX_ERRORS) {
      log.warn(
          "Validation errors truncated: total={}, included={}",
          bindingResult.getErrorCount(),
          MAX_ERRORS);
    }

    return errors;
  }

  /**
   * Map Spring's {@link ObjectError} to a {@link ValidationError} and mask sensitive values.
   *
   * @param error binding error reported by Spring Validation
   * @return sanitized validation error
   */
  private ValidationError mapToValidationError(ObjectError error) {
    if (error instanceof FieldError fieldError) {
      String fieldName = fieldError.getField();
      Object rejectedValue = maskSensitiveValue(fieldName, fieldError.getRejectedValue());
      String message = fieldError.getDefaultMessage();

      return new ValidationError(fieldName, rejectedValue, message);
    } else {
      // Global errors (not field-specific)
      return new ValidationError(error.getObjectName(), null, error.getDefaultMessage());
    }
  }

  /**
   * Mask sensitive field values based on the configured name patterns.
   *
   * @param fieldName logical field name
   * @param value rejected value supplied by the client
   * @return masked value when considered sensitive; original value otherwise
   */
  private Object maskSensitiveValue(String fieldName, Object value) {
    if (fieldName == null || value == null) {
      return value;
    }

    String lowerFieldName = fieldName.toLowerCase();
    boolean isSensitive = SENSITIVE_PATTERNS.stream().anyMatch(lowerFieldName::contains);

    if (isSensitive) {
      log.debug("Masking sensitive field: fieldName={}", fieldName);
      return "***";
    }

    return value;
  }
}
