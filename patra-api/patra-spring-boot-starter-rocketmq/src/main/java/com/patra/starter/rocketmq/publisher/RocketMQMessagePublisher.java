package com.patra.starter.rocketmq.publisher;

import com.patra.starter.rocketmq.config.PatraRocketMQProperties;
import com.patra.starter.rocketmq.model.PatraMessage;
import com.patra.starter.rocketmq.support.TopicNameValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

/**
 * 基于 RocketMQ 官方 starter 的发布实现。
 */
@Slf4j
@RequiredArgsConstructor
public class RocketMQMessagePublisher implements PatraMessagePublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final PatraRocketMQProperties properties;

    @Override
    public void send(String destination, PatraMessage<?> message) {
        TopicNameValidator.validate(destination, properties.getNaming());
        Message<PatraMessage<?>> mqMessage = (Message<PatraMessage<?>>) (Message<?>) MessageBuilder.withPayload(message)
                .setHeaderIfAbsent("eventId", message.getEventId())
                .setHeaderIfAbsent("traceId", message.getTraceId())
                .setHeaderIfAbsent("occurredAt", message.getOccurredAt())
                .build();
        if (log.isDebugEnabled()) {
            log.debug("发送 MQ 消息 destination={} eventId={}", destination, message.getEventId());
        }
        rocketMQTemplate.convertAndSend(destination, mqMessage);
    }
}
