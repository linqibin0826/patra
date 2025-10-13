package com.patra.common.error.codes;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for HTTP-aligned standard error codes (the 0xxx segment).
 *
 * <p>Each service owns a distinct prefix (for example, ING or REG) while the 0xxx suffixes map
 * directly to HTTP semantics. This factory generates prefixed error-code objects to avoid
 * duplication across modules.
 */
public final class HttpStdErrors {

  private HttpStdErrors() {}

  private static final Map<String, Group> CACHE = new ConcurrentHashMap<>();

  /**
   * Returns a lazily cached group of standard HTTP error codes for the given prefix.
   *
   * @param prefix error-code prefix (for example, {@code ING} or {@code REG}); blank values fall
   *     back to {@code UNKNOWN}
   * @return group of prefixed standard HTTP error codes
   */
  public static Group of(String prefix) {
    String p = (prefix == null || prefix.isBlank()) ? "UNKNOWN" : prefix;
    return CACHE.computeIfAbsent(p, Group::new);
  }

  /** Group of standard HTTP error codes bound to a specific prefix. */
  public static final class Group {
    private final String prefix;

    private Group(String prefix) {
      this.prefix = prefix;
    }

    // 4xx
    public ErrorCodeLike BAD_REQUEST() {
      return code("0400", 400);
    }

    public ErrorCodeLike UNAUTHORIZED() {
      return code("0401", 401);
    }

    public ErrorCodeLike FORBIDDEN() {
      return code("0403", 403);
    }

    public ErrorCodeLike NOT_FOUND() {
      return code("0404", 404);
    }

    public ErrorCodeLike CONFLICT() {
      return code("0409", 409);
    }

    public ErrorCodeLike UNPROCESSABLE() {
      return code("0422", 422);
    }

    public ErrorCodeLike TOO_MANY() {
      return code("0429", 429);
    }

    // 5xx
    public ErrorCodeLike INTERNAL_ERROR() {
      return code("0500", 500);
    }

    public ErrorCodeLike UNAVAILABLE() {
      return code("0503", 503);
    }

    public ErrorCodeLike GATEWAY_TIMEOUT() {
      return code("0504", 504);
    }

    private ErrorCodeLike code(String suffix, int status) {
      final String value = prefix + "-" + suffix;
      final int http = status;
      return new ErrorCodeLike() {
        @Override
        public String code() {
          return value;
        }

        @Override
        public int httpStatus() {
          return http;
        }

        @Override
        public String toString() {
          return value;
        }
      };
    }
  }
}
