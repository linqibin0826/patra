package com.patra.registry.domain.exception;

/**
 * Domain-wide validation exception (to replace scattered IllegalArgumentException usage).
 * <p>
 * Typical scenarios: constructing domain objects, enforcing invariants, and
 * validating query view parameters. This represents invalid input supplied by
 * callers rather than internal system errors.
 * </p>
 *
 * <p>Guidelines:
 * <ul>
 *   <li>Adapter/gateway may map this to HTTP 400 (or 422 if desired).</li>
 *   <li>Helps reduce alert noise by log filtering of expected validation failures.</li>
 *   <li>No business error codes here to avoid domain depending on API; mapping occurs in boot layer.</li>
 * </ul>
 * </p>
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
     * Convenience factory: throws when condition is false.
     * @param condition boolean condition to check
     * @param message error message when condition fails
     */
    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new DomainValidationException(message);
        }
    }

    /**
     * Assert a string is non-null and non-blank.
     * @param value value to check
     * @param field field name (for message composition)
     */
    public static String notBlank(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new DomainValidationException(field + " cannot be blank");
        }
        return value.trim();
    }

    /**
     * Assert an object is non-null.
     * @param obj object to check
     * @param field field name
     */
    public static <T> T nonNull(T obj, String field) {
        if (obj == null) {
            throw new DomainValidationException(field + " cannot be null");
        }
        return obj;
    }

    /**
     * Assert number is positive (> 0).
     * @param number numeric value
     * @param field field name
     */
    public static long positive(Long number, String field) {
        if (number == null || number <= 0) {
            throw new DomainValidationException(field + " must be positive");
        }
        return number;
    }

    /**
     * Assert integer is non-negative (>= 0).
     */
    public static int nonNegative(Integer number, String field) {
        if (number == null || number < 0) {
            throw new DomainValidationException(field + " cannot be negative");
        }
        return number;
    }

    /**
     * Assert array is not empty (checks only null/length==0).
     */
    public static <T> T[] notEmpty(T[] arr, String field) {
        if (arr == null || arr.length == 0) {
            throw new DomainValidationException(field + " cannot be empty");
        }
        return arr;
    }

    /**
     * Assert value is within the inclusive range [min, max].
     */
    public static long withinRange(long value, long minInclusive, long maxInclusive, String field) {
        if (value < minInclusive || value > maxInclusive) {
            throw new DomainValidationException(field + " must be between " + minInclusive + " and " + maxInclusive);
        }
        return value;
    }
}
