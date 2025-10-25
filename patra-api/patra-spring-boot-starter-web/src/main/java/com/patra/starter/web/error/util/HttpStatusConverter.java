package com.patra.starter.web.error.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;

/**
 * Utility for converting integer status codes to {@link HttpStatus} with fallback handling.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public final class HttpStatusConverter {

  private HttpStatusConverter() {
    throw new UnsupportedOperationException("Utility class cannot be instantiated");
  }

  /**
   * Converts integer HTTP status code to {@link HttpStatus}, defaulting to 500 when invalid.
   *
   * @param statusCode HTTP status code as integer
   * @return corresponding {@link HttpStatus} or {@code INTERNAL_SERVER_ERROR} if invalid
   */
  public static HttpStatus toHttpStatus(int statusCode) {
    try {
      return HttpStatus.valueOf(statusCode);
    } catch (IllegalArgumentException e) {
      log.warn(
          "Invalid HTTP status code [{}] encountered, defaulting to 500 Internal Server Error",
          statusCode);
      return HttpStatus.INTERNAL_SERVER_ERROR;
    }
  }
}
