package com.patra.ingest.domain.model.entity;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain object representing an outbox message with core fields.
 *
 * @author linqibin
 * @since 0.1.0
 */
public final class OutboxMessage {

  /** Primary key. */
  private final Long id;

  /** Optimistic lock version. */
  private final Long version;

  /** Aggregate type. */
  private final String aggregateType;

  /** Aggregate identifier. */
  private final Long aggregateId;

  /** Logical channel. */
  private final String channel;

  /** Business operation type. */
  private final String opType;

  /** Partition key. */
  private final String partitionKey;

  /** Idempotency key. */
  private final String dedupKey;

  /** Payload JSON. */
  private final String payloadJson;

  /** Headers JSON. */
  private final String headersJson;

  /** Earliest publish time. */
  private final Instant notBefore;

  /** Status code. */
  private final String statusCode;

  /** Retry count. */
  private final Integer retryCount;

  /** Next retry timestamp. */
  private final Instant nextRetryAt;

  /** Error code. */
  private final String errorCode;

  /** Error message. */
  private final String errorMsg;

  /** Lease owner. */
  private final String leaseOwner;

  /** Lease expiration time. */
  private final Instant leaseExpireAt;

  /** Broker message identifier. */
  private final String msgId;

  private OutboxMessage(Builder builder) {
    this.id = builder.id;
    this.version = builder.version;
    this.aggregateType =
        Objects.requireNonNull(builder.aggregateType, "aggregateType must not be null");
    this.aggregateId = Objects.requireNonNull(builder.aggregateId, "aggregateId must not be null");
    this.channel = Objects.requireNonNull(builder.channel, "channel must not be null");
    this.opType = Objects.requireNonNull(builder.opType, "opType must not be null");
    this.partitionKey =
        Objects.requireNonNull(builder.partitionKey, "partitionKey must not be null");
    this.dedupKey = Objects.requireNonNull(builder.dedupKey, "dedupKey must not be null");
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

  /**
   * Create a refreshed message for retry scenario.
   *
   * <p>Resets status to PENDING, clears retry count and error info, updates payload/headers.
   *
   * @param newPayloadJson Updated payload JSON
   * @param newHeadersJson Updated headers JSON
   * @return Refreshed OutboxMessage instance
   */
  public OutboxMessage refreshForRetry(String newPayloadJson, String newHeadersJson) {
    return toBuilder()
        .payloadJson(newPayloadJson)
        .headersJson(newHeadersJson)
        .statusCode("PENDING")
        .retryCount(0)
        .nextRetryAt(null)
        .errorCode(null)
        .errorMsg(null)
        .build();
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

    private Builder() {}

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
