package com.patra.common.logging;

import com.patra.common.logging.sanitizer.LogSanitizer;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;

/**
 * Utility for standardized logging of external API calls.
 *
 * <p>Implements FR-006 (External API Call Logging) and SC-008 (100% Audit Logging).
 *
 * <h3>Purpose:</h3>
 *
 * Provides consistent logging format for all outbound HTTP/REST API calls across microservices,
 * including:
 *
 * <ul>
 *   <li>Request URL and HTTP method
 *   <li>Response status code
 *   <li>Call duration (performance monitoring)
 *   <li>Error details (for failures)
 *   <li>Automatic sanitization of sensitive data in URLs/headers
 * </ul>
 *
 * <h3>Usage Example:</h3>
 *
 * <pre>{@code
 * // In orchestrator or infrastructure layer
 * private static final Logger log = LoggerFactory.getLogger(MyService.class);
 * private final LogSanitizer sanitizer = new DefaultLogSanitizer();
 * private final ApiCallLogger apiCallLogger = new ApiCallLogger(log, sanitizer);
 *
 * public void callExternalApi() {
 *     String url = "https://api.pubmed.gov/articles/12345";
 *     long startTime = System.currentTimeMillis();
 *
 *     try {
 *         HttpResponse response = httpClient.get(url);
 *         apiCallLogger.logSuccess("GET", url, response.statusCode(),
 *                                  Duration.ofMillis(System.currentTimeMillis() - startTime));
 *     } catch (Exception e) {
 *         apiCallLogger.logFailure("GET", url,
 *                                 Duration.ofMillis(System.currentTimeMillis() - startTime), e);
 *     }
 * }
 * }</pre>
 *
 * <h3>Log Format:</h3>
 *
 * <pre>
 * SUCCESS: External API call [GET] https://api.example.com/data → 200 OK (duration=245ms)
 * FAILURE: External API call [POST] https://api.example.com/submit → Connection timeout (duration=5000ms): java.net.SocketTimeoutException: Read timed out
 * </pre>
 *
 * <h3>Sanitization:</h3>
 *
 * Automatically sanitizes:
 *
 * <ul>
 *   <li>API keys in query params: {@code ?apiKey=xxx} → {@code ?apiKey=***REDACTED***}
 *   <li>Tokens in URLs: {@code /token/abc123} → {@code /token/***REDACTED***}
 *   <li>Passwords in URLs: {@code ?password=xxx} → {@code ?password=***REDACTED***}
 * </ul>
 *
 * @since 0.1.0 (Phase 6 - User Story 4)
 * @see LogSanitizer
 */
public class ApiCallLogger {

  private final Logger logger;
  private final LogSanitizer sanitizer;

  /**
   * Creates an ApiCallLogger with the given logger and sanitizer.
   *
   * @param logger The SLF4J logger for the calling class
   * @param sanitizer The log sanitizer for redacting sensitive data
   */
  public ApiCallLogger(Logger logger, LogSanitizer sanitizer) {
    this.logger = logger;
    this.sanitizer = sanitizer;
  }

  /**
   * Logs a successful API call at INFO level.
   *
   * <p>Format: {@code External API call [METHOD] url → status (duration=XXXms)}
   *
   * @param method HTTP method (GET, POST, PUT, DELETE, etc.)
   * @param url Request URL (will be sanitized)
   * @param statusCode HTTP status code (e.g., 200, 201, 204)
   * @param duration Call duration
   */
  public void logSuccess(String method, String url, int statusCode, Duration duration) {
    String sanitizedUrl = sanitizer.sanitize(url);
    logger.info(
        "External API call [{}] {} → {} (duration={}ms)",
        method,
        sanitizedUrl,
        statusCode,
        duration.toMillis());
  }

