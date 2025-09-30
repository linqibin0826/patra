package com.patra.ingest.infra.messaging;

import com.patra.common.messaging.ChannelKey;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.vo.RelayPlan;
import com.patra.ingest.domain.model.vo.TaskReadyMessage;
import com.patra.ingest.domain.port.OutboxPublisherPort;
import com.patra.ingest.infra.messaging.converter.TaskReadyMessageConverter;
import com.patra.starter.rocketmq.core.channel.Channel;
import com.patra.starter.rocketmq.core.message.Message;
import com.patra.starter.rocketmq.publisher.MessagePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * RocketMQ 出站发布实现（基础设施层）。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class RocketMqOutboxPublisher implements OutboxPublisherPort {

    private final TaskReadyMessageConverter messageMapper;
    private final MessagePublisher messagePublisher;

    public RocketMqOutboxPublisher(TaskReadyMessageConverter messageMapper,
                                   MessagePublisher messagePublisher) {
        this.messageMapper = messageMapper;
        this.messagePublisher = messagePublisher;
    }

    @Override
    public PublishResult publish(OutboxMessage message, RelayPlan plan) {
        // 1. 从 plan 获取 channel
        ChannelKey channelKey = plan.channel();

        // 2. 创建 RocketMQ Channel（值对象，用于类型安全）
        Channel channel = Channel.of(channelKey);

        // 3. 映射为领域消息体
        TaskReadyMessage body = messageMapper.convert(message);

        // 4. 构建 RocketMQ 消息
        Message<TaskReadyMessage> mqMessage = buildMessage(message, body, plan.triggeredAt());

        if (log.isDebugEnabled()) {
            log.debug("[INGEST][INFRA] publish outbox message start channel={} dedupKey={} partitionKey={}",
                    channel.value(), message.getDedupKey(), message.getPartitionKey());
        }

        try {
            // 5. 使用强类型 Channel 发送
            messagePublisher.sendByChannel(channel, mqMessage);
            
            if (log.isDebugEnabled()) {
                log.debug("[INGEST][INFRA] publish outbox message success channel={} dedupKey={}",
                        channel.value(), message.getDedupKey());
            }
        } catch (Exception e) {
            log.error("[INGEST][INFRA] publish outbox message fail channel={} dedupKey={} err={}",
                    channel.value(), message.getDedupKey(), e.getMessage(), e);
            throw e;
        }

        return PublishResult.NONE;
    }

    private Message<TaskReadyMessage> buildMessage(
            OutboxMessage message,
            TaskReadyMessage body,
            Instant triggeredAt
    ) {
        return Message.<TaskReadyMessage>builder()
                .payload(body)
                .eventId(message.getDedupKey())
                .traceId(message.getDedupKey())
                .occurredAt(triggeredAt)
                .build();
    }
}
