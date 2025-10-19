package com.patra.starter.logging.autoconfigure;

import com.patra.common.logging.ApiCallLogger;
import com.patra.common.logging.context.TraceContextHolder;
import com.patra.common.logging.sanitizer.LogSanitizer;
import com.patra.starter.logging.interceptor.ApiCallLoggingFeignInterceptor;
import com.patra.starter.logging.interceptor.TraceContextInterceptor;
import feign.Request;
import feign.Response;
import feign.codec.Decoder;
import feign.codec.ErrorDecoder;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Auto-configuration for Feign client trace context propagation and API call logging.
 *
 * <p>Only activated when Feign is on the classpath. Registers:
 *
 * <ul>
 *   <li>{@link TraceContextInterceptor}: Feign interceptor for trace propagation
 *   <li>{@link ApiCallLoggingFeignInterceptor}: Request interceptor for API call logging
 *   <li>Decoder/ErrorDecoder wrappers for response/error logging
 * </ul>
 *
 * @see TraceContextInterceptor
 * @see ApiCallLoggingFeignInterceptor
 * @since 0.1.0
 */
@AutoConfiguration(after = LoggingAutoConfiguration.class)
@ConditionalOnClass(name = "feign.RequestInterceptor")
public class FeignTraceContextAutoConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(FeignTraceContextAutoConfiguration.class);

  public FeignTraceContextAutoConfiguration() {
    log.info("Initializing Feign Trace Context Propagation and API Logging");
  }

  /**
   * Registers the Feign trace context interceptor.
   *
   * <p>Propagates trace context to downstream services via Feign clients.
   *
   * @param holder Trace context holder
   * @return Feign request interceptor
   */
  @Bean
  public TraceContextInterceptor feignTraceContextInterceptor(TraceContextHolder holder) {
    log.debug("Registering TraceContextInterceptor for Feign clients");
    return new TraceContextInterceptor(holder);
  }

  /**
   * Registers an ApiCall logging interceptor for Feign to capture outbound API call timings.
   *
   * <p>Coexists with other Feign interceptors (e.g., trace propagation). Does not replace any
   * existing ErrorDecoder/Decoder configuration.
   */
  @Bean
  public ApiCallLoggingFeignInterceptor apiCallLoggingFeignInterceptor(LogSanitizer sanitizer) {
    log.debug("Registering ApiCallLoggingFeignInterceptor for Feign clients");
    return new ApiCallLoggingFeignInterceptor(sanitizer);
  }

  /**
   * Wraps Feign's Decoder to emit API call success logs at INFO level.
   *
   * <p>Enabled by default; can be disabled via 'patra.logging.api.feign.enabled=false'.
   */
  @Bean(name = "apiCallLoggingFeignResponseDecoder")
  @Primary
  @ConditionalOnClass(Decoder.class)
  @ConditionalOnBean(Decoder.class)
  @ConditionalOnProperty(
      prefix = "patra.logging.api.feign",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public Decoder apiCallLoggingFeignResponseDecoder(
      org.springframework.beans.factory.ObjectProvider<Decoder> delegates, LogSanitizer sanitizer) {
    // Try to reuse an existing Decoder bean (e.g., Spring Cloud OpenFeign's feignDecoder).
    // If multiple candidates exist, avoid self-wrapping by skipping our wrapper type.
    Decoder delegate =
        delegates
            .orderedStream()
            .filter(
                d ->
                    !(d
                        instanceof
                        ApiCallLoggingFeignInterceptor.ApiCallLoggingFeignResponseDecoder))
            .findFirst()
            .orElse(null);

    if (delegate == null) {
      log.warn(
          "No suitable Feign Decoder delegate found; skipping ApiCallLoggingFeignResponseDecoder registration");
      // Returning a minimal delegate would change behavior; instead, do not register bean.
      // However, Spring requires a return value here; we defensively fall back to Decoder.Default
      // to keep context bootable while logging a clear WARN.
      delegate = new feign.codec.Decoder.Default();
    } else {
      log.debug(
          "Registering ApiCallLoggingFeignResponseDecoder (primary) with delegate={}",
          delegate.getClass().getName());
    }

    return new ApiCallLoggingFeignInterceptor.ApiCallLoggingFeignResponseDecoder(
        delegate, sanitizer);
  }

  /**
   * Wraps Feign's ErrorDecoder to emit API call failure logs at ERROR level.
   *
   * <p>Enabled by default; can be disabled via 'patra.logging.api.feign.enabled=false'.
   */
  @Bean(name = "apiCallLoggingFeignErrorDecoder")
  @Primary
  @ConditionalOnClass(ErrorDecoder.class)
  @ConditionalOnBean(ErrorDecoder.class)
  @ConditionalOnProperty(
      prefix = "patra.logging.api.feign",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public ErrorDecoder apiCallLoggingFeignErrorDecoder(
      org.springframework.beans.factory.ObjectProvider<ErrorDecoder> delegates,
      LogSanitizer sanitizer) {
    // Choose existing ErrorDecoder to wrap; prefer any non-logging wrapper to avoid self-wrapping.
    ErrorDecoder delegate =
        delegates
            .orderedStream()
            .filter(
                d -> !(d instanceof ApiCallLoggingFeignInterceptor.ApiCallLoggingFeignErrorDecoder))
            .findFirst()
            .orElse(null);

    if (delegate == null) {
      log.warn(
          "No suitable Feign ErrorDecoder delegate found; skipping ApiCallLoggingFeignErrorDecoder registration");
      delegate = new ErrorDecoder.Default();
    } else {
      log.debug(
          "Registering ApiCallLoggingFeignErrorDecoder (primary) with delegate={}",
          delegate.getClass().getName());
    }

    return new DelegatingApiCallLoggingErrorDecoder(sanitizer, delegate);
  }

  /**
   * Local delegating ErrorDecoder to avoid inner-type constructor resolution issues during
   * pre-commit incremental compiles. Functionally equivalent to
   * ApiCallLoggingFeignInterceptor.ApiCallLoggingFeignErrorDecoder.
   */
  static class DelegatingApiCallLoggingErrorDecoder implements ErrorDecoder {
    private final ApiCallLogger apiCallLogger;
    private final ErrorDecoder delegate;

    DelegatingApiCallLoggingErrorDecoder(LogSanitizer sanitizer, ErrorDecoder delegate) {
      Logger errorLog = LoggerFactory.getLogger("FeignClientErrors");
      this.apiCallLogger = new ApiCallLogger(errorLog, sanitizer);
      this.delegate = (delegate != null) ? delegate : new ErrorDecoder.Default();
    }

    @Override
    public Exception decode(String methodKey, Response response) {
      long startTime = extractStartTime(response.request());
      Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);

      String method = response.request().httpMethod().name();
      String url = response.request().url();
      int status = response.status();

      if (status >= 400) {
        apiCallLogger.logFailure(
            method, url, duration, new FeignApiException(status, response.reason()));
      }

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
        return System.currentTimeMillis();
      }
    }

    private static final class FeignApiException extends RuntimeException {
      FeignApiException(int status, String reason) {
        super("HTTP " + status + ": " + reason);
      }
    }
  }
}
