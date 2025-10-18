package com.patra.common.logging.sanitizer;

/**
 * Interface for sanitizing sensitive data from log messages.
 *
 * <p>Implementations mask or redact sensitive information (passwords, tokens, PII, etc.) before
 * logging to prevent security violations and comply with data protection regulations.
 *
 * <p>This interface supports different sanitization modes:
 *
 * <ul>
 *   <li><b>MANUAL</b>: Sanitization only when explicitly requested via utility methods
 *   <li><b>AUTO</b>: Automatic sanitization of all log messages (future enhancement)
 * </ul>
 *
 * <p>Usage example:
 *
 * <pre>{@code
 * LogSanitizer sanitizer = new DefaultLogSanitizer();
 *
 * // Sanitize plain text
 * String safe = sanitizer.sanitize("password=secret123");
 * // Result: "password=***REDACTED***"
 *
 * // Sanitize JSON
 * String json = """
 *     {"user": "john", "apiKey": "sk-abc123"}
 *     """;
 * String safeJson = sanitizer.sanitizeJson(json);
 * // Result: {"user": "john", "apiKey": "***REDACTED***"}
 *
 * // Sanitize object (DTO/entity)
 * UserDTO user = new UserDTO("john", "john@example.com", "secret");
 * String safeStr = sanitizer.sanitizeObject(user);
 * // Result: Redacts sensitive fields based on annotations or naming patterns
 * }</pre>
 *
 * @see DefaultLogSanitizer
 * @since 0.1.0
 */
public interface LogSanitizer {

  /**
   * Sanitizes a string message by redacting sensitive data patterns.
   *
   * <p>Applies hardcoded regex patterns to detect and mask:
   *
   * <ul>
   *   <li>Passwords (password=..., pwd=..., etc.)
   *   <li>API keys and tokens (apiKey=..., token=..., etc.)
   *   <li>Email addresses
   *   <li>Phone numbers
   *   <li>Credit card numbers
   *   <li>Social Security Numbers (SSN)
   *   <li>Authorization headers
   * </ul>
   *
   * @param message The raw log message (may be null)
   * @return Sanitized message with sensitive data replaced by "***REDACTED***", or null if input is
   *     null
   */
  String sanitize(String message);

  /**
   * Sanitizes a JSON string by redacting sensitive field values.
   *
   * <p>Parses JSON and masks values for sensitive keys (e.g., password, apiKey, token, ssn,
   * creditCard).
   *
   * @param json The JSON string to sanitize (may be null)
   * @return Sanitized JSON with sensitive values replaced, or null if input is null
   */
  String sanitizeJson(String json);

  /**
   * Sanitizes an object by converting it to a safe string representation.
   *
   * <p>Detects sensitive fields based on:
   *
   * <ul>
   *   <li>Field names containing sensitive keywords (password, token, key, ssn, etc.)
   *   <li>Custom annotations (future enhancement)
   * </ul>
   *
   * @param obj The object to sanitize (may be null)
   * @return Safe string representation with sensitive fields masked, or null if input is null
   */
  String sanitizeObject(Object obj);

  /**
   * Checks if a string contains potentially sensitive data.
   *
   * <p>Useful for validation before logging.
   *
   * @param message The message to check
   * @return true if message contains patterns matching sensitive data
   */
  boolean containsSensitiveData(String message);
}
