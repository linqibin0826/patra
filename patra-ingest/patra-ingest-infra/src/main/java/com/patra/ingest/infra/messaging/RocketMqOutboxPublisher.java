package com.patra.ingest.infra.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.domain.exception.OutboxPublishException;
import com.patra.ingest.domain.exception.OutboxPublishException.Reason;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.vo.RelayPlan;
import com.patra.ingest.domain.port.OutboxPublisherPort;
import com.patra.ingest.infra.config.OutboxMqProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageConst;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Map;

/**
 * RocketMQ Outbox 发布实现：使用 StreamBridge 动态目的地发布消息。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "papertrace.ingest.outbox.publisher", havingValue = "rocketmq", matchIfMissing = true)
public class RocketMqOutboxPublisher implements OutboxPublisherPort {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final StreamBridge streamBridge;
    private final ObjectMapper objectMapper;
    private final OutboxMqProperties properties;

    public RocketMqOutboxPublisher(StreamBridge streamBridge,
                                   ObjectMapper objectMapper,
                                   OutboxMqProperties properties) {
        this.streamBridge = streamBridge;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public PublishResult publish(OutboxMessage message, RelayPlan plan) throws Exception {
        if (StringUtils.hasText(message.getMsgId())) {
            if (log.isDebugEnabled()) {
                log.debug("[INGEST][INFRA] Skip publishing message id={} channel={} because msgId already present",
                        message.getId(), message.getChannel());
            }
            return new PublishResult(message.getMsgId());
        }
        String channel = message.getChannel();
        if (!properties.isChannelAllowed(channel)) {
            throw new OutboxPublishException(Reason.CHANNEL_NOT_ALLOWED,
                    "Channel not allowed by whitelist: " + channel);
        }
        Message<String> outboundMessage = buildMessage(message);
        try {
            boolean sent = streamBridge.send(channel, outboundMessage);
            if (!sent) {
                throw new OutboxPublishException(Reason.SEND_FAILED,
                        "StreamBridge returned false when sending to channel " + channel);
            }
            if (log.isDebugEnabled()) {
                log.debug("[INGEST][INFRA] Published message id={} channel={} dedupKey={} opType={} partitionKey={}",
                        message.getId(), channel, message.getDedupKey(), message.getOpType(), outboundMessage.getHeaders().get("partitionKey"));
            }
            return PublishResult.NONE;
        } catch (MessagingException ex) {
            throw new OutboxPublishException(Reason.SEND_FAILED,
                    "Failed to publish message to channel " + channel, ex);
        }
    }

    private Message<String> buildMessage(OutboxMessage message) {
        MessageBuilder<String> builder = MessageBuilder
                .withPayload(defaultPayload(message))
                .setHeader(MessageHeaders.CONTENT_TYPE, MimeTypeUtils.APPLICATION_JSON)
                .setHeader(MessageConst.PROPERTY_KEYS, message.getDedupKey())
                .setHeader(MessageConst.PROPERTY_TAGS, message.getOpType())
                .setHeader("partitionKey", resolvePartitionKey(message));
        Map<String, Object> headers = parseHeaders(message.getHeadersJson());
        headers.forEach(builder::setHeader);
        return builder.build();
    }

    private String defaultPayload(OutboxMessage message) {
        String payload = message.getPayloadJson();
        return payload == null ? "" : payload;
    }

    private String resolvePartitionKey(OutboxMessage message) {
        if (StringUtils.hasText(message.getPartitionKey())) {
            return message.getPartitionKey();
        }
        return message.getDedupKey();
    }

    private Map<String, Object> parseHeaders(String headersJson) {
        if (!StringUtils.hasText(headersJson)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(headersJson, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new OutboxPublishException(Reason.HEADERS_INVALID,
                    "Failed to parse Outbox headers JSON", ex);
        }
    }
}
