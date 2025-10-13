package com.patra.registry.domain.exception;

/**
 * Domain-wide validation exception (to replace scattered IllegalArgumentException usage).
 *
 * <p>Typical scenarios: constructing domain objects, enforcing invariants, and validating query
 * view parameters. This represents invalid input supplied by callers rather than internal system
 * errors.
 *
 * <p>Guidelines:
 *
 * <ul>
 *   <li>Adapter/gateway may map this to HTTP 400 (or 422 if desired).
 *   <li>Helps reduce alert noise by log filtering of expected validation failures.
 *   <li>No business error codes here to avoid domain depending on API; mapping occurs in boot
 *       layer.
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
public class DomainValidationException extends RuntimeException {

  public DomainValidationException(String message) {
    super(message);
  }

  public DomainValidationException(String message, Throwable cause) {
    super(message, cause);
  }

  /**
   * Convenience factory method that throws an exception when the condition is false.
   *
   * @param condition boolean condition to check
   * @param message error message when condition fails
   * @throws DomainValidationException when condition is false
   */
  public static void require(boolean condition, String message) {
    if (!condition) {
      throw new DomainValidationException(message);
    }
  }

  /**
   * Asserts a string is non-null and non-blank, returning trimmed value.
   *
   * @param value value to check
   * @param field field name (for message composition)
   * @return trimmed value if validation passes
   * @throws DomainValidationException when value is null or blank
   */
  public static String notBlank(String value, String field) {
    if (value == null || value.trim().isEmpty()) {
      throw new DomainValidationException(field + " cannot be blank");
    }
    return value.trim();
  }

  /**
   * Asserts an object is non-null.
   *
   * @param obj object to check
   * @param field field name for error message
   * @param <T> type of the object
   * @return the object if validation passes
   * @throws DomainValidationException when object is null
   */
  public static <T> T nonNull(T obj, String field) {
    if (obj == null) {
      throw new DomainValidationException(field + " cannot be null");
    }
    return obj;
  }

  /**
   * Asserts a number is positive (greater than 0).
   *
   * @param number numeric value to check
   * @param field field name for error message
   * @return the number if validation passes
   * @throws DomainValidationException when number is null or not positive
   */
  public static long positive(Long number, String field) {
    if (number == null || number <= 0) {
      throw new DomainValidationException(field + " must be positive");
    }
    return number;
  }

  /**
   * Asserts an integer is non-negative (greater than or equal to 0).
   *
   * @param number numeric value to check
   * @param field field name for error message
   * @return the number if validation passes
   * @throws DomainValidationException when number is null or negative
   */
  public static int nonNegative(Integer number, String field) {
    if (number == null || number < 0) {
      throw new DomainValidationException(field + " cannot be negative");
    }
    return number;
  }

  /**
   * Asserts an array is not empty (checks only null or length == 0).
   *
   * @param arr array to check
   * @param field field name for error message
   * @param <T> type of array elements
   * @return the array if validation passes
   * @throws DomainValidationException when array is null or empty
   */
  public static <T> T[] notEmpty(T[] arr, String field) {
    if (arr == null || arr.length == 0) {
      throw new DomainValidationException(field + " cannot be empty");
    }
    return arr;
  }

  /**
   * Asserts a value is within the inclusive range [min, max].
   *
   * @param value the value to check
   * @param minInclusive minimum allowed value (inclusive)
   * @param maxInclusive maximum allowed value (inclusive)
   * @param field field name for error message
   * @return the value if validation passes
   * @throws DomainValidationException when value is outside the range
   */
  public static long withinRange(long value, long minInclusive, long maxInclusive, String field) {
    if (value < minInclusive || value > maxInclusive) {
      throw new DomainValidationException(
          field + " must be between " + minInclusive + " and " + maxInclusive);
    }
    return value;
  }
}
