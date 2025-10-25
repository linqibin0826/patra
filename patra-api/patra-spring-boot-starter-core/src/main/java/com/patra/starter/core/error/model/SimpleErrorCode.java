package com.patra.starter.core.error.model;

import com.patra.common.error.codes.ErrorCodeLike;

/**
 * Simple immutable implementation of {@link ErrorCodeLike}.
 *
 * <p>This class provides a reusable error code representation to avoid duplicating anonymous
 * ErrorCodeLike instances throughout the codebase.
 */
public final class SimpleErrorCode implements ErrorCodeLike {

  private final String code;
  private final int httpStatus;

  private SimpleErrorCode(String code, int httpStatus) {
    this.code = code;
    this.httpStatus = httpStatus;
  }

  /**
   * Creates an error code from a context prefix and HTTP status suffix.
   *
   * @param contextPrefix the context prefix (e.g., "REG", "INGEST")
   * @param suffix the HTTP status code suffix (e.g., "0404", "0500")
   * @return newly created error code
   */
  public static SimpleErrorCode create(String contextPrefix, String suffix) {
    String prefix = (contextPrefix == null || contextPrefix.isBlank()) ? "UNKNOWN" : contextPrefix;
    String fullCode = prefix + "-" + suffix;
    int status = parseHttpStatus(suffix);
    return new SimpleErrorCode(fullCode, status);
  }

  /**
   * Parses HTTP status code from suffix, defaulting to 500 if invalid.
   *
   * @param suffix status code suffix
   * @return HTTP status code between 100-599, or 500 if invalid
   */
  private static int parseHttpStatus(String suffix) {
    try {
      int status = Integer.parseInt(suffix);
      return (status >= 100 && status <= 599) ? status : 500;
    } catch (NumberFormatException e) {
      return 500;
    }
  }

  @Override
  public String code() {
    return code;
  }

  @Override
  public int httpStatus() {
    return httpStatus;
  }

  @Override
  public String toString() {
    return code;
  }
}
