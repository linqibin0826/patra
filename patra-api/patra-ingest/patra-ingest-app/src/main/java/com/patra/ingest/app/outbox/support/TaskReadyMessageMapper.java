package com.patra.ingest.app.outbox.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.app.outbox.model.TaskReadyMessage;
import com.patra.ingest.domain.model.entity.OutboxMessage;

import java.io.IOException;

/**
 * 将 Outbox 载荷转换为任务发布消息。
 *
 * @author linqibin
 * @since 0.1.0
 */
public class TaskReadyMessageMapper {

    /** JSON 映射器 */
    private final ObjectMapper objectMapper;

    public TaskReadyMessageMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 将 OutboxMessage 映射为 TaskReadyMessage。
     */
    public TaskReadyMessage map(OutboxMessage message) {
        try {
            JsonNode payloadNode = objectMapper.readTree(message.getPayloadJson());
            JsonNode headerNode = message.getHeadersJson() == null ? null : objectMapper.readTree(message.getHeadersJson());
            TaskReadyMessage.Payload payload = objectMapper.treeToValue(payloadNode, TaskReadyMessage.Payload.class);
            TaskReadyMessage.Header header = headerNode == null
                    ? null
                    : objectMapper.treeToValue(headerNode, TaskReadyMessage.Header.class);
            return new TaskReadyMessage(payload, header);
        } catch (IOException e) {
            throw new IllegalStateException("failed to parse outbox payload", e);
        }
    }
}
