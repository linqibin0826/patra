# Outbox 模式指南

> **目的**: 使用事务性 Outbox 模式实现可靠的事件发布,保证业务数据和消息的原子性

## 🚀 快速开始

### 需要实现可靠的事件发布?

```java
// 1. 在业务事务中写入 Outbox
@Transactional
public void completeTask(Long taskId) {
    // 更新业务数据
    TaskAggregate task = taskRepository.findById(taskId).orElseThrow();
    task.markCompleted();
    taskRepository.save(task);

    // 写入 Outbox (同一事务)
    OutboxMessage message = OutboxMessage.builder()
        .channel("ingest.task")
        .dedupKey("TASK_" + taskId)  // 幂等键
        .aggregateType("TASK")
        .aggregateId(taskId)
        .partitionKey("PUBMED:HARVEST")  // 分区键
        .opType("TASK_READY")
        .payloadJson(toJson(task))
        .build();

    outboxRepository.saveOrUpdate(message);
    // ✅ 业务数据和消息原子性提交
}

// 2. 创建中继作业 (定时扫描 Outbox)
@XxlJob("outboxRelayJob")
public void relayOutbox() {
    OutboxRelayCommand command = OutboxRelayCommand.builder()
        .channel("ingest.task")
        .batchSize(100)
        .leaseDuration(Duration.ofSeconds(30))
        .build();

    RelayBatchResult report = outboxRelayUseCase.relay(command);
    // ✅ 发布到 RocketMQ
}
```

---

## 📊 决策矩阵

### 何时使用 Outbox 模式?

| 场景 | 是否使用 Outbox | 原因 |
|------|----------------|------|
| 跨服务事件发布 | ✅ 是 | 保证事务一致性 |
| 本地事件通知 | ❌ 否 | Spring Events 即可 |
| 需要审计追踪 | ✅ 是 | 完整的消息历史 |
| 高并发发布 | ✅ 是 | 批量处理优化 |
| 简单通知 | ❌ 否 | 直接 MQ 发送 |
| 需要重试机制 | ✅ 是 | 内置重试逻辑 |

### Outbox vs 直接发送

```
需要事务保证?
  ├─ 是 → 使用 Outbox 模式
  │       ├─ 业务数据 + Outbox 原子提交
  │       └─ 中继作业异步发布
  └─ 否 → 可以直接发送?
          ├─ 是 → 直接调用 MQ
          └─ 否 → 使用 Outbox 模式
```

---

## 🎯 核心概念

### 为什么需要 Outbox?

**问题**: 业务事务和 MQ 发布没有事务保证

**❌ 不使用 Outbox 的风险**:
```java
@Transactional
public void completeTask(Long taskId) {
    // 1. 更新数据库
    task.markCompleted();
    taskRepository.save(task);

    // 2. 发送 MQ (在事务内)
    rocketMqProducer.send(message);
    // ❌ 问题:
    // - MQ 成功,DB 回滚 → 重复消息
    // - DB 成功,MQ 失败 → 消息丢失
}
```

**✅ 使用 Outbox 的解决方案**:
```java
@Transactional
public void completeTask(Long taskId) {
    // 1. 更新业务数据
    task.markCompleted();
    taskRepository.save(task);

    // 2. 写入 Outbox 表 (同一事务)
    outboxRepository.save(message);
    // ✅ 原子性提交
}

// 3. 中继作业 (独立进程)
@Scheduled
public void relayOutbox() {
    // 扫描 PENDING 消息
    // 发布到 MQ
    // 标记为 PUBLISHED
}
```

### Outbox 工作流程

```
┌─────────────────────────────────────────┐
│          业务事务 (原子性)               │
├─────────────────────────────────────────┤
│  1. 更新业务数据 (ing_task)             │
│  2. 写入 Outbox (ing_outbox_message)    │
│  └─> COMMIT                             │
└─────────────────────────────────────────┘
                 ↓
┌─────────────────────────────────────────┐
│       中继作业 (独立进程)                │
├─────────────────────────────────────────┤
│  1. 查询 PENDING 消息                   │
│  2. 获取租约 (乐观锁)                   │
│  3. 发布到 RocketMQ                     │
│  4. 标记为 PUBLISHED                    │
│  5. 记录中继日志 (审计)                 │
└─────────────────────────────────────────┘
```

