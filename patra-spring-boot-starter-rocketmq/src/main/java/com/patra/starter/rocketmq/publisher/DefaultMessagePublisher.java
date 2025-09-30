package com.patra.starter.rocketmq.publisher;

import com.patra.common.error.ApplicationException;
import com.patra.common.error.codes.HttpStdErrors;
import com.patra.starter.rocketmq.core.Channel;
import com.patra.starter.rocketmq.core.destination.Destination;
import com.patra.starter.rocketmq.core.destination.ChannelDestinationConverter;
import com.patra.starter.rocketmq.core.message.Message;
import com.patra.starter.rocketmq.core.message.MessageHeaderKeys;
import com.patra.starter.rocketmq.validation.TopicValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.support.MessageBuilder;

/**
 * 默认消息发布器实现。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
public class DefaultMessagePublisher implements MessagePublisher {

    private final RocketMQTemplate rocketMQTemplate;
    private final ChannelDestinationConverter channelDestinationConverter;
    private final TopicValidator topicValidator;
    private final ObjectProvider<HttpStdErrors.Group> httpErrorsProvider;

    public DefaultMessagePublisher(RocketMQTemplate rocketMQTemplate,
                                   ChannelDestinationConverter channelDestinationConverter,
                                   TopicValidator topicValidator,
                                   ObjectProvider<HttpStdErrors.Group> httpErrorsProvider) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.channelDestinationConverter = channelDestinationConverter;
        this.topicValidator = topicValidator;
        this.httpErrorsProvider = httpErrorsProvider;
    }

    @Override
    public void send(Destination destination, Message<?> message) {
        validateAndSend(destination, message, false, null, 0);
    }

    @Override
    public void sendByChannel(Channel channel, Message<?> message) {
        try {
            // 转换 channel 为 destination
            Destination destination = channelDestinationConverter.convert(channel);

            // 发送
            send(destination, message);
        } catch (IllegalArgumentException e) {
            throw toApplicationException("发送失败 (channel=" + channel.value() + "): " + e.getMessage(), e);
        }
    }

    @Override
    public void sendOrderly(Destination destination, Message<?> message, String hashKey) {
        validateAndSend(destination, message, true, hashKey, 0);
    }

    @Override
    public void sendDelayed(Destination destination, Message<?> message, int delayLevel) {
        if (delayLevel < 1 || delayLevel > 18) {
            throw toApplicationException("延迟级别必须在 1-18 之间，实际: " + delayLevel, null);
        }
        validateAndSend(destination, message, false, null, delayLevel);
    }

    /**
     * 统一校验与发送逻辑。
     */
    private void validateAndSend(Destination destination, Message<?> message,
                                  boolean orderly, String hashKey, int delayLevel) {
        try {
            // 校验 Topic 命名
            topicValidator.validate(destination.topic());

            // 构建 RocketMQ 消息
            org.springframework.messaging.Message<?> mqMessage = MessageBuilder
                    .withPayload(message)
                    .setHeader(MessageHeaderKeys.EVENT_ID, message.getEventId())
                    .setHeader(MessageHeaderKeys.TRACE_ID, message.getTraceId())
                    .setHeader(MessageHeaderKeys.OCCURRED_AT, message.getOccurredAt())
                    .build();

            String dest = destination.toString();

            // 根据模式发送
            if (orderly) {
                if (log.isDebugEnabled()) {
                    log.debug("发送顺序消息: destination={}, eventId={}, hashKey={}",
                            dest, message.getEventId(), hashKey);
                }
                rocketMQTemplate.syncSendOrderly(dest, mqMessage, hashKey);
            } else if (delayLevel > 0) {
                if (log.isDebugEnabled()) {
                    log.debug("发送延迟消息: destination={}, eventId={}, delayLevel={}",
                            dest, message.getEventId(), delayLevel);
                }
                rocketMQTemplate.syncSend(dest, mqMessage, 3000, delayLevel);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("发送普通消息: destination={}, eventId={}", dest, message.getEventId());
                }
                rocketMQTemplate.convertAndSend(dest, mqMessage);
            }

            if (log.isInfoEnabled()) {
                log.info("消息发送成功: destination={}, eventId={}, traceId={}",
                        dest, message.getEventId(), message.getTraceId());
            }
        } catch (IllegalArgumentException e) {
            throw toApplicationException("消息发送失败 (destination=" + destination + "): " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("消息发送异常: destination={}, eventId={}", destination, message.getEventId(), e);
            throw toApplicationException("消息发送异常: " + e.getMessage(), e);
        }
    }

    private ApplicationException toApplicationException(String message, Throwable cause) {
        HttpStdErrors.Group group = httpErrorsProvider.getIfAvailable(() -> HttpStdErrors.of("UNKNOWN"));
        return new ApplicationException(group.UNPROCESSABLE(), message, cause);
    }
}
