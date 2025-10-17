package com.patra.starter.logging.autoconfigure;

import com.patra.common.logging.context.LogContextEnricher;
import com.patra.common.logging.context.TraceContextHolder;
import com.patra.starter.logging.filter.TraceContextFilter;
import com.patra.starter.logging.interceptor.RestTemplateInterceptor;
import com.patra.starter.logging.interceptor.TraceContextInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
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
}
