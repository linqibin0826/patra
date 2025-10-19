package com.patra.starter.logging.autoconfigure;

import com.patra.common.logging.context.TraceContextHolder;
import com.patra.common.logging.sanitizer.LogSanitizer;
import com.patra.starter.logging.interceptor.ApiCallLoggingRestTemplateInterceptor;
import com.patra.starter.logging.interceptor.RestTemplateInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.web.client.RestTemplate;

/**
 * Auto-configuration for trace context propagation in RestTemplate environments.
 *
 * <p>Registers {@link RestTemplateInterceptor} and {@link ApiCallLoggingRestTemplateInterceptor}
 * for outbound HTTP requests made via RestTemplate.
 *
 * <p>For other trace propagation mechanisms, see:
 *
 * <ul>
 *   <li>{@link ServletTraceContextAutoConfiguration}: Servlet filter for incoming requests
 *   <li>{@link FeignTraceContextAutoConfiguration}: Feign client interceptors
 *   <li>{@link GatewayTraceContextAutoConfiguration}: WebFlux Gateway filter
 * </ul>
 *
 * @see RestTemplateInterceptor
 * @see ApiCallLoggingRestTemplateInterceptor
 * @since 0.1.0
 */
@AutoConfiguration(after = LoggingAutoConfiguration.class)
public class TraceContextAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(TraceContextAutoConfiguration.class);

  public TraceContextAutoConfiguration() {
    log.info("Initializing Trace Context Propagation for RestTemplate");
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
      @Lazy RestTemplateInterceptor traceInterceptor, LogSanitizer sanitizer) {
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
}
