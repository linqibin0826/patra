package com.patra.ingest.infra.messaging;

import com.patra.ingest.domain.model.vo.TaskReadyMessage;
import com.patra.ingest.infra.messaging.support.TaskReadyMessageMapper;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.vo.RelayPlan;
import com.patra.ingest.domain.port.OutboxPublisherPort;
import com.patra.starter.rocketmq.model.PatraMessage;
import com.patra.starter.rocketmq.publisher.PatraMessagePublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * RocketMQ 出站发布实现（基础设施层）。
 * <p>
 * 职责：
 * <ul>
 *   <li>根据 {@link RelayPlan} 与 OutboxMessage 解析出 Topic:Tag 目的地。</li>
 *   <li>将 Outbox 存储的精简载荷映射为领域级消息 {@link TaskReadyMessage}。</li>
 *   <li>调用底层 {@link PatraMessagePublisher} 发送，屏蔽 MQ SDK 细节。</li>
 * </ul>
 * </p>
 * <p>
 * 日志策略：
 * <ul>
 *   <li>DEBUG：发送前（目的地 / dedupKey）与发送成功（messageId 若可获得——当前返回 NONE）。</li>
 *   <li>ERROR：发送抛出异常时记录必要诊断上下文（destination / dedupKey）。</li>
 * </ul>
 * 不在此处做重试：重试与状态推进由 Relay 上层协调，确保职责单一。
 * </p>
 */
@Slf4j
@Component
public class RocketMqOutboxPublisher implements OutboxPublisherPort {

    private final TaskReadyMessageMapper messageMapper;
    private final PatraMessagePublisher messagePublisher;

    public RocketMqOutboxPublisher(TaskReadyMessageMapper messageMapper,
                                   PatraMessagePublisher messagePublisher) {
        this.messageMapper = messageMapper;
        this.messagePublisher = messagePublisher;
    }

    /**
     * 发布单条 Outbox 消息到 RocketMQ。
     *
     * @param message Outbox 消息（已由上层获取租约并校验状态）
     * @param plan    Relay 发布计划（含触发时间 / channel）
     * @return 发布结果（当前实现返回 NONE，brokerMsgId 由上层通过 markPublished 记录）
     */
    @Override
    public PublishResult publish(OutboxMessage message, RelayPlan plan) {
        var channelKey = plan.channel();
        // 按照 Outbox 载荷映射为领域消息体
        TaskReadyMessage body = messageMapper.map(message);
        // 拼装 PatraMessage 以对接 MQ SDK（含 traceId / occurredAt）
        PatraMessage<TaskReadyMessage> mqMessage = buildMessage(message, body, plan.triggeredAt());
        if (log.isDebugEnabled()) {
            log.debug("[INGEST][INFRA] publish outbox message start channel={} dedupKey={} partitionKey={}", channelKey.channel(), message.getDedupKey(), message.getPartitionKey());
        }
        try {
            // 统一从 channel 解析为 destination 并发送
            messagePublisher.sendByChannel(channelKey.channel(), mqMessage);
            if (log.isDebugEnabled()) {
                log.debug("[INGEST][INFRA] publish outbox message success channel={} dedupKey={}", channelKey.channel(), message.getDedupKey());
            }
        } catch (Exception e) {
            log.error("[INGEST][INFRA] publish outbox message fail channel={} dedupKey={} err={}", channelKey.channel(), message.getDedupKey(), e.getMessage(), e);
            throw e;
        }
        return PublishResult.NONE;
    }

    /**
     * 构建发送到 MQ 的 PatraMessage。
     * <p>TraceId 优先：header.scheduleInstanceId → partitionKey。</p>
     * <p>occurredAt 优先：header.occurredAt → plan.triggeredAt（fallbackOccurredAt 参数）。</p>
     */
    private PatraMessage<TaskReadyMessage> buildMessage(OutboxMessage message,
                                                        TaskReadyMessage body,
                                                        Instant fallbackOccurredAt) {
        String traceId = body.header() != null && body.header().scheduleInstanceId() != null
                ? String.valueOf(body.header().scheduleInstanceId())
                : message.getPartitionKey();
        Instant occurredAt = body.header() != null && body.header().occurredAt() != null
                ? body.header().occurredAt()
                : fallbackOccurredAt;
        return PatraMessage.<TaskReadyMessage>builder()
                .eventId(message.getDedupKey())
                .traceId(traceId)
                .occurredAt(occurredAt)
                .payload(body)
                .build();
    }
}
