package com.patra.egress.api.error;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.common.error.codes.HttpStdErrors;

/**
 * Egress Gateway error codes
 *
 * <p>Provides standardized error codes for the egress gateway service. Uses HttpStdErrors for
 * HTTP-aligned errors (0xxx segment) and custom ErrorCodeLike implementations for business errors
 * (1xxx+ segment).
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class EgressErrors {

  private EgressErrors() {}

  /** HTTP-aligned errors (0xxx segment) */
  private static final HttpStdErrors.Group HTTP_ERRORS = HttpStdErrors.of("EGR");

  // ==================== HTTP-Aligned Errors (0xxx) ====================

  /** EGR-0400: Bad Request */
  public static final ErrorCodeLike BAD_REQUEST = HTTP_ERRORS.BAD_REQUEST();

  /** EGR-0422: Unprocessable Entity - Configuration validation failed */
  public static final ErrorCodeLike UNPROCESSABLE_ENTITY = HTTP_ERRORS.UNPROCESSABLE();

  /** EGR-0429: Too Many Requests - Rate limit exceeded */
  public static final ErrorCodeLike RATE_LIMIT_EXCEEDED = HTTP_ERRORS.TOO_MANY();

  /** EGR-0500: Internal Server Error */
  public static final ErrorCodeLike INTERNAL_SERVER_ERROR = HTTP_ERRORS.INTERNAL_ERROR();

  /** EGR-0503: Service Unavailable - Circuit breaker is open */
  public static final ErrorCodeLike CIRCUIT_BREAKER_OPEN = HTTP_ERRORS.UNAVAILABLE();

  /** EGR-0504: Gateway Timeout - External service call timeout */
  public static final ErrorCodeLike GATEWAY_TIMEOUT = HTTP_ERRORS.GATEWAY_TIMEOUT();

  // ==================== Business Errors (1xxx+) ====================

  /** EGR-1001: Config load failed */
  public static final ErrorCodeLike CONFIG_LOAD_FAILED =
      businessError("1001", 500, "Failed to load system configuration");

  /** EGR-1002: Config validation failed */
  public static final ErrorCodeLike CONFIG_VALIDATION_FAILED =
      businessError("1002", 422, "Configuration validation failed");

  /** EGR-1003: External service call failed */
  public static final ErrorCodeLike EXTERNAL_CALL_FAILED =
      businessError("1003", 502, "External service call failed");

  /** EGR-1004: Response envelope build failed */
  public static final ErrorCodeLike RESPONSE_ENVELOPE_FAILED =
      businessError("1004", 500, "Failed to build response envelope");

  /**
   * Creates a business error code
   *
   * @param suffix error code suffix (e.g., "1001")
   * @param httpStatus HTTP status code
   * @param description error description
   * @return ErrorCodeLike instance
   */
  private static ErrorCodeLike businessError(String suffix, int httpStatus, String description) {
    final String code = "EGR-" + suffix;
    final int status = httpStatus;
    final String desc = description;

    return new ErrorCodeLike() {
      @Override
      public String code() {
        return code;
      }

      @Override
      public int httpStatus() {
        return status;
      }

      @Override
      public String toString() {
        return code + ": " + desc;
      }
    };
  }
}
