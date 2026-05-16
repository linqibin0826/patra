package dev.linqibin.patra.ingest.domain.model.entity;

import java.time.Instant;
import java.util.Objects;

/// 发件箱消息实体。封装 Outbox 模式的核心消息数据。
///
/// 标识：由聚合类型 + 聚合ID + 去重键唯一标识。
///
/// 生命周期：
///
/// - 创建时处于 `PENDING` 状态，等待中继器发布
///   - 中继器获取租约后转换为 `PUBLISHING` 状态
///   - 发布成功后转换为 `PUBLISHED` 状态
///   - 发布失败后根据重试策略转换为 `PENDING` 或 `FAILED` 状态
///
/// 业务约束：
///
/// - 支持租约机制，确保消息不被并发发布
///   - 支持延迟发布（notBefore 字段）
///   - 支持重试机制（retryCount + nextRetryAt）
///   - 分区键用于保证消息的有序投递
///   - 去重键用于消费端幂等性保证
///
/// @author linqibin
/// @since 0.1.0
public final class OutboxMessage {

  /// Primary key.
  private final Long id;

  /// Optimistic lock version.
  private final Long version;

  /// Aggregate type.
  private final String aggregateType;

  /// Aggregate identifier.
  private final Long aggregateId;

  /// Logical channel.
  private final String channel;

  /// Business operation type.
  private final String opType;

  /// Partition key.
  private final String partitionKey;

  /// Idempotency key.
  private final String dedupKey;

  /// Payload JSON.
  private final String payloadJson;

  /// Headers JSON.
  private final String headersJson;

  /// Earliest publish time.
  private final Instant notBefore;

  /// Status code.
  private final String statusCode;

  /// Retry count.
  private final Integer retryCount;

  /// Next retry timestamp.
  private final Instant nextRetryAt;

  /// Error code.
  private final String errorCode;

  /// Error message.
  private final String errorMsg;

  /// Lease owner.
  private final String leaseOwner;

  /// Lease expiration time.
  private final Instant leaseExpireAt;

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
        .leaseExpireAt(leaseExpireAt);
  }

  /// Create a refreshed message for retry scenario.
  ///
  /// Resets status to PENDING, clears retry count and error info, updates payload/headers.
  ///
  /// @param newPayloadJson Updated payload JSON
  /// @param newHeadersJson Updated headers JSON
  /// @return Refreshed OutboxMessage instance
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

  // ========== Outbox Pattern Enhancement - Behavior Methods ==========

  /// Computes the next attempt number for this message.
  ///
  /// Handles null retryCount (first attempt case) gracefully.
  ///
  /// @return next attempt number (1-based, starts from 1)
  public int computeNextAttempt() {
    return (retryCount == null ? 0 : retryCount) + 1;
  }

  /// Checks if this message can be retried based on maximum attempts limit.
  ///
  /// Compares next attempt number against maxAttempts threshold.
  ///
  /// @param maxAttempts maximum allowed relay attempts (from retry policy)
  /// @return true if next attempt <= maxAttempts, false otherwise
  public boolean canRetry(int maxAttempts) {
    return computeNextAttempt() <= maxAttempts;
  }

  /// Checks if this message currently holds an active (non-expired) lease.
  ///
  /// A lease is active if:
  ///
  /// - leaseOwner is not null
  ///   - leaseExpireAt is not null
  ///   - leaseExpireAt is after the given timestamp
  ///
  /// @param now current timestamp for lease expiry check
  /// @return true if lease is active, false otherwise
  public boolean hasActiveLease(Instant now) {
    return leaseOwner != null && leaseExpireAt != null && leaseExpireAt.isAfter(now);
  }

  /// Checks if this message is in PENDING state.
  ///
  /// @return true if status is PENDING
  public boolean isPending() {
    return "PENDING".equals(statusCode);
  }

  /// Checks if this message is in PUBLISHING state.
  ///
  /// @return true if status is PUBLISHING
  public boolean isPublishing() {
    return "PUBLISHING".equals(statusCode);
  }

  /// Checks if this message is in a terminal state (PUBLISHED or FAILED).
  ///
  /// Terminal states indicate no further relay processing is needed.
  ///
  /// @return true if status is PUBLISHED or FAILED
  public boolean isTerminal() {
    return "PUBLISHED".equals(statusCode) || "FAILED".equals(statusCode);
  }

  /// Checks if this message is ready to be relayed at the given timestamp.
  ///
  /// A message is ready if:
  ///
  /// - Status is PENDING (awaiting relay)
  ///   - notBefore time has passed (if set)
  ///   - nextRetryAt time has passed (if set, for retries)
  ///
  /// @param now current timestamp for readiness check
  /// @return true if message is ready to relay
  public boolean isReadyToRelay(Instant now) {
    if (!isPending()) {
      return false;
    }
    if (notBefore != null && notBefore.isAfter(now)) {
      return false;
    }
    if (nextRetryAt != null && nextRetryAt.isAfter(now)) {
      return false;
    }
    return true;
  }

  /// Checks if the lease for this message has expired at the given timestamp.
  ///
  /// Returns true if:
  ///
  /// - Message has no lease (leaseExpireAt is null), OR
  ///   - Lease expiration time has passed
  ///
  /// @param now current timestamp for expiry check
  /// @return true if lease is expired or absent
  public boolean isLeaseExpired(Instant now) {
    return leaseExpireAt == null || leaseExpireAt.isBefore(now);
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

    public OutboxMessage build() {
      return new OutboxMessage(this);
    }
  }
}