---

## 🏗️ 实现指南

### 1. 领域模型

<details>
<summary>查看 OutboxMessage 实体</summary>

**核心字段** (基于实际实现):
```java
public final class OutboxMessage {
    // 核心标识
    private final Long id;
    private final Long version;  // 乐观锁
    private final String aggregateType;  // 聚合类型 (如 "TASK")
    private final Long aggregateId;      // 聚合 ID

    // 路由信息
    private final String channel;        // 逻辑主题 (如 "ingest.task")
    private final String opType;         // 操作类型 (如 "TASK_READY")
    private final String partitionKey;   // 分区键 (如 "PUBMED:HARVEST")
    private final String dedupKey;       // 幂等键 (如 "TASK_123")

    // 消息内容
    private final String payloadJson;    // JSON 负载
    private final String headersJson;    // JSON 头部

    // 调度和重试
    private final Instant notBefore;     // 最早发布时间
    private final String statusCode;     // PENDING, PUBLISHING, PUBLISHED, FAILED, DEAD
    private final Integer retryCount;    // 重试次数
    private final Instant nextRetryAt;   // 下次重试时间

    // 错误追踪
    private final String errorCode;
    private final String errorMsg;

    // 租约管理
    private final String leaseOwner;     // 租约持有者
    private final Instant leaseExpireAt; // 租约过期时间

    // ✅ 领域行为方法
    public int computeNextAttempt() {
        return (retryCount == null ? 0 : retryCount) + 1;
    }

    public boolean canRetry(int maxAttempts) {
        return computeNextAttempt() <= maxAttempts;
    }

    public boolean hasActiveLease(Instant now) {
        return leaseOwner != null && leaseExpireAt != null && leaseExpireAt.isAfter(now);
    }

    public boolean isPending() {
        return "PENDING".equals(statusCode);
    }

    public boolean isPublishing() {
        return "PUBLISHING".equals(statusCode);
    }

    public boolean isTerminal() {
        return "PUBLISHED".equals(statusCode) || "FAILED".equals(statusCode) || "DEAD".equals(statusCode);
    }

    public boolean isReadyToRelay(Instant now) {
        if (!isPending()) return false;
        if (notBefore != null && notBefore.isAfter(now)) return false;
        if (nextRetryAt != null && nextRetryAt.isAfter(now)) return false;
        return true;
    }

    public boolean isLeaseExpired(Instant now) {
        return leaseExpireAt == null || leaseExpireAt.isBefore(now);
    }
}
```

**参考**: `patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/entity/OutboxMessage.java`

</details>

### 2. 数据库设计

<details>
<summary>查看实际表结构 (ing_outbox_message)</summary>

