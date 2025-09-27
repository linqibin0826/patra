package com.patra.ingest.app.outbox.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.app.outbox.model.TaskReadyMessage;
import com.patra.ingest.domain.model.entity.OutboxMessage;

import java.io.IOException;

/**
 * 将 Outbox 载荷转换为任务发布消息。
 */
public class TaskReadyMessageMapper {

    private final ObjectMapper objectMapper;

    public TaskReadyMessageMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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
            throw new IllegalStateException("解析 outbox 载荷失败", e);
        }
    }
}
