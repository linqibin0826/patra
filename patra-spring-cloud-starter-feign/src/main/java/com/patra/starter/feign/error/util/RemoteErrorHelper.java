package com.patra.starter.feign.error.util;

import com.patra.starter.feign.error.exception.RemoteCallException;

/**
 * Helper utilities for working with {@link
 * com.patra.starter.feign.error.exception.RemoteCallException}.
 *
 * <p>Provides semantic checks for the most common HTTP / business error patterns so adapter code
 * can remain concise and expressive.
 */
public final class RemoteErrorHelper {

  private RemoteErrorHelper() {}

  /**
   * Determine whether the error indicates a not-found condition (HTTP 404 or error code ending with
   * {@code -0404}).
   */
  public static boolean isNotFound(RemoteCallException ex) {
    return ex.getHttpStatus() == 404
        || (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0404"));
  }

  /**
   * Determine whether the error indicates a conflict (HTTP 409 or error code ending with {@code
   * -0409}).
   */
  public static boolean isConflict(RemoteCallException ex) {
    return ex.getHttpStatus() == 409
        || (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0409"));
  }

  /**
   * Determine whether the error indicates an unauthorized request (HTTP 401 or error code ending
   * with {@code -0401}).
   */
  public static boolean isUnauthorized(RemoteCallException ex) {
    return ex.getHttpStatus() == 401
        || (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0401"));
  }

  /**
   * Determine whether the error indicates a forbidden request (HTTP 403 or error code ending with
   * {@code -0403}).
   */
  public static boolean isForbidden(RemoteCallException ex) {
    return ex.getHttpStatus() == 403
        || (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0403"));
  }

  /**
   * Determine whether the error indicates an unprocessable entity (HTTP 422 or error code ending
   * with {@code -0422}).
   */
  public static boolean isUnprocessableEntity(RemoteCallException ex) {
    return ex.getHttpStatus() == 422
        || (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0422"));
  }

  /**
   * Determine whether the error indicates a rate-limit breach (HTTP 429 or error code ending with
   * {@code -0429}).
   */
  public static boolean isTooManyRequests(RemoteCallException ex) {
    return ex.getHttpStatus() == 429
        || (ex.getErrorCode() != null && ex.getErrorCode().endsWith("-0429"));
  }

  /**
   * @return {@code true} if the downstream call resulted in a 4xx status code.
   */
  public static boolean isClientError(RemoteCallException ex) {
    return ex.getHttpStatus() >= 400 && ex.getHttpStatus() < 500;
  }

  /**
   * @return {@code true} if the downstream call resulted in a 5xx status code.
   */
  public static boolean isServerError(RemoteCallException ex) {
    return ex.getHttpStatus() >= 500 && ex.getHttpStatus() < 600;
  }

  /** Check whether the downstream business error code matches the supplied value. */
  public static boolean is(RemoteCallException ex, String errorCode) {
    return errorCode != null && errorCode.equals(ex.getErrorCode());
  }

  /**
   * @return {@code true} if a downstream business error code is present.
   */
  public static boolean hasErrorCode(RemoteCallException ex) {
    return ex.hasErrorCode();
  }

  /**
   * @return {@code true} if a downstream trace identifier is present.
   */
  public static boolean hasTraceId(RemoteCallException ex) {
    return ex.hasTraceId();
  }

  /** Check whether the downstream business error code is one of the provided values. */
  public static boolean isAnyOf(RemoteCallException ex, String... errorCodes) {
    if (errorCodes == null || errorCodes.length == 0) {
      return false;
    }

    String actualCode = ex.getErrorCode();
    if (actualCode == null) {
      return false;
    }

    for (String code : errorCodes) {
      if (actualCode.equals(code)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Determine whether the error represents a potentially retryable transient failure (e.g. 5xx,
   * 429, 408, 503, 504).
   */
  public static boolean isRetryable(RemoteCallException ex) {
    int status = ex.getHttpStatus();
    return isServerError(ex)
        || status == 429
        || // Too Many Requests
        status == 408
        || // Request Timeout
        status == 503
        || // Service Unavailable
        status == 504; // Gateway Timeout
  }
}
