package com.patra.common.json;

/**
 * Exception thrown when JSON normalization fails.
 *
 * <p>Reasons for failure include parsing errors, illegal numeric values (NaN, Infinity), exceeding
 * depth or length limits, or encountering forbidden keys.
 */
public class JsonNormalizationException extends RuntimeException {

  /**
   * Creates an exception with the specified message.
   *
   * @param message error message describing the normalization failure
   */
  public JsonNormalizationException(String message) {
    super(message);
  }

  /**
   * Creates an exception with the specified message and cause.
   *
   * @param message error message describing the normalization failure
   * @param cause underlying cause of the failure
   */
  public JsonNormalizationException(String message, Throwable cause) {
    super(message, cause);
  }
}