```sql
CREATE TABLE IF NOT EXISTS `ing_outbox_message`
(
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT 'PK · OutboxID',
    `aggregate_type`   VARCHAR(32)     NOT NULL COMMENT 'Aggregate type: e.g. TASK/PLAN/...',
    `aggregate_id`     BIGINT UNSIGNED NOT NULL COMMENT 'Aggregate root ID',
    `channel`          VARCHAR(64)     NOT NULL COMMENT 'Logical channel=target Topic, e.g. ingest.task',
    `op_type`          VARCHAR(32)     NOT NULL COMMENT 'Business semantic label: e.g. TASK_READY',
    `partition_key`    VARCHAR(128)    NOT NULL COMMENT 'Partitioning/ordering routing key',
    `dedup_key`        VARCHAR(128)    NOT NULL COMMENT 'Idempotency key',
    `payload_json`     JSON            NOT NULL COMMENT 'Message payload (JSON)',
    `headers_json`     JSON            NULL COMMENT 'Extension headers (JSON)',
    `not_before`       TIMESTAMP(6)    NULL COMMENT 'Earliest publishable time (UTC)',
    `published_at`     TIMESTAMP(6)    NULL COMMENT 'Successful publish timestamp (UTC)',

    `status_code`      VARCHAR(16)     NOT NULL DEFAULT 'PENDING'
        COMMENT 'Publishing status: PENDING/PUBLISHING/PUBLISHED/FAILED/DEAD',
    `retry_count`      INT UNSIGNED    NOT NULL DEFAULT 0
        COMMENT 'Publishing retry count',
    `next_retry_at`    TIMESTAMP(6)    NULL
        COMMENT 'Next publishing attempt time (UTC)',
    `error_code`       VARCHAR(64)     NULL
        COMMENT 'Latest publishing error code',
    `error_msg`        VARCHAR(512)    NULL
        COMMENT 'Latest publishing error details',

    `pub_lease_owner`  VARCHAR(128)    NULL
        COMMENT 'Publisher lease holder (instance ID or workerId)',
    `pub_leased_until` TIMESTAMP(6)    NULL
        COMMENT 'Publisher lease expiration (UTC)',

    -- 审计字段 (统一审计字段规范)
    `record_remarks`   JSON            NULL
        COMMENT 'JSON array, remarks/change log',
    `version`          BIGINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT 'Optimistic lock version number',
    `ip_address`       VARBINARY(16)   NULL
        COMMENT 'Requester IP (binary, supports IPv4/IPv6)',
    `created_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        COMMENT 'Creation time (UTC)',
    `created_by`       BIGINT UNSIGNED NULL
        COMMENT 'Creator ID',
    `created_by_name`  VARCHAR(100)    NULL
        COMMENT 'Creator name',
    `updated_at`       TIMESTAMP(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6)
        COMMENT 'Update time (UTC)',
    `updated_by`       BIGINT UNSIGNED NULL
        COMMENT 'Updater ID',
    `updated_by_name`  VARCHAR(100)    NULL
        COMMENT 'Updater name',
    `deleted`          TINYINT(1)      NOT NULL DEFAULT 0
        COMMENT 'Soft delete: 0=active, 1=deleted',

    PRIMARY KEY (`id`),

    -- 幂等性约束
    UNIQUE KEY `uk_outbox_channel_dedup` (`channel`, `dedup_key`),

    -- 中继查询优化
    KEY `idx_outbox_status_time` (`status_code`, `not_before`, `id`),
    KEY `idx_outbox_partition` (`channel`, `partition_key`, `status_code`),
    KEY `idx_outbox_lease` (`status_code`, `pub_leased_until`),

    -- V2.0 增强 - UNION ALL 查询优化
    KEY `idx_pending_relay` (`channel`, `status_code`, `not_before`, `id`)
        COMMENT 'PENDING 消息中继查询优化',
    KEY `idx_publishing_lease` (`channel`, `status_code`, `pub_leased_until`, `id`)
        COMMENT 'PUBLISHING 消息租约过期查询优化'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

**关键索引说明**:
- `uk_outbox_channel_dedup`: 保证幂等性 (channel + dedupKey 唯一)
- `idx_pending_relay`: PENDING 消息批量获取优化
- `idx_publishing_lease`: 租约过期回收优化
- `idx_outbox_partition`: 分区处理优化

**参考**: `patra-ingest-infra/src/main/resources/db/migration/V0.1.0__init_ingest_schema.sql:519-571`

</details>

### 3. 中继逻辑

<details>
<summary>查看中继执行器实现框架</summary>

**职责分离**:
- `OutboxRelayOrchestrator`: 应用层编排器
- `OutboxRelayExecutor`: 批量执行器
- `RelayLeaseCoordinator`: 租约协调器
- `RelayPublishCoordinator`: 发布协调器
- `RelayLogCoordinator`: 日志协调器

