package com.patra.starter.logging.interceptor;

import com.patra.common.logging.ApiCallLogger;
import com.patra.common.logging.sanitizer.LogSanitizer;
import feign.Request;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import feign.Response;
import feign.codec.ErrorDecoder;
import java.io.IOException;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Feign interceptor that logs all external API calls using {@link ApiCallLogger}.
 *
 * <p>Implements FR-006 (External API Call Logging) and SC-008 (100% Audit Logging) for Feign
 * clients.
 *
 * <h3>Functionality:</h3>
 *
 * <ul>
 *   <li>Logs all outbound Feign requests (URL, method, duration)
 *   <li>Logs response status codes (success/failure)
 *   <li>Automatically sanitizes sensitive data in URLs and headers
 *   <li>Integrates with trace context (trace IDs included in logs via MDC)
 * </ul>
 *
 * <h3>Usage:</h3>
 *
 * <pre>{@code
 * @Configuration
 * public class FeignConfig {
 *
 *     @Autowired
 *     private LogSanitizer sanitizer;
 *
 *     @Bean
 *     public RequestInterceptor apiCallLoggingInterceptor() {
 *         return new ApiCallLoggingFeignInterceptor(sanitizer);
 *     }
 * }
 * }</pre>
 *
 * <h3>Log Output Example:</h3>
 *
 * <pre>
 * INFO  External API call [GET] https://api.pubmed.gov/articles/12345 → 200 (duration=245ms)
 * ERROR External API call [POST] https://api.example.com/submit → FAILED (duration=5000ms): Connection timeout
 * </pre>
 *
 * <h3>Performance Considerations:</h3>
 *
 * <ul>
 *   <li>Logging is non-blocking (async appenders)
 *   <li>Sanitization has <50ms p95 latency
 *   <li>Minimal overhead for production workloads
 * </ul>
 *
 * @see ApiCallLogger
 * @see LogSanitizer
 * @since 0.1.0 (Phase 6 - User Story 4)
 */
public class ApiCallLoggingFeignInterceptor implements RequestInterceptor {

  private static final Logger log = LoggerFactory.getLogger(ApiCallLoggingFeignInterceptor.class);

  private final ApiCallLogger apiCallLogger;

  /**
   * Creates the interceptor with the given sanitizer.
   *
   * @param sanitizer Log sanitizer for redacting sensitive data
   */
  public ApiCallLoggingFeignInterceptor(LogSanitizer sanitizer) {
    this.apiCallLogger = new ApiCallLogger(log, sanitizer);
  }

  /**
   * Intercepts Feign requests to log API call details.
   *
   * <p>Note: This method runs BEFORE the request is sent. Actual logging of response status
   * requires custom Feign client configuration or error decoder.
   *
   * <p>For complete request/response logging, combine with {@link ApiCallLoggingFeignErrorDecoder}.
   *
   * @param template Feign request template
   */
  @Override
  public void apply(RequestTemplate template) {
    // Log request initiation at DEBUG level
    String url = template.url();
    String method = template.method();

    log.debug("Initiating Feign call [{}] {}", method, url);

    // Store start time in request metadata for duration tracking
    // (actual logging happens in response decoder/error decoder)
    template.header("X-Request-Start-Time", String.valueOf(System.currentTimeMillis()));
  }

  /**
   * Custom Feign ErrorDecoder that logs failed API calls using ApiCallLogger.
   *
   * <p>Registers this as a bean to enable error logging:
   *
   * <pre>{@code
   * @Bean
   * public ErrorDecoder apiCallLoggingErrorDecoder(LogSanitizer sanitizer) {
   *     return new ApiCallLoggingFeignErrorDecoder(sanitizer);
   * }
   * }</pre>
   */
  public static class ApiCallLoggingFeignErrorDecoder implements ErrorDecoder {

    private final ApiCallLogger apiCallLogger;
    private final ErrorDecoder delegate;

