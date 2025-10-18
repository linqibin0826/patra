package com.patra.starter.logging.autoconfigure;

import com.patra.common.logging.context.DefaultLogContextEnricher;
import com.patra.common.logging.context.LogContextEnricher;
import com.patra.common.logging.context.TraceContextHolder;
import com.patra.common.logging.sanitizer.DefaultLogSanitizer;
import com.patra.common.logging.sanitizer.LogSanitizer;
import com.patra.starter.logging.aspect.ExceptionLoggingAspect;
import com.patra.starter.logging.context.DefaultTraceContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * Base auto-configuration for the logging starter.
 *
 * <p>This configuration registers foundational beans required by all logging features:
 *
 * <ul>
 *   <li>{@link TraceContextHolder}: Manages distributed trace context
 *   <li>{@link LogContextEnricher}: Enriches MDC with trace information
 *   <li>{@link LogSanitizer}: Sanitizes sensitive data from logs
 *   <li>{@link ExceptionLoggingAspect}: Automatic exception logging (Phase 3)
 * </ul>
 *
 * <p>All beans are registered with {@code @ConditionalOnMissingBean} to allow custom
 * implementations.
 *
 * <p>AspectJ proxy support is enabled for exception logging aspect.
 *
 * @since 0.1.0
 */
@Configuration
@EnableAspectJAutoProxy
public class LoggingAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(LoggingAutoConfiguration.class);

  public LoggingAutoConfiguration() {
    log.info("Initializing Papertrace Logging Starter (Phase 2-3 - Foundational + US1)");
  }

  /**
   * Registers the default trace context holder.
   *
   * <p>Integrates with SkyWalking for trace ID/span ID retrieval.
   *
   * @return Default trace context holder bean
   */
  @Bean
  @ConditionalOnMissingBean
  public TraceContextHolder traceContextHolder() {
    log.debug("Registering DefaultTraceContextHolder bean");
    return new DefaultTraceContextHolder();
  }

  /**
   * Registers the default log context enricher.
   *
   * <p>Uses SLF4J MDC for adding trace context to log messages.
   *
   * @return Default log context enricher bean
   */
  @Bean
  @ConditionalOnMissingBean
  public LogContextEnricher logContextEnricher() {
    log.debug("Registering DefaultLogContextEnricher bean");
    return new DefaultLogContextEnricher();
  }

  /**
   * Registers the default log sanitizer.
   *
   * <p>Provides hardcoded regex patterns for detecting and redacting sensitive data.
   *
   * @return Default log sanitizer bean
   */
  @Bean
  @ConditionalOnMissingBean
  public LogSanitizer logSanitizer() {
    log.debug("Registering DefaultLogSanitizer bean");
    return new DefaultLogSanitizer();
  }

  /**
   * Registers the exception logging aspect (T031a).
   *
   * <p>Automatically logs all exceptions with context capture.
   *
   * @param sanitizer Log sanitizer for argument sanitization
   * @return Exception logging aspect bean
   */
  @Bean
  @ConditionalOnMissingBean
  public ExceptionLoggingAspect exceptionLoggingAspect(LogSanitizer sanitizer) {
    log.debug("Registering ExceptionLoggingAspect bean");
    return new ExceptionLoggingAspect(sanitizer);
  }
}