**核心流程** (简化版):
```java
@Component
@RequiredArgsConstructor
public class OutboxRelayExecutor {
    private final OutboxRelayStore relayStore;
    private final RelayLeaseCoordinator leaseCoordinator;
    private final RelayPublishCoordinator publishCoordinator;

    public RelayBatchResult execute(RelayPlan plan) {
        // 1. 获取待发布消息 (PENDING 或租约过期的 PUBLISHING)
        List<OutboxMessage> messages = relayStore.fetchPending(
            plan.channel(),
            plan.triggeredAt(),
            plan.batchSize()
        );

        if (messages.isEmpty()) {
            return RelayBatchResult.empty(plan.channel());
        }

        // 2. 创建批次 ID
        RelayBatchId batchId = RelayBatchId.generate(plan.triggeredAt());

        // 3. 处理消息
        RelayContext context = new RelayContext(plan);
        for (OutboxMessage message : messages) {
            processMessage(message, context, batchId);
        }

        return context.toBatchResult(messages.size());
    }

    private void processMessage(
        OutboxMessage message,
        RelayContext context,
        RelayBatchId batchId
    ) {
        Instant startTime = Instant.now();

        // ✅ 尝试获取租约
        if (!leaseCoordinator.tryAcquire(message, context.plan())) {
            context.onLeaseMissed(message);
            return;
        }

        // ✅ 发布消息
        RelayResult result = publishCoordinator.publish(message, context.plan());

        if (result.isSuccess()) {
            context.onPublished(message, result.attemptNumber());
        } else if (result.isDeferred()) {
            context.onDeferred(message, result);
        } else {
            context.onFailed(message, result);
        }
    }
}
```

**参考**: `patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/relay/executor/OutboxRelayExecutor.java`

</details>

---

## 📋 速查表

### 状态机转换

| 状态 | 说明 | 下一状态 |
|------|------|---------|
| **PENDING** | 初始状态,待发布 | PUBLISHING (获取租约后) |
| **PUBLISHING** | 已获取租约,发布中 | PUBLISHED (成功) / PENDING (重试) / FAILED (失败) |
| **PUBLISHED** | 发布成功 | 终态 |
| **FAILED** | 发布失败 (超过重试) | 终态 |
| **DEAD** | 致命错误,不再重试 | 终态 |

**注意**: PUBLISHING 是中间状态,通过租约机制防止并发发布。

### 关键字段说明

| 字段 | 用途 | 示例值 |
|------|------|--------|
| `channel` | 逻辑主题 (对应 MQ Topic) | "ingest.task" |
| `dedup_key` | 幂等键 (保证唯一性) | "TASK_123" |
| `partition_key` | 分区键 (保证顺序) | "PUBMED:HARVEST" |
| `status_code` | 发布状态 | "PENDING" / "PUBLISHING" / "PUBLISHED" |
| `pub_lease_owner` | 租约持有者 | "host-123-456" |
| `pub_leased_until` | 租约过期时间 | 30 秒后 |
| `retry_count` | 重试次数 | 0, 1, 2, ... |
| `next_retry_at` | 下次重试时间 | 10s, 20s, 40s (指数退避) |

### 中继配置参数

| 参数 | 推荐值 | 说明 |
|------|--------|------|
| `batchSize` | 100 | 每批处理消息数 |
| `leaseDuration` | 30s | 租约持有时间 |
| `maxRetries` | 3 | 最大重试次数 |
| `initialBackoff` | 10s | 初始退避时间 |
| `pollingInterval` | 5s | 扫描间隔 |

---

## 🔧 核心模式

### 模式 1: 基于租约的分布式协调

**问题**: 多个中继实例同时处理同一消息

**解决方案**: 乐观锁 + 租约字段

<details>
<summary>查看实现代码</summary>

```java
// ✅ 获取租约 (使用乐观锁)
public boolean acquireLease(
    Long id,
    Long expectedVersion,
    String leaseOwner,
    Instant leaseExpireAt
) {
    // SQL: UPDATE ing_outbox_message
    //      SET pub_lease_owner = ?,
    //          pub_leased_until = ?,
    //          status_code = 'PUBLISHING',
    //          version = version + 1
    //      WHERE id = ?
    //        AND version = ?
    //        AND status_code IN ('PENDING', 'PUBLISHING')
    //        AND (pub_leased_until IS NULL OR pub_leased_until < NOW(6))

    int rows = mapper.acquireLease(id, expectedVersion, leaseOwner, leaseExpireAt);
    return rows == 1;  // ✅ 只有一个实例成功
}
```

**关键点**:
- WHERE 条件包含 `version = ?` (乐观锁)
- WHERE 条件检查租约是否过期
- UPDATE 同时设置 `status_code = 'PUBLISHING'`
- 只有一个实例的 UPDATE 能成功 (CAS 语义)

</details>

### 模式 2: 指数退避重试

**问题**: 临时错误应该重试,但不能太激进

**解决方案**: 指数退避 + 最大重试次数

<details>
<summary>查看实现代码</summary>

