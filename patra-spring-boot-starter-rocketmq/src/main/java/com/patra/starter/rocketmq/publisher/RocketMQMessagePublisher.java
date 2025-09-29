package com.patra.starter.rocketmq.publisher;

import com.patra.common.error.ApplicationException;
import com.patra.common.error.codes.HttpStdErrors;
import com.patra.starter.rocketmq.config.PatraRocketMQProperties;
import com.patra.starter.rocketmq.model.PatraMessage;
import com.patra.starter.rocketmq.support.DestinationBuilder;
import com.patra.starter.rocketmq.support.EnvNamespaceResolver;
import com.patra.starter.rocketmq.support.TopicNameValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.env.Environment;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * 基于 RocketMQ 官方 starter 的发布实现。
 */
@Slf4j
public record RocketMQMessagePublisher(RocketMQTemplate rocketMQTemplate,
                                       PatraRocketMQProperties properties,
                                       Environment environment,
                                       ObjectProvider<HttpStdErrors.Group> httpErrorsProvider) implements PatraMessagePublisher {

    @Override
    public void send(String destination, PatraMessage<?> message) {
        try {
            // 使用按 profile 推导的 namespace 进行 topic 校验
            PatraRocketMQProperties.Naming effective = new PatraRocketMQProperties.Naming();
            effective.setNamespace(EnvNamespaceResolver.resolve(environment, properties.getNaming()));
            effective.setTopicPattern(properties.getNaming().getTopicPattern());
            TopicNameValidator.validate(destination, effective);
        } catch (IllegalArgumentException e) {
            throw asApp422("MQ 目的地不合法: " + destination + ", " + e.getMessage(), e);
        }
        Message<?> mqMessage = MessageBuilder.withPayload(message)
                .setHeaderIfAbsent("eventId", message.getEventId())
                .setHeaderIfAbsent("traceId", message.getTraceId())
                .setHeaderIfAbsent("occurredAt", message.getOccurredAt())
                .build();
        if (log.isDebugEnabled()) {
            log.debug("发送 MQ 消息 destination={} eventId={}", destination, message.getEventId());
        }
        rocketMQTemplate.convertAndSend(destination, mqMessage);
    }

    @Override
    public void sendByChannel(String channel, PatraMessage<?> message) {
        try {
            // 依据 profile 推导 namespace 并构建目的地
            String ns = EnvNamespaceResolver.resolve(environment, properties.getNaming());
            PatraRocketMQProperties.Naming effective = new PatraRocketMQProperties.Naming();
            effective.setNamespace(ns);
            effective.setTagDelimiter(properties.getNaming().getTagDelimiter());
            effective.setTopicPattern(properties.getNaming().getTopicPattern());
            String destination = DestinationBuilder.fromChannel(channel, effective);
            send(destination, message);
        } catch (IllegalArgumentException e) {
            throw asApp422("MQ channel 不合法: " + channel + ", " + e.getMessage(), e);
        }
    }

    @Override
    public void sendOrderly(String destination, PatraMessage<?> message, String hashKey) {
        try {
            PatraRocketMQProperties.Naming effective = new PatraRocketMQProperties.Naming();
            effective.setNamespace(EnvNamespaceResolver.resolve(environment, properties.getNaming()));
            effective.setTopicPattern(properties.getNaming().getTopicPattern());
            TopicNameValidator.validate(destination, effective);
        } catch (IllegalArgumentException e) {
            throw asApp422("MQ 目的地不合法(顺序消息): " + destination + ", " + e.getMessage(), e);
        }
        Message<?> mqMessage = MessageBuilder.withPayload(message)
                .setHeaderIfAbsent("eventId", message.getEventId())
                .setHeaderIfAbsent("traceId", message.getTraceId())
                .setHeaderIfAbsent("occurredAt", message.getOccurredAt())
                .build();
        if (log.isDebugEnabled()) {
            log.debug("发送顺序消息 destination={} eventId={} hashKey={}", destination, message.getEventId(), hashKey);
        }
        rocketMQTemplate.syncSendOrderly(destination, mqMessage, hashKey);
    }

    @Override
    public void sendDelay(String destination, PatraMessage<?> message, long timeoutMs, int delayLevel) {
        try {
            PatraRocketMQProperties.Naming effective = new PatraRocketMQProperties.Naming();
            effective.setNamespace(EnvNamespaceResolver.resolve(environment, properties.getNaming()));
            effective.setTopicPattern(properties.getNaming().getTopicPattern());
            TopicNameValidator.validate(destination, effective);
        } catch (IllegalArgumentException e) {
            throw asApp422("MQ 目的地不合法(延迟消息): " + destination + ", " + e.getMessage(), e);
        }
        Message<?> mqMessage = MessageBuilder.withPayload(message)
                .setHeaderIfAbsent("eventId", message.getEventId())
                .setHeaderIfAbsent("traceId", message.getTraceId())
                .setHeaderIfAbsent("occurredAt", message.getOccurredAt())
                .build();
        if (log.isDebugEnabled()) {
            log.debug("发送延迟消息 destination={} eventId={} delayLevel={}", destination, message.getEventId(), delayLevel);
        }
        rocketMQTemplate.syncSend(destination, mqMessage, timeoutMs, delayLevel);
    }

    /**
     * 将参数/命名类异常转为平台统一异常（422）。
     */
    private ApplicationException asApp422(String msg, Throwable cause) {
        HttpStdErrors.Group group = httpErrorsProvider != null ? httpErrorsProvider.getIfAvailable() : null;
        if (group == null) {
            group = HttpStdErrors.of("UNKNOWN");
        }
        return new ApplicationException(group.UNPROCESSABLE(), msg, cause);
    }
}
