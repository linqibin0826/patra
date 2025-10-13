package com.patra.starter.web.error.spi;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * SPI for contributing Web-specific extension fields to {@link
 * org.springframework.http.ProblemDetail}. Provides access to {@link
 * jakarta.servlet.http.HttpServletRequest} so implementations can extract request context for
 * diagnostics.
 *
 * @author linqibin
 * @since 0.1.0
 * @see com.patra.starter.web.error.builder.ProblemDetailBuilder
 */
public interface WebProblemFieldContributor {

  /**
   * Contribute extension fields to the {@code ProblemDetail}, including Web-specific context.
   *
   * @param fields mutable map that will be merged into the response
   * @param exception current exception
   * @param request HTTP request
   */
  void contribute(Map<String, Object> fields, Throwable exception, HttpServletRequest request);
}
