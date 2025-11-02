# Outbox 模式

**目的**: 使用事务性 Outbox 模式实现具有事务保证的可靠事件发布。

---

## 目录

1. [概览](#overview)
2. [领域层](#domain-layer)
3. [数据库设计](#database-design)
4. [应用层](#application-layer)
5. [基础设施层](#infrastructure-layer)
6. [适配器层](#adapter-layer)
7. [核心模式](#key-patterns)
8. [最佳实践](#best-practices)

---

## 概览

### 什么是 Outbox 模式？

**事务性 Outbox 模式**通过在与业务数据相同的事务中将事件写入数据库表，确保可靠的事件发布。单独的中继进程读取这些事件并发布到消息代理。

### 为什么使用 Outbox 模式？

**❌ 不使用 Outbox：**
- 业务数据已提交，但 MQ 发布失败 → **数据丢失**
- MQ 发布成功，但业务数据回滚 → **重复事件**
- DB 和 MQ 之间没有事务保证

**✅ 使用 Outbox：**
- 业务数据 + outbox 消息在**单个事务**中写入
- 中继作业从 outbox 表发布消息
- 至少一次投递保证
- **没有双写问题**

### Papertrace 实现

```
┌─────────────────────────────────────────────────────────────┐
│                    Ingest Flow                              │
├─────────────────────────────────────────────────────────────┤
│  1. Business Transaction:                                   │
│     - Save Task aggregate (ing_task)                        │
│     - Save Outbox message (ing_outbox_message)              │
│     └─> COMMIT (atomic)                                     │
│                                                             │
│  2. Relay Job (separate process):                           │
│     - Poll ing_outbox_message (PENDING)                     │
│     - Acquire lease (optimistic lock)                       │
│     - Publish to RocketMQ                                   │
│     - Mark as PUBLISHED                                     │
│     - Save relay log (audit trail)                          │
└─────────────────────────────────────────────────────────────┘
```

---

## 领域层

### OutboxMessage 实体

**File**: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/entity/OutboxMessage.java`

```java
package com.patra.ingest.domain.model.entity;

import java.time.Instant;
import java.util.Objects;

/**
 * 表示一个用于可靠事件发布的 outbox 消息。
 *
 * <p>使用 Builder 模式进行复杂构造，包含多个可选字段。
 */
public final class OutboxMessage {

  // ========== Core Identity & Metadata ==========
  private final Long id;
  private final Long version;  // Optimistic locking
  private final String aggregateType;  // e.g., "TASK", "PLAN"
  private final Long aggregateId;
  private final String channel;        // Logical topic (e.g., "INGEST_TASK")
  private final String opType;         // Operation (e.g., "TASK_READY")

  // ========== Routing & Deduplication ==========
  private final String partitionKey;   // For ordering (e.g., "PUBMED:HARVEST")
  private final String dedupKey;       // Idempotency key

  // ========== Payload ==========
  private final String payloadJson;
  private final String headersJson;

  // ========== Scheduling & Retry ==========
  private final Instant notBefore;     // Earliest publish time
  private final String statusCode;     // PENDING, PUBLISHED, FAILED
  private final Integer retryCount;
  private final Instant nextRetryAt;

  // ========== Error Tracking ==========
  private final String errorCode;
  private final String errorMsg;

  // ========== Lease Management ==========
  private final String leaseOwner;     // Instance that acquired lease
  private final Instant leaseExpireAt;

  private OutboxMessage(Builder builder) {
    // ✅ Validate required fields
    this.id = builder.id;
    this.version = builder.version;
    this.aggregateType = Objects.requireNonNull(builder.aggregateType, "aggregateType must not be null");
    this.aggregateId = Objects.requireNonNull(builder.aggregateId, "aggregateId must not be null");
    this.channel = Objects.requireNonNull(builder.channel, "channel must not be null");
    this.opType = Objects.requireNonNull(builder.opType, "opType must not be null");
    this.partitionKey = Objects.requireNonNull(builder.partitionKey, "partitionKey must not be null");
    this.dedupKey = Objects.requireNonNull(builder.dedupKey, "dedupKey must not be null");

    // Optional fields with defaults
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

  // ========== Getters (omitted for brevity) ==========

  // ========== Domain Methods ==========

  /** Calculate the next attempt number for retry logging. */
  public int computeNextAttempt() {
    return retryCount + 1;
  }

  /** Check if message has exceeded max retry limit. */
  public boolean hasExceededRetries(int maxRetries) {
    return retryCount >= maxRetries;
  }

  // ========== Builder Pattern ==========

  public static Builder builder() {
    return new Builder();
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

    // ✅ Fluent setters
    public Builder id(Long id) { this.id = id; return this; }
    public Builder version(Long version) { this.version = version; return this; }
    public Builder aggregateType(String aggregateType) { this.aggregateType = aggregateType; return this; }
    public Builder aggregateId(Long aggregateId) { this.aggregateId = aggregateId; return this; }
    public Builder channel(String channel) { this.channel = channel; return this; }
    public Builder opType(String opType) { this.opType = opType; return this; }
    public Builder partitionKey(String partitionKey) { this.partitionKey = partitionKey; return this; }
    public Builder dedupKey(String dedupKey) { this.dedupKey = dedupKey; return this; }
    public Builder payloadJson(String payloadJson) { this.payloadJson = payloadJson; return this; }
    public Builder headersJson(String headersJson) { this.headersJson = headersJson; return this; }
    public Builder notBefore(Instant notBefore) { this.notBefore = notBefore; return this; }
    public Builder statusCode(String statusCode) { this.statusCode = statusCode; return this; }
    public Builder retryCount(Integer retryCount) { this.retryCount = retryCount; return this; }
    public Builder nextRetryAt(Instant nextRetryAt) { this.nextRetryAt = nextRetryAt; return this; }
    public Builder errorCode(String errorCode) { this.errorCode = errorCode; return this; }
    public Builder errorMsg(String errorMsg) { this.errorMsg = errorMsg; return this; }
    public Builder leaseOwner(String leaseOwner) { this.leaseOwner = leaseOwner; return this; }
    public Builder leaseExpireAt(Instant leaseExpireAt) { this.leaseExpireAt = leaseExpireAt; return this; }

    public OutboxMessage build() {
      return new OutboxMessage(this);
    }
  }
}
```

### Repository Port

**File**: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/OutboxMessageRepository.java`

```java
package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import java.util.List;
import java.util.Optional;

/**
 * 仓储端口用于 outbox messages.
 *
 * <p>Persists pending messages, enforces idempotency, and enables bulk operations.
 */
public interface OutboxMessageRepository {

  /** Persist a batch of outbox messages. */
  void saveAll(List<OutboxMessage> messages);

  /** Create or update a single outbox message. */
  void saveOrUpdate(OutboxMessage message);

  /** Locate an existing message by channel and idempotent key. */
  Optional<OutboxMessage> findByChannelAndDedup(String channel, String dedupKey);

  /** Retrieve messages by channel and dedup keys (batch idempotency checks). */
  List<OutboxMessage> findByChannelAndDedupIn(String channel, List<String> dedupKeys);

  /** Update a batch of outbox messages (compensation/retry scenarios). */
  void updateBatch(List<OutboxMessage> messages);

  /**
   * Perform batch insert-or-update (upsert) on outbox messages.
   *
   * <p>Enforces idempotency via unique constraint on (channel, dedupKey).
   */
  void upsertBatch(List<OutboxMessage> messages);
}
```

### 中继存储端口

**File**: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/OutboxRelayStore.java`

```java
package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import java.time.Instant;
import java.util.List;

/**
 * 持久化端口供 the outbox relay to fetch publishable messages
 * and drive state transitions.
 */
public interface OutboxRelayStore {

  /**
   * Fetch pending outbox messages.
   *
   * <p>Supports optional channel filtering (null means all channels).
   */
  List<OutboxMessage> fetchPending(String channel, Instant availableTime, int limit);

  /**
   * Acquire a lease for the given message (optimistic locking).
   *
   * @return true when acquired successfully; false otherwise
   */
  boolean acquireLease(Long id, Long expectedVersion, String leaseOwner, Instant leaseExpireAt);

  /** Mark the message as published. */
  void markPublished(Long id, Long expectedVersion);

  /** Requeue the message for retry after a recoverable failure. */
  void markDeferred(
      Long id,
      Long expectedVersion,
      int retryCount,
      Instant nextRetryAt,
      String errorCode,
      String errorMessage);

  /** Mark the message as dead once retries are exhausted. */
  void markFailed(
      Long id, Long expectedVersion, int retryCount, String errorCode, String errorMessage);
}
```

---

## 数据库设计

### 表结构

**Table**: `ing_outbox_message`

```sql
CREATE TABLE ing_outbox_message (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  version BIGINT NOT NULL DEFAULT 0,          -- Optimistic locking

  aggregate_type VARCHAR(50) NOT NULL,        -- e.g., 'TASK', 'PLAN'
  aggregate_id BIGINT NOT NULL,
  channel VARCHAR(100) NOT NULL,              -- Logical topic
  op_type VARCHAR(50) NOT NULL,               -- Operation type

  partition_key VARCHAR(200) NOT NULL,        -- For ordering (e.g., 'PUBMED:HARVEST')
  dedup_key VARCHAR(200) NOT NULL,            -- Idempotency key

  payload_json TEXT,                          -- JSON payload
  headers_json TEXT,                          -- JSON headers

  not_before DATETIME(6),                     -- Earliest publish time (UTC)
  status_code VARCHAR(20) NOT NULL DEFAULT 'PENDING',
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_at DATETIME(6),

  error_code VARCHAR(100),
  error_msg TEXT,

  pub_lease_owner VARCHAR(200),               -- Lease owner
  pub_leased_until DATETIME(6),               -- Lease expiration

  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),

  UNIQUE KEY uk_outbox_channel_dedup (channel, dedup_key),
  INDEX idx_outbox_status_time (status_code, not_before, created_at),
  INDEX idx_outbox_partition (channel, partition_key, status_code),
  INDEX idx_outbox_lease (pub_lease_owner, pub_leased_until)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

### 索引说明

| 索引 | 用途 |
|-------|---------|
| `uk_outbox_channel_dedup` | **Idempotency**: Prevent duplicate messages for same (channel, dedupKey) |
| `idx_outbox_status_time` | **Relay polling**: Fetch PENDING messages ordered by creation time |
| `idx_outbox_partition` | **Ordered processing**: Group messages by channel and partition key |
| `idx_outbox_lease` | **Lease management**: Find expired leases for takeover |

### 状态机

```
┌─────────┐
│ PENDING │  (Initial state after write)
└────┬────┘
     │
     │ (Relay job acquires lease)
     │
     v
┌─────────────┐
│   LEASED    │  (Implicit: leaseOwner != null && leaseExpireAt > now)
└──┬──────┬───┘
   │      │
   │      │ (Publish failed, retryable)
   │      └──────> markDeferred() ──> PENDING (retryCount++, nextRetryAt set)
   │
   │ (Publish success)
   └──> markPublished() ──> PUBLISHED (terminal)

   (Publish failed, max retries)
   └──> markFailed() ──> FAILED (terminal)
```

---

## 应用层

### Outbox 中继编排器

**File**: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/relay/OutboxRelayOrchestrator.java`

```java
package com.patra.ingest.app.usecase.relay;

import com.patra.ingest.app.usecase.relay.command.OutboxRelayCommand;
import com.patra.ingest.app.usecase.relay.config.OutboxRelayProperties;
import com.patra.ingest.app.usecase.relay.dto.RelayReport;
import com.patra.ingest.app.usecase.relay.executor.OutboxRelayExecutor;
import com.patra.ingest.app.usecase.relay.planner.RelayPlanBuilder;
import com.patra.ingest.app.usecase.relay.publisher.RelayEventPublisher;
import com.patra.ingest.domain.model.vo.relay.RelayBatchResult;
import com.patra.ingest.domain.model.vo.relay.RelayPlan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 应用服务用于 the Outbox Relay use case.
 *
 * <p>Flow:
 * 1. Check feature toggle (disabled → return empty report)
 * 2. Build RelayPlan
 * 3. Delegate to executor and gather RelayBatchResult
 * 4. Publish domain events for auditing and monitoring
 * 5. Assemble and return RelayReport
 *
 * <p>Transaction: @Transactional ensures single-database write atomicity.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayOrchestrator implements OutboxRelayUseCase {

  private final OutboxRelayProperties properties;
  private final RelayPlanBuilder planBuilder;
  private final OutboxRelayExecutor relayExecutor;
  private final RelayEventPublisher eventPublisher;

  /**
   * Execute one relay run. When disabled, return empty report.
   */
  @Override
  @Transactional
  public RelayReport relay(OutboxRelayCommand instruction) {
    // ✅ Feature toggle check
    if (!properties.isEnabled()) {
      String channelDesc = instruction.channel() != null ? instruction.channel().channel() : "ALL_CHANNELS";
      log.info("Outbox relay disabled, skip channel={}", channelDesc);
      return RelayReport.empty(instruction.channel());
    }

    long start = System.currentTimeMillis();

    // ✅ Build execution plan
    RelayPlan plan = planBuilder.build(instruction);

    // ✅ Execute relay batch
    RelayBatchResult result = relayExecutor.execute(plan);

    // ✅ Publish domain events
    eventPublisher.publish(result.events());

    long elapsed = System.currentTimeMillis() - start;
    String channelDesc = result.channel() != null ? result.channel().channel() : "ALL_CHANNELS";
    log.info(
        "relay completed channel={} fetched={} published={} retried={} failed={} leaseMissed={} costMs={}",
        channelDesc, result.fetched(), result.published(), result.retried(),
        result.failed(), result.leaseMissed(), elapsed);

    return new RelayReport(
        result.channel(),
        result.fetched(),
        result.published(),
        result.retried(),
        result.failed(),
        result.leaseMissed());
  }
}
```

### Outbox 中继执行器

**File**: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/relay/executor/OutboxRelayExecutor.java`

```java
package com.patra.ingest.app.usecase.relay.executor;

import com.patra.ingest.app.usecase.relay.coordinator.*;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.vo.relay.*;
import com.patra.ingest.domain.port.OutboxRelayStore;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Outbox 中继执行器 - orchestrates relay batch execution via specialized coordinators.
 *
 * <h3>Architecture: Orchestrator + Coordinators Pattern</h3>
 *
 * This executor delegates to:
 * - RelayLeaseCoordinator: Distributed lease acquisition
 * - RelayPublishCoordinator: Message publishing, error classification, retry scheduling
 * - RelayLogCoordinator: Complete audit trail creation and batch persistence
 *
 * <h3>Execution Flow</h3>
 * 1. Fetch pending messages (respecting channel filter and batch size)
 * 2. Create relay batch ID and log accumulator
 * 3. For each message:
 *    a. Try acquire lease → record LEASE_MISSED if failed
 *    b. Publish message → handle SUCCESS/DEFERRED/FAILED outcome
 *    c. Record relay log for audit trail
 * 4. Batch-persist all relay logs (single INSERT statement)
 * 5. Return batch result with statistics and domain events
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayExecutor {

  private final OutboxRelayStore relayStore;
  private final RelayLeaseCoordinator leaseCoordinator;
  private final RelayPublishCoordinator publishCoordinator;
  private final RelayLogCoordinator logCoordinator;

  /**
   * Executes a single relay batch according to the plan.
   */
  public RelayBatchResult execute(RelayPlan plan) {
    // ✅ Fetch pending messages (null channel means all channels)
    String channel = plan.channel() != null ? plan.channel().channel() : null;
    List<OutboxMessage> messages = relayStore.fetchPending(channel, plan.triggeredAt(), plan.batchSize());

    if (messages.isEmpty()) {
      return RelayBatchResult.empty(plan.channel());
    }

    // ✅ Create batch ID and log accumulator
    RelayBatchId batchId = RelayBatchId.generate(plan.triggeredAt());
    LogAccumulator logAcc = logCoordinator.createAccumulator(batchId);

    // ✅ Process messages and accumulate statistics
    RelayContext context = new RelayContext(plan);
    for (OutboxMessage message : messages) {
      processMessage(message, context, logAcc);
    }

    // ✅ Persist all relay logs in batch (single INSERT with N rows)
    logCoordinator.persistBatch(logAcc);

    return context.toBatchResult(messages.size());
  }

  /**
   * Processes a single message through the relay pipeline: lease → publish → log.
   */
  private void processMessage(OutboxMessage message, RelayContext context, LogAccumulator logAcc) {
    Instant startTime = Instant.now();

    // ✅ Attempt lease acquisition
    if (!leaseCoordinator.tryAcquire(message, context.plan())) {
      context.onLeaseMissed(message);
      logAcc.recordLeaseMissed(message, context.plan().leaseOwner(), startTime);
      return;
    }

    // ✅ Sync version after successful lease acquisition
    message = message.toBuilder().version(message.getVersion() + 1).build();

    // ✅ Publish message and handle result
    RelayResult result = publishCoordinator.publish(message, context.plan());

    if (result.isSuccess()) {
      context.onPublished(message, result.attemptNumber());
      logAcc.recordPublished(message, context.plan().leaseOwner(), startTime, Instant.now());
    } else if (result.isDeferred()) {
      context.onDeferred(message, result);
      logAcc.recordDeferred(message, context.plan().leaseOwner(), startTime,
          result.nextRetryAt(), result.errorCode(), result.errorMessage(), "TRANSIENT");
    } else {
      context.onFailed(message, result);
      logAcc.recordFailed(message, context.plan().leaseOwner(), startTime,
          result.errorCode(), result.errorMessage(), "FATAL");
    }
  }
}
```

---

## 基础设施层

### 数据库实体

**File**: `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/persistence/entity/OutboxMessageDO.java`

```java
package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Outbox message DO — table: ing_outbox_message
 *
 * <p>Idempotency: (channel, dedup_key) is unique (UK: uk_outbox_channel_dedup).
 * <p>Lease: pub_lease_owner/pub_leased_until prevent concurrent processing.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_outbox_message", autoResultMap = true)
public class OutboxMessageDO extends BaseDO {

  @TableField("aggregate_type")
  private String aggregateType;

  @TableField("aggregate_id")
  private Long aggregateId;

  @TableField("channel")
  private String channel;

  @TableField("op_type")
  private String opType;

  @TableField("partition_key")
  private String partitionKey;

  @TableField("dedup_key")
  private String dedupKey;

  @TableField("payload_json")
  private String payloadJson;

  @TableField("headers_json")
  private String headersJson;

  @TableField("not_before")
  private Instant notBefore;

  @TableField("status_code")
  private String statusCode;

  @TableField("retry_count")
  private Integer retryCount;

  @TableField("next_retry_at")
  private Instant nextRetryAt;

  @TableField("error_code")
  private String errorCode;

  @TableField("error_msg")
  private String errorMsg;

  @TableField("pub_lease_owner")
  private String pubLeaseOwner;

  @TableField("pub_leased_until")
  private Instant pubLeasedUntil;
}
```

### 仓储实现

**File**: `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/persistence/repository/OutboxMessageRepositoryMpImpl.java`

```java
package com.patra.ingest.infra.persistence.repository;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.port.OutboxMessageRepository;
import com.patra.ingest.domain.port.OutboxRelayStore;
import com.patra.ingest.infra.persistence.converter.OutboxMessageConverter;
import com.patra.ingest.infra.persistence.entity.OutboxMessageDO;
import com.patra.ingest.infra.persistence.mapper.OutboxMessageMapper;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * MyBatis-Plus 实现 for Outbox message persistence.
 *
 * <h3>State Machine</h3>
 * PENDING → LEASED (implicit) → PUBLISHED / DEFERRED / FAILED
 *
 * <h3>Concurrency Control</h3>
 * - Version number prevents concurrent overwrites via conditional updates
 * - Lease (leaseOwner/leaseExpireAt) makes message invisible to other consumers
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OutboxMessageRepositoryMpImpl implements OutboxMessageRepository, OutboxRelayStore {

  private final OutboxMessageMapper mapper;
  private final OutboxMessageConverter converter;

  @Override
  public void saveAll(List<OutboxMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return;
    }
    for (OutboxMessage message : messages) {
      OutboxMessageDO entity = converter.toEntity(message);
      mapper.insert(entity);
    }
  }

  @Override
  public List<OutboxMessage> fetchPending(String channel, Instant availableTime, int limit) {
    // ✅ SQL: WHERE status = 'PENDING'
    //          AND (not_before IS NULL OR not_before <= ?)
    //          AND (pub_leased_until IS NULL OR pub_leased_until < ?)
    //          AND (channel = ? OR ? IS NULL)
    //     ORDER BY created_at LIMIT ?
    List<OutboxMessageDO> entities = mapper.selectPendingMessages(channel, availableTime, limit);
    return entities.stream().map(converter::toAggregate).toList();
  }

  @Override
  public boolean acquireLease(Long id, Long expectedVersion, String leaseOwner, Instant leaseExpireAt) {
    // ✅ SQL: UPDATE ing_outbox_message
    //         SET pub_lease_owner = ?, pub_leased_until = ?, version = version + 1
    //         WHERE id = ? AND version = ?
    int rows = mapper.acquireLease(id, expectedVersion, leaseOwner, leaseExpireAt);
    return rows == 1;
  }

  @Override
  public void markPublished(Long id, Long expectedVersion) {
    // ✅ SQL: UPDATE ing_outbox_message
    //         SET status_code = 'PUBLISHED', version = version + 1
    //         WHERE id = ? AND version = ?
    mapper.markPublished(id, expectedVersion);
  }

  @Override
  public void markDeferred(Long id, Long expectedVersion, int retryCount,
                           Instant nextRetryAt, String errorCode, String errorMessage) {
    // ✅ SQL: UPDATE ing_outbox_message
    //         SET status_code = 'PENDING', retry_count = ?, next_retry_at = ?,
    //             error_code = ?, error_msg = ?,
    //             pub_lease_owner = NULL, pub_leased_until = NULL,
    //             version = version + 1
    //         WHERE id = ? AND version = ?
    mapper.markDeferred(id, expectedVersion, retryCount, nextRetryAt, errorCode, errorMessage);
  }

  @Override
  public void markFailed(Long id, Long expectedVersion, int retryCount,
                         String errorCode, String errorMessage) {
    // ✅ SQL: UPDATE ing_outbox_message
    //         SET status_code = 'FAILED', retry_count = ?,
    //             error_code = ?, error_msg = ?, version = version + 1
    //         WHERE id = ? AND version = ?
    mapper.markFailed(id, expectedVersion, retryCount, errorCode, errorMessage);
  }
}
```

---

## 适配器层

### Outbox 中继作业

**File**: `patra-ingest/patra-ingest-adapter/src/main/java/com/patra/ingest/adapter/scheduler/job/OutboxRelayJob.java`

```java
package com.patra.ingest.adapter.scheduler.job;

import cn.hutool.core.net.NetUtil;
import cn.hutool.core.util.IdUtil;
import com.patra.ingest.app.usecase.relay.OutboxRelayUseCase;
import com.patra.ingest.app.usecase.relay.command.OutboxRelayCommand;
import com.patra.ingest.app.usecase.relay.dto.RelayReport;
import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Outbox 中继定时作业. Periodically scans the Outbox table to fetch
 * deliverable messages and attempts to publish them.
 *
 * <p>Idempotency: lease owner identifier includes host + jobId + threadId + uuid
 * to distinguish concurrent instances.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayJob {

  private final OutboxRelayUseCase relayUseCase;
  private final Clock clock;

  /**
   * XXL-Job entrypoint. Parses params, performs relay, and writes statistics.
   */
  @XxlJob("ingestOutboxRelayJob")
  public void execute() {
    Instant now = Instant.now(clock);

    // ✅ Parse job parameters (channel, batchSize, leaseDuration, etc.)
    OutboxRelayJobParam jobParam = parseParam(XxlJobHelper.getJobParam());

    // ✅ Build relay command
    OutboxRelayCommand command = buildInstruction(jobParam, now);

    // ✅ Execute relay
    RelayReport report = relayUseCase.relay(command);

    // ✅ Report statistics to XXL-Job
    String channelDesc = report.channel() != null ? report.channel().channel() : "ALL_CHANNELS";
    XxlJobHelper.handleSuccess(
        String.format(
            "Relay finished channel=%s fetched=%d published=%d retried=%d failed=%d leaseMissed=%d",
            channelDesc, report.fetched(), report.published(),
            report.retried(), report.failed(), report.leaseMissed()));
  }

  /**
   * Builds the lease owner id: host + jobId + threadId + uuid
   * to avoid collisions and aid traceability.
   */
  private String buildLeaseOwner() {
    String host = NetUtil.getLocalHostName();
    return host + '-' + XxlJobHelper.getJobId() + '-'
        + Thread.currentThread().threadId() + '-' + IdUtil.fastSimpleUUID();
  }
}
```

---

## 核心模式

### 1. 基于租约的分布式协调

**问题**: Multiple relay job instances polling same Outbox table → duplicate publishes

**解决方案**: Optimistic locking + lease fields

```java
// ✅ GOOD: Acquire lease before publishing
boolean acquireLease(Long id, Long expectedVersion, String leaseOwner, Instant leaseExpireAt) {
  // SQL: UPDATE ing_outbox_message
  //      SET pub_lease_owner = ?, pub_leased_until = ?, version = version + 1
  //      WHERE id = ? AND version = ?
  int rows = mapper.acquireLease(id, expectedVersion, leaseOwner, leaseExpireAt);
  return rows == 1;  // ✅ Only one instance succeeds
}
```

**Benefits**:
- ✅ Prevents duplicate processing
- ✅ Allows lease takeover if instance crashes (after expiration)
- ✅ No distributed locks required

### 2. 指数退避重试策略

**问题**: Transient errors should be retried, but not too aggressively

**解决方案**: Exponential backoff + max retries

```java
// ✅ GOOD: Calculate next retry time with exponential backoff
Instant calculateNextRetry(int retryCount, Duration initialBackoff) {
  // nextRetry = now + initialBackoff * 2^retryCount
  long delaySeconds = initialBackoff.toSeconds() * (1L << retryCount);
  return Instant.now().plusSeconds(delaySeconds);
}

// Mark as DEFERRED with next retry time
if (retryCount < maxRetries) {
  Instant nextRetry = calculateNextRetry(retryCount, initialBackoff);
  relayStore.markDeferred(id, version, retryCount + 1, nextRetry, errorCode, errorMsg);
} else {
  // ❌ Max retries exhausted → FAILED
  relayStore.markFailed(id, version, retryCount, errorCode, errorMsg);
}
```

### 3. 批处理优化

**问题**: Processing messages one-by-one is slow

**解决方案**: Batch relay + batch audit logging

```java
// ✅ GOOD: Batch processing with single relay log INSERT
public RelayBatchResult execute(RelayPlan plan) {
  // Fetch batch of messages
  List<OutboxMessage> messages = relayStore.fetchPending(channel, now, batchSize);

  // Create log accumulator
  LogAccumulator logAcc = logCoordinator.createAccumulator(batchId);

  // Process messages
  for (OutboxMessage message : messages) {
    processMessage(message, context, logAcc);  // Accumulate logs
  }

  // ✅ Single INSERT statement for all relay logs
  logCoordinator.persistBatch(logAcc);

  return context.toBatchResult(messages.size());
}
```

### 4. 幂等性保证

**问题**: Same business event might trigger multiple Outbox writes

**解决方案**: Unique constraint on (channel, dedupKey)

```sql
-- ✅ GOOD: Unique constraint prevents duplicates
UNIQUE KEY uk_outbox_channel_dedup (channel, dedup_key)
```

```java
// ✅ GOOD: Idempotent write
OutboxMessage message = OutboxMessage.builder()
    .channel("INGEST_TASK")
    .dedupKey("TASK_" + taskId)  // ✅ Unique key
    .aggregateType("TASK")
    .aggregateId(taskId)
    .payloadJson(payload)
    .build();

// If dedupKey already exists → INSERT fails (caught and ignored)
outboxRepository.saveOrUpdate(message);
```

---

## 最佳实践

### ✅ 应该做

| 实践 | 原因 |
|----------|--------|
| **Write outbox in same transaction** | Guarantee atomicity with business data |
| **Use unique (channel, dedupKey)** | Prevent duplicate events |
| **Include lease expiration** | Allow takeover if instance crashes |
| **Use batch processing** | Reduce database round-trips |
| **Implement exponential backoff** | Avoid overwhelming downstream systems |
| **Log relay statistics** | Enable monitoring and alerting |
| **Create audit trail (relay logs)** | Trace message lifecycle |

### ❌ 不应该做

| 反模式 | 问题 |
|--------------|---------|
| **Skip optimistic locking** | Leads to duplicate publishes |
| **Retry indefinitely** | Poison messages block queue |
| **Process messages without lease** | Concurrent processing |
| **Store large payloads in outbox** | Database bloat and performance issues |
| **Publish directly in business transaction** | Breaks transaction atomicity |
| **Ignore error classification** | Can't distinguish transient vs fatal errors |

---

**相关文件：**
- [orchestrator-coordinator-patterns.md](orchestrator-coordinator-patterns.md) - Application layer orchestration
- [domain-modeling-patterns.md](domain-modeling-patterns.md) - Domain entity patterns
- [mybatis-plus-patterns.md](mybatis-plus-patterns.md) - Infrastructure layer persistence
- [event-driven-architecture.md](event-driven-architecture.md) - Event publishing patterns

---

**📝 状态**: ✅ **已完成** - 全面指南： Outbox Pattern implementation from patra-ingest with real examples.
