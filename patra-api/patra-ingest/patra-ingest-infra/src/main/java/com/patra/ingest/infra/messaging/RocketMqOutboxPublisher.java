package com.patra.ingest.infra.messaging;

import com.patra.ingest.domain.model.value.TaskReadyMessage;
import com.patra.ingest.infra.messaging.support.OutboxDestinationResolver;
import com.patra.ingest.infra.messaging.support.TaskReadyMessageMapper;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.value.RelayPlan;
import com.patra.ingest.domain.port.OutboxPublisherPort;
import com.patra.starter.rocketmq.model.PatraMessage;
import com.patra.starter.rocketmq.publisher.PatraMessagePublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * RocketMQ 出站发布实现，位于基础设施层。
 */
@Component
public class RocketMqOutboxPublisher implements OutboxPublisherPort {

    private final TaskReadyMessageMapper messageMapper;
    private final OutboxDestinationResolver destinationResolver;
    private final PatraMessagePublisher messagePublisher;

    public RocketMqOutboxPublisher(TaskReadyMessageMapper messageMapper,
                                   OutboxDestinationResolver destinationResolver,
                                   PatraMessagePublisher messagePublisher) {
        this.messageMapper = messageMapper;
        this.destinationResolver = destinationResolver;
        this.messagePublisher = messagePublisher;
    }

    @Override
    public PublishResult publish(OutboxMessage message, RelayPlan plan) throws Exception {
        String destination = destinationResolver.resolve(plan.channel());
        TaskReadyMessage body = messageMapper.map(message);
        PatraMessage<TaskReadyMessage> mqMessage = buildMessage(message, body, plan.triggeredAt());
        messagePublisher.send(destination, mqMessage);
        return PublishResult.NONE;
    }

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
