package com.patra.starter.logging.autoconfigure;

import com.patra.common.logging.context.LogContextEnricher;
import com.patra.common.logging.context.TraceContextHolder;
import com.patra.starter.logging.filter.TraceContextFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;

/**
 * Auto-configuration for trace context propagation in Servlet environments.
 *
 * <p>Only activated in Servlet-based web applications (not WebFlux/Reactive).
 *
 * <p>Registers the {@link TraceContextFilter} to extract/generate trace context from incoming HTTP
 * requests.
 *
 * @see TraceContextFilter
 * @since 0.1.0
 */
@AutoConfiguration(after = LoggingAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ServletTraceContextAutoConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(ServletTraceContextAutoConfiguration.class);

  public ServletTraceContextAutoConfiguration() {
    log.info("Initializing Servlet Trace Context Propagation");
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
  public FilterRegistrationBean<TraceContextFilter> traceContextFilter(
      TraceContextHolder holder, LogContextEnricher enricher) {

    log.debug("Registering TraceContextFilter at HIGHEST_PRECEDENCE");

    TraceContextFilter filter = new TraceContextFilter(holder, enricher);
    FilterRegistrationBean<TraceContextFilter> registration = new FilterRegistrationBean<>(filter);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
    registration.addUrlPatterns("/*");
    return registration;
  }
}
