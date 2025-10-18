package com.patra.starter.logging.autoconfigure;

import com.patra.common.logging.context.LogContextEnricher;
import com.patra.common.logging.context.TraceContextHolder;
import com.patra.starter.logging.gateway.TraceContextGlobalFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for trace context propagation in Spring Cloud Gateway (WebFlux).
 *
 * <p>Registers {@link TraceContextGlobalFilter} when Spring Cloud Gateway is detected on classpath.
 *
 * <p>Implements FR-003: Automatic trace context propagation across gateway → downstream services.
 *
 * <p>Implements SC-002: 100% trace ID coverage for synchronous operations through gateway.
 *
 * <h3>Conditional Activation:</h3>
 *
 * <ul>
 *   <li><strong>@ConditionalOnClass(GlobalFilter.class)</strong>: Only activates if Spring Cloud
 *       Gateway is on classpath
 *   <li><strong>@ConditionalOnWebApplication(type = REACTIVE)</strong>: Only activates for WebFlux
 *       applications
 *   <li><strong>@ConditionalOnMissingBean</strong>: Allows custom TraceContextGlobalFilter override
 * </ul>
 *
 * <h3>Architecture Note:</h3>
 *
 * <p>Spring Cloud Gateway uses WebFlux (reactive, non-blocking) model:
 *
 * <ul>
 *   <li>Servlet-based {@code TraceContextFilter} does NOT work (thread-per-request model)
 *   <li>WebFlux requires {@code GlobalFilter} with Reactor Context (async model)
 *   <li>This auto-configuration provides WebFlux-compatible trace propagation
 * </ul>
 *
 * <h3>Execution Order:</h3>
 *
 * <ol>
 *   <li>{@link LoggingAutoConfiguration} → Registers TraceContextHolder, LogContextEnricher
 *   <li>{@link TraceContextAutoConfiguration} → Registers servlet-based TraceContextFilter (for
 *       non-gateway services)
 *   <li><strong>This configuration</strong> → Registers WebFlux-based TraceContextGlobalFilter (for
 *       gateway only)
 * </ol>
 *
 * <h3>Testing:</h3>
 *
 * <ul>
 *   <li>Unit test: Verify filter is NOT registered when Gateway absent
 *   <li>Integration test (T058-T060): Verify trace propagation gateway → registry → ingest
 * </ul>
 *
 * @see TraceContextGlobalFilter
 * @see LoggingAutoConfiguration
 * @see TraceContextAutoConfiguration
 * @since 0.1.0 (Phase 5 - User Story 3)
 */
@AutoConfiguration(after = LoggingAutoConfiguration.class)
@ConditionalOnClass(GlobalFilter.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.REACTIVE)
public class GatewayTraceContextAutoConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(GatewayTraceContextAutoConfiguration.class);

  public GatewayTraceContextAutoConfiguration() {
    log.info(
        "Initializing GatewayTraceContextAutoConfiguration for Spring Cloud Gateway (Phase 5 -"
            + " US3)");
  }

  /**
   * Registers TraceContextGlobalFilter for WebFlux-based Spring Cloud Gateway.
   *
   * <p>This filter:
   *
   * <ul>
   *   <li>Extracts trace context from incoming request headers
   *   <li>Generates new trace IDs if missing (gateway is entry point)
   *   <li>Enriches Reactor Context with trace data (WebFlux-safe MDC alternative)
   *   <li>Propagates trace headers to downstream services
   *   <li>Clears context after request completes
   * </ul>
   *
   * @param traceContextHolder Holder for distributed trace context
   * @param logContextEnricher Enricher for MDC/Reactor Context
   * @return TraceContextGlobalFilter bean
   */
  @Bean
  @ConditionalOnMissingBean
  public TraceContextGlobalFilter traceContextGlobalFilter(
      TraceContextHolder traceContextHolder, LogContextEnricher logContextEnricher) {
    log.debug("Registering TraceContextGlobalFilter for Spring Cloud Gateway trace propagation");
    return new TraceContextGlobalFilter(traceContextHolder, logContextEnricher);
  }
}
