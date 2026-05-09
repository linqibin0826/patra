---
name: "patra-events"
description: "Patra 事件驱动与 Outbox 模式开发指南。创建 DomainEvent、事务事件监听器、Outbox 中继或可靠消息发布时使用。"
---

# Patra 事件驱动与 Outbox 模式指南

在 Patra 中实现事件驱动通信或可靠消息发布时，按此指南选择正确的模式。

## 内部事件 vs 外部事件

```
需要通知谁？
│
├── 同一服务内的其他聚合
│   └── Spring ApplicationEvent（内部事件）
│       ├── 需要事务一致性 → @TransactionalEventListener(AFTER_COMMIT)
│       ├── 立即处理 → @EventListener
│       └── 异步处理 → @EventListener + @Async
│
└── 其他微服务
    └── Outbox Pattern + MQ（外部事件）
        └── 参考 [outbox-guide.md](resources/outbox-guide.md)
```

## 内部事件快速模板

### 1. 定义事件（domain 层）

```java
/// 任务完成事件。
///
/// 事件链: TaskCompletedEvent → SliceStatusChangedEvent → PlanAggregate 更新
public record TaskCompletedEvent(
    Long taskId,
    Long sliceId,
    Long planId,
    String status,
    Instant occurredAt
) implements DomainEvent {

    public TaskCompletedEvent {
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    public static TaskCompletedEvent of(Long taskId, Long sliceId, Long planId, String status) {
        return new TaskCompletedEvent(taskId, sliceId, planId, status, null);
    }
}
```

**命名规范**：过去时（`TaskCompletedEvent`），不用现在时（~~`TaskCompleteEvent`~~）

### 2. 创建处理器（app 层）

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskCompletedEventHandler {

    private final PlanSliceRepository sliceRepository;

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(TaskCompletedEvent event) {
        try {
            PlanSliceAggregate slice = sliceRepository.findById(event.sliceId())
                .orElseThrow();

            // 幂等性检查
            if (slice.getStatus() == calculateNewStatus(event.status())) {
                return;
            }

            slice.updateStatus(calculateNewStatus(event.status()));
            sliceRepository.save(slice);

        } catch (OptimisticLockingFailureException e) {
            log.warn("并发更新冲突 sliceId={}", event.sliceId());
        } catch (Exception e) {
            log.error("处理失败 taskId={}", event.taskId(), e);
        }
    }
}
```

### 3. 发布事件

```java
// 在 Handler 中通过 ApplicationEventPublisher 发布
eventPublisher.publishEvent(TaskCompletedEvent.of(taskId, sliceId, planId, "COMPLETED"));
```

## 注解组合速查

| 场景 | 注解组合 |
|------|---------|
| 事务提交后处理 | `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Transactional(propagation = REQUIRES_NEW)` |
| 立即同步处理 | `@EventListener` |
| 异步处理 | `@EventListener` + `@Async` |
| 条件处理 | `@EventListener(condition = "#event.status == 'COMPLETED'")` |

## Outbox 模式概览

业务数据和消息在同一事务中原子提交，由中继作业异步发布到 MQ：

```
┌─────────────────────────────┐
│ 业务事务（原子性）            │
│  1. 更新业务数据              │
│  2. 写入 Outbox 表           │
│  └─> COMMIT                  │
└─────────────────────────────┘
         ↓ 中继作业扫描
┌─────────────────────────────┐
│ 中继作业（独立进程）          │
│  1. 获取租约（乐观锁）       │
│  2. 发布到 RocketMQ          │
│  3. 标记为 PUBLISHED         │
└─────────────────────────────┘
```

详细实现模板请参考 [outbox-guide.md](resources/outbox-guide.md)

## 常见陷阱

| 问题 | 原因 | 解决 |
|------|------|------|
| 事件重复处理 | 并发或重试 | 幂等性检查 + 乐观锁 |
| 事件循环 | A → B → A | 确保事件链是线性的 |
| 事务回滚但事件已发 | 使用了 @EventListener | 改用 `AFTER_COMMIT` |
| Outbox 消息重复发布 | 未使用租约机制 | 先获取租约再发布 |

## 资源索引

| 场景 | 参考文档 |
|------|----------|
| 内部事件详细模式 | [event-patterns.md](resources/event-patterns.md) |
| Outbox 完整实现 | [outbox-guide.md](resources/outbox-guide.md) |

## Codex 工具使用说明

原 Claude `allowed-tools` 已改写为 Codex 操作建议，不是权限边界。使用本技能时优先读取事件/Outbox 规则与相关代码、用 `rg`/文件检索定位事件链和处理器，再通过 `apply_patch` 做小范围修改，并在必要时运行 Gradle 验证命令。
