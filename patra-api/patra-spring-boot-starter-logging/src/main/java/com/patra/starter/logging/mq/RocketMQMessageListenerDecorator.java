package com.patra.starter.logging.mq;

import com.patra.common.logging.context.DistributedTraceContext;
import com.patra.common.logging.context.LogContextEnricher;
import com.patra.common.logging.context.TraceContextHolder;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decorator for RocketMQ message listeners to propagate trace context.
 *
 * <p>RocketMQ consumers run in separate threads managed by the MQ broker, so trace context must be
 * explicitly extracted from message properties and restored.
 *
 * <p>Message Properties (set by producers):
 *
 * <ul>
 *   <li><b>traceId</b>: Distributed trace identifier
 *   <li><b>spanId</b>: Span identifier (becomes parent span for consumer processing)
 *   <li><b>correlationId</b>: Business correlation identifier
 * </ul>
 *
 * <p><b>Auto-Configuration:</b> This decorator is automatically registered as a Spring Bean when
 * RocketMQ is on the classpath. Access via dependency injection:
 *
 * <pre>{@code
 * @RocketMQMessageListener(topic = "my-topic", consumerGroup = "my-group")
 * public class MyConsumer implements RocketMQListener<MessageExt> {
 *
 *     @Autowired
 *     private RocketMQMessageListenerDecorator decorator;
 *
 *     @Override
 *     public void onMessage(MessageExt messageExt) {
 *         decorator.withTraceContext(messageExt.getUserProperties(), () -> {
 *             // Process message with trace context
 *             log.info("Processing message: {}", new String(messageExt.getBody()));
 *         });
 *     }
 * }
 * }</pre>
 *
 * <p><b>Legacy Static Usage:</b> For compatibility, static methods are still available:
 *
 * <pre>
 * RocketMQMessageListenerDecorator.withTraceContext(
 *     holder, enricher, messageExt.getUserProperties(),
 *     () -&gt; processMessage()
 * );
 * </pre>
 *
 * @see TraceContextHolder
 * @see LogContextEnricher
 * @since 0.1.0
 */
public class RocketMQMessageListenerDecorator {

  private static final Logger log = LoggerFactory.getLogger(RocketMQMessageListenerDecorator.class);

  private static final String PROPERTY_TRACE_ID = "traceId";
  private static final String PROPERTY_SPAN_ID = "spanId";
  private static final String PROPERTY_CORRELATION_ID = "correlationId";

  private final TraceContextHolder holder;
  private final LogContextEnricher enricher;

  /**
   * Constructs a new RocketMQ message listener decorator.
   *
   * <p>This constructor is used by Spring auto-configuration to create a managed bean.
   *
   * @param holder Trace context holder
   * @param enricher Log context enricher
   */
  public RocketMQMessageListenerDecorator(TraceContextHolder holder, LogContextEnricher enricher) {
    this.holder = holder;
    this.enricher = enricher;
  }

  /**
   * Executes a task with trace context restored from RocketMQ message properties (instance method).
   *
   * <p>Use this method when the decorator is injected as a Spring bean.
   *
   * @param messageProperties Message properties (user properties from MessageExt)
   * @param task The task to execute
   */
  public void withTraceContext(java.util.Map<String, String> messageProperties, Runnable task) {
    withTraceContext(holder, enricher, messageProperties, task);
  }

  /**
   * Executes a task with trace context restored from RocketMQ message properties (static method for
   * legacy usage).
   *
   * @param holder Trace context holder
   * @param enricher Log context enricher
   * @param messageProperties Message properties (user properties from MessageExt)
   * @param task The task to execute
   */
  public static void withTraceContext(
      TraceContextHolder holder,
      LogContextEnricher enricher,
      java.util.Map<String, String> messageProperties,
      Runnable task) {

    try {
      // Extract trace context from message properties
      DistributedTraceContext context = extractTraceContext(messageProperties);

      if (context != null) {
        holder.setContext(context);
        enricher.enrich(context);
        log.debug(
            "Restored trace context from RocketMQ message [traceId={}, correlationId={}]",
            context.traceId(),
            context.correlationId().orElse("N/A"));
      } else {
        log.warn("No trace context found in RocketMQ message properties");
      }

      // Execute task
      task.run();

    } finally {
      // Clean up
      enricher.clear();
      holder.clearContext();
    }
  }

  /**
   * Extracts trace context from RocketMQ message properties.
   *
   * @param properties Message properties
   * @return Trace context, or null if trace ID is missing
   */
  private static DistributedTraceContext extractTraceContext(
      java.util.Map<String, String> properties) {

    if (properties == null || properties.isEmpty()) {
      return null;
    }

    String traceId = properties.get(PROPERTY_TRACE_ID);
    String spanId = properties.get(PROPERTY_SPAN_ID);
    String correlationId = properties.get(PROPERTY_CORRELATION_ID);

    if (traceId == null || traceId.isBlank()) {
      return null;
    }

    // Generate new span ID if missing (consumer creates child span)
    if (spanId == null || spanId.isBlank()) {
      spanId = java.util.UUID.randomUUID().toString();
    }

    return new DistributedTraceContext(
        traceId,
        spanId,
        Optional.empty(), // Parent span can be added if needed
        Optional.ofNullable(correlationId).filter(s -> !s.isBlank()));
  }

  /**
   * Adds trace context to RocketMQ message properties (for producers).
   *
   * @param holder Trace context holder
   * @param properties Message properties (mutable map)
   */
  public static void propagateTraceContext(
      TraceContextHolder holder, java.util.Map<String, String> properties) {

    holder
        .getContext()
        .ifPresentOrElse(
            context -> {
              properties.put(PROPERTY_TRACE_ID, context.traceId());
              properties.put(PROPERTY_SPAN_ID, context.spanId());
              context
                  .correlationId()
                  .ifPresent(
                      correlationId -> properties.put(PROPERTY_CORRELATION_ID, correlationId));

              log.debug(
                  "Propagated trace context to RocketMQ message [traceId={}, correlationId={}]",
                  context.traceId(),
                  context.correlationId().orElse("N/A"));
            },
            () -> log.warn("No trace context available for RocketMQ message propagation"));
  }
}
