package com.patra.starter.logging.autoconfigure;

import com.patra.common.logging.context.LogContextEnricher;
import com.patra.common.logging.context.TraceContextHolder;
import com.patra.starter.logging.mq.RocketMQMessageListenerDecorator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for RocketMQ trace context support.
 *
 * <p>Automatically registers {@link RocketMQMessageListenerDecorator} when RocketMQ is on the
 * classpath. This allows message consumers to automatically extract and restore trace context from
 * message properties.
 *
 * <h3>Activation Conditions:</h3>
 *
 * <ul>
 *   <li>RocketMQ {@code MessageListener} interface is present on classpath
 *   <li>Property {@code patra.logging.rocketmq.enabled} is {@code true} (default)
 * </ul>
 *
 * <h3>Usage Example:</h3>
 *
 * <pre>{@code
 * @RocketMQMessageListener(topic = "my-topic", consumerGroup = "my-group")
 * public class MyConsumer implements RocketMQListener<MessageExt> {
 *     @Autowired
 *     private RocketMQMessageListenerDecorator decorator;
 *
 *     @Override
 *     public void onMessage(MessageExt messageExt) {
 *         decorator.withTraceContext(messageExt.getUserProperties(), () -> {
 *             log.info("Processing message"); // [traceId=xxx][correlationId=xxx]
 *             // Your message processing logic here
 *         });
 *     }
 * }
 * }</pre>
 *
 * <h3>Producer-Side Trace Propagation:</h3>
 *
 * <pre>{@code
 * @Autowired
 * private RocketMQMessageListenerDecorator decorator;
 *
 * public void sendMessage(String topic, String content) {
 *     Message message = new Message(topic, content.getBytes());
 *     decorator.propagateTraceContext(traceContextHolder, message.getUserProperties());
 *     rocketMQTemplate.send(topic, message);
 * }
 * }</pre>
 *
 * <h3>Configuration:</h3>
 *
 * <pre>
 * # application.yml
 * patra:
 *   logging:
 *     rocketmq:
 *       enabled: true  # Default: true
 * </pre>
 *
 * @see RocketMQMessageListenerDecorator
 * @see TraceContextHolder
 * @see LogContextEnricher
 * @since 0.1.0
 */
@AutoConfiguration(after = LoggingAutoConfiguration.class)
@ConditionalOnClass(name = "org.apache.rocketmq.client.consumer.listener.MessageListener")
@ConditionalOnProperty(
    prefix = "patra.logging.rocketmq",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class RocketMQTraceContextAutoConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(RocketMQTraceContextAutoConfiguration.class);

  public RocketMQTraceContextAutoConfiguration() {
    log.info(
        "Initializing RocketMQ Trace Context Support - message consumers will automatically extract trace context");
  }

  /**
   * Registers the RocketMQ message listener decorator.
   *
   * <p>This decorator provides utility methods to wrap RocketMQ message listeners with trace
   * context extraction from message properties and propagation to MDC.
   *
   * @param holder Trace context holder
   * @param enricher Log context enricher
   * @return RocketMQ message listener decorator
   */
  @Bean
  public RocketMQMessageListenerDecorator rocketMQMessageListenerDecorator(
      TraceContextHolder holder, LogContextEnricher enricher) {
    log.debug(
        "Registering RocketMQMessageListenerDecorator for message listener trace context support");
    return new RocketMQMessageListenerDecorator(holder, enricher);
  }
}