```java
// ✅ 计算下次重试时间
public Instant calculateNextRetry(int retryCount, Duration initialBackoff) {
    // nextRetry = now + initialBackoff * 2^retryCount
    long delaySeconds = initialBackoff.toSeconds() * (1L << retryCount);
    return Instant.now().plusSeconds(delaySeconds);
}

// ✅ 标记为延迟重试
if (message.canRetry(maxRetries)) {
    Instant nextRetry = calculateNextRetry(message.getRetryCount(), initialBackoff);
    relayStore.markDeferred(
        message.getId(),
        message.getVersion(),
        message.computeNextAttempt(),
        nextRetry,
        errorCode,
        errorMsg
    );
} else {
    // ❌ 超过最大重试 → FAILED
    relayStore.markFailed(
        message.getId(),
        message.getVersion(),
        message.getRetryCount(),
        errorCode,
        errorMsg
    );
}
```

**退避时间示例**:
- 第 1 次重试: 10s
- 第 2 次重试: 20s
- 第 3 次重试: 40s
- 第 4 次重试: 80s

</details>

### 模式 3: 批量处理优化

**问题**: 逐条处理消息效率低

**解决方案**: 批量查询 + 批量日志

<details>
<summary>查看优化策略</summary>

```java
// ✅ 批量获取消息 (UNION ALL 优化)
// 查询 1: PENDING 消息
// 查询 2: PUBLISHING 但租约过期的消息
List<OutboxMessage> messages = relayStore.fetchPending(channel, now, 100);

// ✅ 批量处理
for (OutboxMessage message : messages) {
    processMessage(message, context);  // 逐条发布
}

// ✅ 批量插入中继日志 (一次 INSERT)
logCoordinator.persistBatch(logAccumulator);
```

**性能优化**:
1. 使用 `idx_pending_relay` 和 `idx_publishing_lease` 索引
2. 批量插入中继日志 (减少数据库往返)
3. 控制批次大小 (避免长事务)

</details>

### 模式 4: 幂等性保证

**问题**: 同一业务事件可能触发多次 Outbox 写入

**解决方案**: 唯一约束 (channel, dedup_key)

<details>
<summary>查看幂等性实现</summary>

```sql
-- ✅ 唯一约束防止重复
UNIQUE KEY uk_outbox_channel_dedup (channel, dedup_key)
```

```java
// ✅ 幂等写入
OutboxMessage message = OutboxMessage.builder()
    .channel("ingest.task")
    .dedupKey("TASK_" + taskId)  // ✅ 唯一键
    .aggregateType("TASK")
    .aggregateId(taskId)
    .build();

// 如果 dedupKey 已存在 → INSERT 失败 (被捕获并忽略)
try {
    outboxRepository.saveOrUpdate(message);
} catch (DuplicateKeyException e) {
    // 幂等性保证 - 忽略重复
    log.debug("Message already exists: {}", message.getDedupKey());
}
```

**幂等键设计**:
- 任务场景: `"TASK_" + taskId`
- 事件场景: `"EVENT_" + aggregateId + "_" + eventType`
- 全局唯一: 保证 (channel + dedupKey) 组合唯一

</details>

---

## ⚠️ 常见问题与解决

### 问题 1: 消息重复发布

**症状**: 同一消息被发送多次到 MQ

**原因**: 未使用租约机制或乐观锁

<details>
<summary>查看解决方案</summary>

```java
// ❌ 错误: 不获取租约直接发布
List<OutboxMessage> messages = fetchPending();
for (OutboxMessage message : messages) {
    publish(message);  // ❌ 多个实例可能同时处理
}

// ✅ 正确: 先获取租约
for (OutboxMessage message : messages) {
    if (leaseCoordinator.tryAcquire(message, plan)) {  // ✅ 只有一个实例成功
        publishCoordinator.publish(message, plan);
    }
}
```

**关键点**:
- 必须先获取租约再发布
- 使用乐观锁确保 CAS 语义
- 租约过期后自动释放

</details>

### 问题 2: 死信消息堆积

**症状**: FAILED/DEAD 状态消息越来越多

**原因**: 错误分类不当,可恢复错误被标记为 FAILED

<details>
<summary>查看错误分类策略</summary>

