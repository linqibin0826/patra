package com.patra.ingest.infra.persistence.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.infra.persistence.entity.OutboxMessageDO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Outbox 消息转换器：应用层值对象 → 数据对象。
 */
@Component
@RequiredArgsConstructor
public class OutboxMessageConverter {

    private static final String DEFAULT_STATUS = "PENDING";

    private final ObjectMapper objectMapper;

    public OutboxMessageDO toEntity(OutboxMessage message) {
        OutboxMessageDO entity = new OutboxMessageDO();
        entity.setAggregateType(message.getAggregateType());
        entity.setAggregateId(message.getAggregateId());
        entity.setChannel(message.getChannel());
        entity.setOpType(message.getOpType());
        entity.setPartitionKey(message.getPartitionKey());
        entity.setDedupKey(message.getDedupKey());
        entity.setPayloadJson(readTree(message.getPayloadJson()));
        entity.setHeadersJson(readTree(message.getHeadersJson()));
        entity.setNotBefore(message.getNotBefore());
        entity.setStatusCode(message.getStatusCode() == null ? DEFAULT_STATUS : message.getStatusCode());
        entity.setRetryCount(message.getRetryCount());
        return entity;
    }

    private JsonNode readTree(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("解析 outbox JSON 字段失败", e);
        }
    }
}
