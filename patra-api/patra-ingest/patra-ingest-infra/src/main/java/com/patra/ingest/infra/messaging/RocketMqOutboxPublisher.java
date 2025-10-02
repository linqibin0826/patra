package com.patra.ingest.infra.messaging;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.vo.RelayPlan;
import com.patra.ingest.domain.model.vo.TaskReadyMessage;
import com.patra.ingest.domain.port.OutboxPublisherPort;
import com.patra.ingest.infra.messaging.converter.TaskReadyMessageConverter;
import com.patra.starter.rocketmq.core.Channel;
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
        // 1. 从消息本身获取 channel（数据库查询出来的实际数据）
        // 注意：不能使用 plan.channel()，因为它可能为 null（查询所有频道时）
        String channelValue = message.getChannel();

        // 2. 创建 RocketMQ Channel（值对象，用于类型安全）
        // 注意：使用 fromString 方法自动处理大小写转换（数据库存储大写格式）
        Channel channel = Channel.fromString(channelValue);

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