  /**
   * Logs a failed API call at ERROR level.
   *
   * <p>Format: {@code External API call [METHOD] url → error (duration=XXXms): exception message}
   *
   * @param method HTTP method (GET, POST, PUT, DELETE, etc.)
   * @param url Request URL (will be sanitized)
   * @param duration Call duration
   * @param error The exception that caused the failure
   */
  public void logFailure(String method, String url, Duration duration, Throwable error) {
    String sanitizedUrl = sanitizer.sanitize(url);
    logger.error(
        "External API call [{}] {} → FAILED (duration={}ms): {}",
        method,
        sanitizedUrl,
        duration.toMillis(),
        error.getMessage(),
        error);
  }

  /**
   * Logs an API call with optional status code at DEBUG level.
   *
   * <p>Useful for debugging when you want to log both successful and failed calls at DEBUG level
   * instead of INFO/ERROR.
   *
   * @param method HTTP method
   * @param url Request URL (will be sanitized)
   * @param statusCode Optional HTTP status code (empty if request failed before receiving response)
   * @param duration Call duration
   */
  public void logDebug(String method, String url, Optional<Integer> statusCode, Duration duration) {
    String sanitizedUrl = sanitizer.sanitize(url);
    if (statusCode.isPresent()) {
      logger.debug(
          "External API call [{}] {} → {} (duration={}ms)",
          method,
          sanitizedUrl,
          statusCode.get(),
          duration.toMillis());
    } else {
      logger.debug(
          "External API call [{}] {} → NO RESPONSE (duration={}ms)",
          method,
          sanitizedUrl,
          duration.toMillis());
    }
  }

  /**
   * Logs a slow API call at WARN level.
   *
   * <p>Use this to highlight API calls that exceed expected duration thresholds.
   *
   * @param method HTTP method
   * @param url Request URL (will be sanitized)
   * @param statusCode HTTP status code
   * @param duration Call duration
   * @param threshold Expected duration threshold
   */
  public void logSlowCall(
      String method, String url, int statusCode, Duration duration, Duration threshold) {
    String sanitizedUrl = sanitizer.sanitize(url);
    logger.warn(
        "SLOW External API call [{}] {} → {} (duration={}ms, threshold={}ms)",
        method,
        sanitizedUrl,
        statusCode,
        duration.toMillis(),
        threshold.toMillis());
  }

  /**
   * Logs a retry attempt at WARN level.
   *
   * <p>Use this to track retry behavior for resilience monitoring.
   *
   * @param method HTTP method
   * @param url Request URL (will be sanitized)
   * @param attemptNumber Current retry attempt (1-based)
   * @param maxAttempts Maximum retry attempts
   * @param error The error that triggered the retry
   */
  public void logRetry(
      String method, String url, int attemptNumber, int maxAttempts, Throwable error) {
    String sanitizedUrl = sanitizer.sanitize(url);
    logger.warn(
        "Retrying External API call [{}] {} (attempt {}/{}) due to: {}",
        method,
        sanitizedUrl,
        attemptNumber,
        maxAttempts,
        error.getMessage());
  }

  /**
   * Logs request/response payload at TRACE level for debugging.
   *
   * <p>WARNING: Only use for non-production debugging. Payloads will be sanitized but may still
   * contain large amounts of data.
   *
   * @param method HTTP method
   * @param url Request URL (will be sanitized)
   * @param requestPayload Request body (will be sanitized)
   * @param responsePayload Response body (will be sanitized)
   */
  public void logPayloads(
      String method, String url, String requestPayload, String responsePayload) {
    if (logger.isTraceEnabled()) {
      String sanitizedUrl = sanitizer.sanitize(url);
      String sanitizedRequest = sanitizer.sanitize(requestPayload);
      String sanitizedResponse = sanitizer.sanitize(responsePayload);

      logger.trace(
          "External API call [{}] {}\nRequest: {}\nResponse: {}",
          method,
          sanitizedUrl,
          sanitizedRequest,
          sanitizedResponse);
    }
  }
}