```java
// ✅ 错误分类
if (isTransientError(error)) {
    // 网络超时、限流等 → DEFERRED (重试)
    markDeferred(message, calculateNextRetry(message.getRetryCount()));
} else if (isBusinessError(error)) {
    // 业务校验失败 → DEAD (不重试)
    markDead(message, errorCode, errorMsg);
} else {
    // 未知错误 → 有限重试
    if (message.canRetry(maxRetries)) {
        markDeferred(message, calculateNextRetry(message.getRetryCount()));
    } else {
        markFailed(message, errorCode, errorMsg);
    }
}
```

**错误分类**:
- **临时错误** (TRANSIENT): 网络超时、限流、MQ 不可用 → 重试
- **致命错误** (FATAL): 序列化错误、配置错误 → DEAD
- **业务错误**: 校验失败、数据不存在 → DEAD
- **未知错误**: 有限重试后 → FAILED

</details>

### 问题 3: Outbox 表性能下降

**症状**: 查询 Outbox 表变慢

**原因**: 历史消息未清理,表过大

<details>
<summary>查看归档策略</summary>

```java
// ✅ 定期归档已发布消息
@Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨 2 点
public void archivePublishedMessages() {
    // 归档 7 天前的 PUBLISHED 消息
    Instant cutoff = Instant.now().minus(Duration.ofDays(7));

    // 1. 复制到归档表 (可选)
    archiveRepository.archiveMessages(cutoff);

    // 2. 删除原始记录
    int deleted = outboxRepository.deletePublishedBefore(cutoff);
    log.info("Archived {} published messages before {}", deleted, cutoff);
}

// ✅ 归档 FAILED/DEAD 消息
@Scheduled(cron = "0 0 3 * * ?")  // 每天凌晨 3 点
public void archiveFailedMessages() {
    Instant cutoff = Instant.now().minus(Duration.ofDays(30));
    int deleted = outboxRepository.deleteFailedBefore(cutoff);
    log.info("Archived {} failed messages before {}", deleted, cutoff);
}
```

**归档策略**:
- PUBLISHED: 7 天后归档
- FAILED/DEAD: 30 天后归档
- 归档到历史表或对象存储
- 使用批量删除 (避免长事务)

</details>

---

## ✅ 最佳实践清单

### 设计原则
- [ ] Outbox 和业务数据在同一事务
- [ ] 使用唯一约束保证幂等性 (`uk_outbox_channel_dedup`)
- [ ] 租约机制防止重复处理
- [ ] 批量处理提升性能

### 中继作业
- [ ] 使用乐观锁获取租约
- [ ] 实现指数退避重试
- [ ] 错误分类 (临时 vs 致命 vs 业务)
- [ ] 记录完整的中继日志 (`ing_outbox_relay_log`)

### 运维监控
- [ ] 监控 PENDING 消息堆积
- [ ] 监控 FAILED/DEAD 消息数量
- [ ] 监控中继作业成功率
- [ ] 定期归档历史消息

### 性能优化
- [ ] 批量查询和处理 (使用 UNION ALL)
- [ ] 合理的索引设计 (`idx_pending_relay`, `idx_publishing_lease`)
- [ ] 控制 Outbox 表大小
- [ ] 避免长事务

---

## 📚 相关文档

### 核心概念
- [orchestrator-coordinator-patterns.md](orchestrator-coordinator-patterns.md) - 应用层编排
- [event-driven-architecture.md](event-driven-architecture.md) - 事件驱动架构
- [mybatis-plus-patterns.md](mybatis-plus-patterns.md) - 持久化实现

### 测试指南
- [testing-guide.md](testing-guide.md) - 完整测试策略
- [test-templates-infrastructure.md](test-templates-infrastructure.md) - 基础设施层测试

### 代码参考
- **领域模型**: `patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/entity/OutboxMessage.java`
- **数据库设计**: `patra-ingest-infra/src/main/resources/db/migration/V0.1.0__init_ingest_schema.sql` (行 512-653)
- **中继执行器**: `patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/relay/executor/OutboxRelayExecutor.java`
- **集成测试**: `patra-ingest-boot/src/test/java/com/patra/ingest/integration/outbox/OutboxPatternE2ETest.java`
