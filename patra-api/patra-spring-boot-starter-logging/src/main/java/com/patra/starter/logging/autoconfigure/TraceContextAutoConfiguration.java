package com.patra.starter.logging.autoconfigure;

import com.patra.common.logging.context.LogContextEnricher;
import com.patra.common.logging.context.TraceContextHolder;
import com.patra.common.logging.sanitizer.LogSanitizer;
import com.patra.starter.logging.filter.TraceContextFilter;
import com.patra.starter.logging.interceptor.ApiCallLoggingFeignInterceptor;
import com.patra.starter.logging.interceptor.ApiCallLoggingRestTemplateInterceptor;
import com.patra.starter.logging.interceptor.RestTemplateInterceptor;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.Ordered;
import org.springframework.web.client.RestTemplate;

/**
 * Auto-configuration for trace context propagation.
 *
 * <p>Registers components for distributed tracing:
 *
 * <ul>
 *   <li>{@link TraceContextFilter}: Servlet filter for extracting/generating trace context
 *   <li>{@link TraceContextInterceptor}: Feign interceptor (if Feign is available)
 *   <li>{@link RestTemplateInterceptor}: RestTemplate interceptor factory
 * </ul>
 *
 * @see TraceContextFilter
 * @see TraceContextInterceptor
 * @see RestTemplateInterceptor
 * @since 0.1.0
 */
@AutoConfiguration(after = LoggingAutoConfiguration.class)
public class TraceContextAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(TraceContextAutoConfiguration.class);

  public TraceContextAutoConfiguration() {
    log.info("Initializing Trace Context Propagation (Phase 3 - US1)");
  }

  /**
   * Registers the trace context servlet filter.
   *
   * <p>Runs at highest precedence to ensure trace context is available for all downstream
   * processing.
   *
   * @param holder Trace context holder
   * @param enricher Log context enricher
   * @return Filter registration bean
   */
  @Bean
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  public FilterRegistrationBean<TraceContextFilter> traceContextFilter(
      TraceContextHolder holder, LogContextEnricher enricher) {

    log.debug("Registering TraceContextFilter at HIGHEST_PRECEDENCE");

    TraceContextFilter filter = new TraceContextFilter(holder, enricher);
    FilterRegistrationBean<TraceContextFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    registration.addUrlPatterns("/*");
    return registration;
  }

  /**
   * Registers the Feign trace context interceptor.
   *
   * <p>Only registered if Feign is on the classpath.
   *
   * @param holder Trace context holder
   * @return Feign request interceptor
   */
  @Bean
  @ConditionalOnClass(name = "feign.RequestInterceptor")
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
  @ConditionalOnClass(name = "feign.RequestInterceptor")
  public ApiCallLoggingFeignInterceptor apiCallLoggingFeignInterceptor(LogSanitizer sanitizer) {
    log.debug("Registering ApiCallLoggingFeignInterceptor for Feign clients");
    return new ApiCallLoggingFeignInterceptor(sanitizer);
  }

  /**
   * Provides a factory method for creating RestTemplate interceptors.
   *
   * <p>Applications should manually add this interceptor to RestTemplate beans:
   *
   * <pre>{@code
   * @Bean
   * public RestTemplate restTemplate(TraceContextHolder holder) {
   *     RestTemplate template = new RestTemplate();
   *     template.getInterceptors().add(restTemplateTraceInterceptor(holder));
   *     return template;
   * }
   * }</pre>
   *
   * @param holder Trace context holder
   * @return RestTemplate interceptor
   */
  @Bean
  @ConditionalOnClass(RestTemplate.class)
  public RestTemplateInterceptor restTemplateTraceInterceptor(TraceContextHolder holder) {
    log.debug("Providing RestTemplateInterceptor factory bean");
    return new RestTemplateInterceptor(holder);
  }

  /**
   * Customizes all RestTemplate beans to include both trace-propagation and API-call logging
   * interceptors. Safe to apply globally; existing interceptors are preserved.
   */
  @Bean
  @ConditionalOnClass(RestTemplate.class)
  public RestTemplateCustomizer restTemplateLoggingCustomizer(
      org.springframework.http.client.ClientHttpRequestInterceptor traceInterceptor,
      LogSanitizer sanitizer) {
    return restTemplate -> {
      // Detect existing trace interceptor by identity (works with JDK/CGLIB proxies)
      boolean hasTrace = restTemplate.getInterceptors().contains(traceInterceptor);
      boolean hasApiLog =
          restTemplate.getInterceptors().stream()
              .anyMatch(i -> i instanceof ApiCallLoggingRestTemplateInterceptor);

      if (!hasTrace) {
        restTemplate.getInterceptors().add(traceInterceptor);
      }
      if (!hasApiLog) {
        restTemplate.getInterceptors().add(new ApiCallLoggingRestTemplateInterceptor(sanitizer));
      }
    };
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
    private final com.patra.common.logging.ApiCallLogger apiCallLogger;
    private final ErrorDecoder delegate;

    DelegatingApiCallLoggingErrorDecoder(LogSanitizer sanitizer, ErrorDecoder delegate) {
      Logger errorLog = LoggerFactory.getLogger("FeignClientErrors");
      this.apiCallLogger = new com.patra.common.logging.ApiCallLogger(errorLog, sanitizer);
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
