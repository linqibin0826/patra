package com.patra.ingest.infra.persistence.converter;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.starter.core.json.JsonNodeSupport;
import com.patra.ingest.infra.persistence.entity.OutboxMessageDO;
import org.springframework.stereotype.Component;

/**
 * Outbox 消息转换器：应用层值对象 → 数据对象。
 */
@Component
public class OutboxMessageConverter implements JsonNodeSupport {

    private static final String DEFAULT_STATUS = "PENDING";

    public OutboxMessageDO toEntity(OutboxMessage message) {
        OutboxMessageDO entity = new OutboxMessageDO();
        entity.setId(message.getId());
        entity.setVersion(message.getVersion());
        entity.setAggregateType(message.getAggregateType());
        entity.setAggregateId(message.getAggregateId());
        entity.setChannel(message.getChannel());
        entity.setOpType(message.getOpType());
        entity.setPartitionKey(message.getPartitionKey());
        entity.setDedupKey(message.getDedupKey());
        entity.setPayloadJson(readJsonNode(message.getPayloadJson()));
        entity.setHeadersJson(readJsonNode(message.getHeadersJson()));
        entity.setNotBefore(message.getNotBefore());
        entity.setStatusCode(message.getStatusCode() == null ? DEFAULT_STATUS : message.getStatusCode());
        entity.setRetryCount(message.getRetryCount());
        entity.setNextRetryAt(message.getNextRetryAt());
        entity.setErrorCode(message.getErrorCode());
        entity.setErrorMsg(message.getErrorMsg());
        entity.setPubLeaseOwner(message.getLeaseOwner());
        entity.setPubLeasedUntil(message.getLeaseExpireAt());
        entity.setMsgId(message.getMsgId());
        return entity;
    }

    public OutboxMessage toDomain(OutboxMessageDO entity) {
        return OutboxMessage.builder()
                .id(entity.getId())
                .version(entity.getVersion())
                .aggregateType(entity.getAggregateType())
                .aggregateId(entity.getAggregateId())
                .channel(entity.getChannel())
                .opType(entity.getOpType())
                .partitionKey(entity.getPartitionKey())
                .dedupKey(entity.getDedupKey())
                .payloadJson(writeJsonString(entity.getPayloadJson()))
                .headersJson(writeJsonString(entity.getHeadersJson()))
                .notBefore(entity.getNotBefore())
                .statusCode(entity.getStatusCode())
                .retryCount(entity.getRetryCount())
                .nextRetryAt(entity.getNextRetryAt())
                .errorCode(entity.getErrorCode())
                .errorMsg(entity.getErrorMsg())
                .leaseOwner(entity.getPubLeaseOwner())
                .leaseExpireAt(entity.getPubLeasedUntil())
                .msgId(entity.getMsgId())
                .build();
    }
}
