package com.patra.ingest.domain.model.entity;

import java.time.Instant;
import java.util.Objects;

/**
 * Outbox 消息领域对象，封装基础字段。
 */
public final class OutboxMessage {

    private final String aggregateType;
    private final Long aggregateId;
    private final String channel;
    private final String opType;
    private final String partitionKey;
    private final String dedupKey;
    private final String payloadJson;
    private final String headersJson;
    private final Instant notBefore;
    private final String statusCode;
    private final Integer retryCount;

    private OutboxMessage(Builder builder) {
        this.aggregateType = Objects.requireNonNull(builder.aggregateType, "aggregateType 必填");
        this.aggregateId = Objects.requireNonNull(builder.aggregateId, "aggregateId 必填");
        this.channel = Objects.requireNonNull(builder.channel, "channel 必填");
        this.opType = Objects.requireNonNull(builder.opType, "opType 必填");
        this.partitionKey = Objects.requireNonNull(builder.partitionKey, "partitionKey 必填");
        this.dedupKey = Objects.requireNonNull(builder.dedupKey, "dedupKey 必填");
        this.payloadJson = builder.payloadJson;
        this.headersJson = builder.headersJson;
        this.notBefore = builder.notBefore;
        this.statusCode = builder.statusCode == null ? "PENDING" : builder.statusCode;
        this.retryCount = builder.retryCount == null ? 0 : builder.retryCount;
    }

    public String getAggregateType() {
        return aggregateType;
    }

    public Long getAggregateId() {
        return aggregateId;
    }

    public String getChannel() {
        return channel;
    }

    public String getOpType() {
        return opType;
    }

    public String getPartitionKey() {
        return partitionKey;
    }

    public String getDedupKey() {
        return dedupKey;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public String getHeadersJson() {
        return headersJson;
    }

    public Instant getNotBefore() {
        return notBefore;
    }

    public String getStatusCode() {
        return statusCode;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String aggregateType;
        private Long aggregateId;
        private String channel;
        private String opType;
        private String partitionKey;
        private String dedupKey;
        private String payloadJson;
        private String headersJson;
        private Instant notBefore;
        private String statusCode;
        private Integer retryCount;

        private Builder() {
        }

        public Builder aggregateType(String aggregateType) {
            this.aggregateType = aggregateType;
            return this;
        }

        public Builder aggregateId(Long aggregateId) {
            this.aggregateId = aggregateId;
            return this;
        }

        public Builder channel(String channel) {
            this.channel = channel;
            return this;
        }

        public Builder opType(String opType) {
            this.opType = opType;
            return this;
        }

        public Builder partitionKey(String partitionKey) {
            this.partitionKey = partitionKey;
            return this;
        }

        public Builder dedupKey(String dedupKey) {
            this.dedupKey = dedupKey;
            return this;
        }

        public Builder payloadJson(String payloadJson) {
            this.payloadJson = payloadJson;
            return this;
        }

        public Builder headersJson(String headersJson) {
            this.headersJson = headersJson;
            return this;
        }

        public Builder notBefore(Instant notBefore) {
            this.notBefore = notBefore;
            return this;
        }

        public Builder statusCode(String statusCode) {
            this.statusCode = statusCode;
            return this;
        }

        public Builder retryCount(Integer retryCount) {
            this.retryCount = retryCount;
            return this;
        }

        public OutboxMessage build() {
            return new OutboxMessage(this);
        }
    }
}
