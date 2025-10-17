package com.patra.starter.logging.xxljob;

import com.patra.common.logging.context.DistributedTraceContext;
import com.patra.common.logging.context.LogContextEnricher;
import com.patra.common.logging.context.TraceContextHolder;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorator for XXL-Job scheduled tasks to establish trace context.
 *
 * <p>XXL-Job tasks run in managed threads without incoming HTTP requests, so trace context must be
 * generated for each execution.
 *
 * <p>This decorator:
 *
 * <ul>
 *   <li>Generates new trace ID for each job execution
 *   <li>Uses job name + execution ID as correlation ID
 *   <li>Propagates context to MDC for logging
 *   <li>Integrates with SkyWalking (if agent is active)
 * </ul>
 *
 * <p>Usage: Wrap XXL-Job handler logic with trace context generation.
 *
 * <pre>{@code
 * @XxlJob("myScheduledJob")
 * public void execute() {
 *     XxlJobTraceContextDecorator.withTraceContext(
 *         traceContextHolder,
 *         logContextEnricher,
 *         "myScheduledJob",
 *         () -> {
 *             // Job logic with trace context
 *             log.info("Executing scheduled job"); // [traceId=xxx][correlationId=job-xxx]
 *         }
 *     );
 * }
 * }</pre>
 *
 * @see TraceContextHolder
 * @see LogContextEnricher
 * @since 0.1.0
 */
public class XxlJobTraceContextDecorator {

  private static final Logger log = LoggerFactory.getLogger(XxlJobTraceContextDecorator.class);

  /**
   * Executes an XXL-Job task with generated trace context.
   *
   * @param holder Trace context holder
   * @param enricher Log context enricher
   * @param jobName The XXL-Job handler name
   * @param task The task to execute
   */
  public static void withTraceContext(
      TraceContextHolder holder, LogContextEnricher enricher, String jobName, Runnable task) {

    try {
      // Generate new trace context for job execution
      String traceId = generateTraceId();
      String spanId = generateSpanId();
      String correlationId = generateCorrelationId(jobName);

      DistributedTraceContext context =
          DistributedTraceContext.withCorrelation(traceId, spanId, correlationId);

      // Store and enrich MDC
      holder.setContext(context);
      enricher.enrich(context);

      log.info(
          "Starting XXL-Job execution [job={}, traceId={}, correlationId={}]",
          jobName,
          traceId,
          correlationId);

      // Execute job
      task.run();

      log.info("Completed XXL-Job execution [job={}, traceId={}]", jobName, traceId);

    } catch (Exception e) {
      log.error("XXL-Job execution failed [job={}]", jobName, e);
      throw e;
    } finally {
      // Clean up
      enricher.clear();
      holder.clearContext();
    }
  }

  /**
   * Generates a new trace ID.
   *
   * @return UUID-based trace ID
   */
  private static String generateTraceId() {
    return UUID.randomUUID().toString();
  }

  /**
   * Generates a new span ID.
   *
   * @return UUID-based span ID
   */
  private static String generateSpanId() {
    return UUID.randomUUID().toString();
  }

  /**
   * Generates a correlation ID based on job name and timestamp.
   *
   * <p>Format: {@code job-<name>-<timestamp>}
   *
   * @param jobName The job name
   * @return Correlation ID
   */
  private static String generateCorrelationId(String jobName) {
    return String.format("job-%s-%d", jobName, System.currentTimeMillis());
  }
}
