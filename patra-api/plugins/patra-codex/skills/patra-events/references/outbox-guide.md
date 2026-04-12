# Outbox 模式实现指南

## 目录

- 写入 Outbox（业务事务中）
- 状态机
- 关键字段说明
- 中继作业
- 租约机制（防止重复发布）
- 指数退避重试
- 错误分类
- 幂等性保证
- 配置参数
- 代码参考

## 写入 Outbox（业务事务中）

```java
@Transactional
public void completeTask(Long taskId) {
    // 1. 更新业务数据
    TaskAggregate task = taskRepository.findById(taskId).orElseThrow();
    task.markCompleted();
    taskRepository.save(task);

    // 2. 写入 Outbox（同一事务）
    OutboxMessage message = OutboxMessage.builder()
        .channel("ingest.task")           // 逻辑通道 = MQ Topic
        .dedupKey("TASK_" + taskId)       // 幂等键
        .aggregateType("TASK")
        .aggregateId(taskId)
        .partitionKey("PUBMED:HARVEST")   // 分区键（有序投递）
        .opType("TASK_READY")             // 业务语义标签
        .payloadJson(toJson(task))        // 最小必要负载
        .build();
    outboxRepository.saveOrUpdate(message);
}
```

## 状态机

```
PENDING → PUBLISHING → PUBLISHED（终态）
              ↓
          PENDING（重试）→ FAILED（终态，超过重试次数）
              ↓
          DEAD（终态，致命错误不重试）
```

## 关键字段说明

| 字段 | 用途 | 示例 |
|------|------|------|
| `channel` | 逻辑主题（MQ Topic） | `"ingest.task"` |
| `dedup_key` | 幂等键（唯一约束） | `"TASK_123"` |
| `partition_key` | 分区键（保证顺序） | `"PUBMED:HARVEST"` |
| `pub_lease_owner` | 租约持有者 | `"host-123"` |
| `retry_count` | 重试次数 | `0, 1, 2, ...` |

## 中继作业

```java
@XxlJob("outboxRelayJob")
public void relayOutbox() {
    OutboxRelayCommand command = OutboxRelayCommand.builder()
        .channel("ingest.task")
        .batchSize(100)
        .leaseDuration(Duration.ofSeconds(30))
        .build();
    RelayBatchResult report = commandBus.handle(command);
    XxlJobHelper.handleSuccess("中继 " + report.publishedCount() + " 条消息");
}
```

## 租约机制（防止重复发布）

```sql
UPDATE ing_outbox_message
SET pub_lease_owner = ?,
    pub_leased_until = ?,
    status_code = 'PUBLISHING',
    version = version + 1
WHERE id = ?
  AND version = ?
  AND status_code IN ('PENDING', 'PUBLISHING')
  AND (pub_leased_until IS NULL OR pub_leased_until < NOW(6))
```

乐观锁 + 租约过期检查，确保只有一个实例能成功获取租约。

## 指数退避重试

```java
// nextRetry = now + initialBackoff * 2^retryCount
// 第 1 次: 10s, 第 2 次: 20s, 第 3 次: 40s
Instant nextRetry = Instant.now().plusSeconds(
    initialBackoff.toSeconds() * (1L << retryCount));
```

## 错误分类

| 错误类型 | 处理 |
|---------|------|
| 临时错误（网络超时、限流） | DEFERRED → 重试 |
| 致命错误（序列化错误、配置错误） | DEAD → 不重试 |
| 业务错误（校验失败） | DEAD → 不重试 |
| 未知错误 | 有限重试 → FAILED |

## 幂等性保证

唯一约束 `(channel, dedup_key)` 防止重复写入：

```java
try {
    outboxRepository.saveOrUpdate(message);
} catch (DuplicateKeyException e) {
    log.debug("消息已存在: {}", message.getDedupKey());
}
```

## 配置参数

| 参数 | 推荐值 | 说明 |
|------|--------|------|
| `batchSize` | 100 | 每批处理消息数 |
| `leaseDuration` | 30s | 租约持有时间 |
| `maxRetries` | 3 | 最大重试次数 |
| `initialBackoff` | 10s | 初始退避时间 |
| `pollingInterval` | 5s | 扫描间隔 |

## 代码参考

- **领域模型**: `patra-ingest-domain/.../model/entity/OutboxMessage.java`
- **中继执行器**: `patra-ingest-app/.../usecase/relay/executor/OutboxRelayExecutor.java`
- **集成测试**: `patra-ingest-boot/.../integration/outbox/OutboxPatternE2E.java`
