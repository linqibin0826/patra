package com.patra.ingest.domain.model.entity;

import java.time.Instant;
import java.util.Objects;

/**
 * Outbox 消息领域对象，封装基础字段。
 */
public final class OutboxMessage {

    private final Long id;
    private final Long version;
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
    private final Instant nextRetryAt;
    private final String errorCode;
    private final String errorMsg;
    private final String leaseOwner;
    private final Instant leaseExpireAt;
    private final String msgId;

    private OutboxMessage(Builder builder) {
        this.id = builder.id;
        this.version = builder.version;
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
        this.nextRetryAt = builder.nextRetryAt;
        this.errorCode = builder.errorCode;
        this.errorMsg = builder.errorMsg;
        this.leaseOwner = builder.leaseOwner;
        this.leaseExpireAt = builder.leaseExpireAt;
        this.msgId = builder.msgId;
    }

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
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

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public Instant getLeaseExpireAt() {
        return leaseExpireAt;
    }

    public String getMsgId() {
        return msgId;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder toBuilder() {
        return new Builder()
                .id(id)
                .version(version)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .channel(channel)
                .opType(opType)
                .partitionKey(partitionKey)
                .dedupKey(dedupKey)
                .payloadJson(payloadJson)
                .headersJson(headersJson)
                .notBefore(notBefore)
                .statusCode(statusCode)
                .retryCount(retryCount)
                .nextRetryAt(nextRetryAt)
                .errorCode(errorCode)
                .errorMsg(errorMsg)
                .leaseOwner(leaseOwner)
                .leaseExpireAt(leaseExpireAt)
                .msgId(msgId);
    }

    public static final class Builder {
        private Long id;
        private Long version;
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
        private Instant nextRetryAt;
        private String errorCode;
        private String errorMsg;
        private String leaseOwner;
        private Instant leaseExpireAt;
        private String msgId;

        private Builder() {
        }

        public Builder id(Long id) {
            this.id = id;
            return this;
        }

        public Builder version(Long version) {
            this.version = version;
            return this;
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

        public Builder nextRetryAt(Instant nextRetryAt) {
            this.nextRetryAt = nextRetryAt;
            return this;
        }

        public Builder errorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder errorMsg(String errorMsg) {
            this.errorMsg = errorMsg;
            return this;
        }

        public Builder leaseOwner(String leaseOwner) {
            this.leaseOwner = leaseOwner;
            return this;
        }

        public Builder leaseExpireAt(Instant leaseExpireAt) {
            this.leaseExpireAt = leaseExpireAt;
            return this;
        }

        public Builder msgId(String msgId) {
            this.msgId = msgId;
            return this;
        }

        public OutboxMessage build() {
            return new OutboxMessage(this);
        }
    }
}
