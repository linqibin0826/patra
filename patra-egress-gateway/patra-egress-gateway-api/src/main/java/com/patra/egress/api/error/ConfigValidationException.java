package com.patra.egress.api.error;

/**
 * Exception thrown when configuration validation fails
 *
 * <p>Indicates that caller-provided or system-loaded configuration does not meet validity
 * requirements.
 *
 * @author linqibin
 * @since 0.1.0
 */
public class ConfigValidationException extends EgressException {

  /**
   * Constructs a configuration validation exception
   *
   * @param message validation error message
   */
  public ConfigValidationException(String message) {
    super(EgressErrors.CONFIG_VALIDATION_FAILED, message);
  }

  /**
   * Constructs a configuration validation exception with cause
   *
   * @param message validation error message
   * @param cause underlying cause
   */
  public ConfigValidationException(String message, Throwable cause) {
    super(EgressErrors.CONFIG_VALIDATION_FAILED, message, cause);
  }
}
