package com.patra.starter.logging.autoconfigure;

import com.patra.common.logging.context.LogContextEnricher;
import com.patra.common.logging.context.TraceContextHolder;
import com.patra.starter.logging.xxljob.XxlJobTraceContextDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for XXL-Job trace context support.
 *
 * <p>Automatically registers {@link XxlJobTraceContextDecorator} when XXL-Job is on the classpath.
 * This allows scheduled tasks to automatically establish trace context for logging and monitoring.
 *
 * <h3>Activation Conditions:</h3>
 *
 * <ul>
 *   <li>XXL-Job annotation {@code @XxlJob} is present on classpath
 *   <li>Property {@code patra.logging.xxljob.enabled} is {@code true} (default)
 * </ul>
 *
 * <h3>Usage Example:</h3>
 *
 * <pre>{@code
 * @Component
 * public class MyScheduledJob {
 *     @Autowired
 *     private XxlJobTraceContextDecorator decorator;
 *
 *     @XxlJob("dailyReport")
 *     public void execute() {
 *         decorator.withTraceContext("dailyReport", () -> {
 *             log.info("Executing daily report"); // [traceId=xxx][correlationId=job-dailyReport-xxx]
 *             // Your job logic here
 *         });
 *     }
 * }
 * }</pre>
 *
 * <h3>Configuration:</h3>
 *
 * <pre>
 * # application.yml
 * patra:
 *   logging:
 *     xxljob:
 *       enabled: true  # Default: true
 * </pre>
 *
 * @see XxlJobTraceContextDecorator
 * @see TraceContextHolder
 * @see LogContextEnricher
 * @since 0.1.0
 */
@AutoConfiguration(after = LoggingAutoConfiguration.class)
@ConditionalOnClass(name = "com.xxl.job.core.handler.annotation.XxlJob")
@ConditionalOnProperty(
    prefix = "patra.logging.xxljob",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class XxlJobTraceContextAutoConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(XxlJobTraceContextAutoConfiguration.class);

  public XxlJobTraceContextAutoConfiguration() {
    log.info(
        "Initializing XXL-Job Trace Context Support - scheduled tasks will automatically establish trace context");
  }

  /**
   * Registers the XXL-Job trace context decorator.
   *
   * <p>This decorator provides utility methods to wrap XXL-Job handlers with trace context
   * generation and propagation to MDC.
   *
   * @param holder Trace context holder
   * @param enricher Log context enricher
   * @return XXL-Job trace context decorator
   */
  @Bean
  public XxlJobTraceContextDecorator xxlJobTraceContextDecorator(
      TraceContextHolder holder, LogContextEnricher enricher) {
    log.debug("Registering XxlJobTraceContextDecorator for scheduled task trace context support");
    return new XxlJobTraceContextDecorator(holder, enricher);
  }
}