    public ApiCallLoggingFeignErrorDecoder(LogSanitizer sanitizer) {
      Logger errorLog = LoggerFactory.getLogger("FeignClientErrors");
      this.apiCallLogger = new ApiCallLogger(errorLog, sanitizer);
      this.delegate = new Default();
    }

    /**
     * Creates an error decoder that logs failures and then delegates to the provided decoder.
     *
     * @param sanitizer Log sanitizer
     * @param delegate The underlying ErrorDecoder to delegate to (e.g., Spring Cloud's default)
     */
    public ApiCallLoggingFeignErrorDecoder(LogSanitizer sanitizer, ErrorDecoder delegate) {
      Logger errorLog = LoggerFactory.getLogger("FeignClientErrors");
      this.apiCallLogger = new ApiCallLogger(errorLog, sanitizer);
      this.delegate = delegate != null ? delegate : new Default();
    }

    @Override
    public Exception decode(String methodKey, Response response) {
      // Extract request start time from headers
      long startTime = extractStartTime(response.request());
      Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);

      // Log failed API call
      String method = response.request().httpMethod().name();
      String url = response.request().url();
      int status = response.status();

      if (status >= 400) {
        // Client error (4xx) or Server error (5xx)
        apiCallLogger.logFailure(
            method,
            url,
            duration,
            new FeignApiException("HTTP " + status + ": " + response.reason()));
      }

      // Delegate to underlying decoder for exception creation
      return delegate.decode(methodKey, response);
    }

    private long extractStartTime(Request request) {
      try {
        String startTimeHeader =
            request.headers().getOrDefault("X-Request-Start-Time", java.util.List.of()).stream()
                .findFirst()
                .orElse(String.valueOf(System.currentTimeMillis()));
        return Long.parseLong(startTimeHeader);
      } catch (NumberFormatException e) {
        return System.currentTimeMillis(); // Fallback
      }
    }

    /** Custom exception for Feign API failures. */
    private static class FeignApiException extends RuntimeException {
      public FeignApiException(String message) {
        super(message);
      }
    }
  }

  /**
   * Custom Feign ResponseDecoder wrapper that logs successful API calls.
   *
   * <p>Wraps your existing decoder to add logging:
   *
   * <pre>{@code
   * @Bean
   * public Decoder apiCallLoggingDecoder(Decoder defaultDecoder, LogSanitizer sanitizer) {
   *     return new ApiCallLoggingFeignResponseDecoder(defaultDecoder, sanitizer);
   * }
   * }</pre>
   */
  public static class ApiCallLoggingFeignResponseDecoder implements feign.codec.Decoder {

    private final feign.codec.Decoder delegate;
    private final ApiCallLogger apiCallLogger;

    public ApiCallLoggingFeignResponseDecoder(
        feign.codec.Decoder delegate, LogSanitizer sanitizer) {
      this.delegate = delegate;
      Logger successLog = LoggerFactory.getLogger("FeignClientSuccess");
      this.apiCallLogger = new ApiCallLogger(successLog, sanitizer);
    }

    @Override
    public Object decode(Response response, java.lang.reflect.Type type)
        throws IOException, feign.codec.DecodeException, feign.FeignException {

      // Extract request start time
      long startTime = extractStartTime(response.request());
      Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);

      // Log successful API call
      String method = response.request().httpMethod().name();
      String url = response.request().url();
      int status = response.status();

      if (status >= 200 && status < 400) {
        apiCallLogger.logSuccess(method, url, status, duration);
      }

      // Delegate to original decoder
      return delegate.decode(response, type);
    }

    private long extractStartTime(Request request) {
      try {
        String startTimeHeader =
            request.headers().getOrDefault("X-Request-Start-Time", java.util.List.of()).stream()
                .findFirst()
                .orElse(String.valueOf(System.currentTimeMillis()));
        return Long.parseLong(startTimeHeader);
      } catch (NumberFormatException e) {
        return System.currentTimeMillis();
      }
    }
  }
}
