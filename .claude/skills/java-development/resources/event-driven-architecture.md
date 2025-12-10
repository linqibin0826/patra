# 事件驱动架构指南

> **目的**: 使用领域事件实现聚合根之间的松耦合通信和状态传播

## 🚀 快速开始

### 需要创建新事件？

```java
// 1. 在 domain 层创建事件 (使用 record)
public record TaskCompletedEvent(
    Long taskId,
    Long sliceId,
    String status,
    Instant occurredAt
) implements DomainEvent {
    // 自动填充时间戳
    public TaskCompletedEvent {
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }
}

// 2. 在 app 层创建处理器
@Component
@RequiredArgsConstructor
public class TaskCompletedEventHandler {
    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(TaskCompletedEvent event) {
        // 处理逻辑
    }
}

// 3. 发布事件
eventPublisher.publishEvent(new TaskCompletedEvent(...));
```

### 需要处理外部系统事件？

参见 [Outbox 模式集成](#outbox-pattern-integration)

---

## 📊 决策矩阵

### 何时使用事件？

| 场景 | 使用事件? | 原因 |
|-----|----------|------|
| 聚合根状态变化需要通知其他聚合 | ✅ 是 | 松耦合 |
| 需要审计追踪 | ✅ 是 | 事件历史 |
| 跨服务通信 | ✅ 是 (Outbox) | 可靠传递 |
| 简单的同步调用 | ❌ 否 | 直接调用更简单 |
| 需要立即响应 | ❌ 否 | 事件是异步的 |

### 事件类型选择

```
需要跨服务通信？
  ├─ 是 → 使用 Outbox Pattern + MQ
  └─ 否 → 需要事务一致性？
          ├─ 是 → @TransactionalEventListener(AFTER_COMMIT)
          └─ 否 → @EventListener
```

---

## 🎯 常见场景与模板

### 场景 1: 聚合状态变化触发下游更新

**案例**: Task 完成 → 更新 Slice → 更新 Plan

<details>
<summary>查看完整实现</summary>

```java
/// 1. 定义事件
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

    // 工厂方法
    public static TaskCompletedEvent of(Long taskId, Long sliceId, Long planId, String status) {
        return new TaskCompletedEvent(taskId, sliceId, planId, status, null);
    }
}

/// 2. 事件处理器
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskCompletedEventHandler {

    private final PlanSliceRepository sliceRepository;
    private final ApplicationEventPublisher eventPublisher;

    @TransactionalEventListener(phase = AFTER_COMMIT)
    @Transactional(propagation = REQUIRES_NEW)
    public void handle(TaskCompletedEvent event) {
        try {
            // 查询并更新 Slice
            PlanSliceAggregate slice = sliceRepository.findById(event.sliceId())
                .orElseThrow(() -> new IllegalStateException("Slice not found"));

            SliceStatus oldStatus = slice.getStatus();
            SliceStatus newStatus = calculateNewStatus(event.status());

            // 幂等性检查
            if (oldStatus == newStatus) {
                return;
            }

            slice.updateStatus(newStatus);
            sliceRepository.save(slice);

            // 触发下一个事件
            eventPublisher.publishEvent(
                SliceStatusChangedEvent.of(event.sliceId(), event.planId(),
                    oldStatus.getCode(), newStatus.getCode())
            );

        } catch (OptimisticLockingFailureException e) {
            log.warn("并发更新冲突 sliceId={}", event.sliceId());
        } catch (Exception e) {
            log.error("处理失败 taskId={}", event.taskId(), e);
        }
    }
}
```

</details>

### 场景 2: 跨服务事件通知

**案例**: 通知下游服务任务完成

<details>
<summary>查看 Outbox 模式实现</summary>

```java
@Transactional
public void completeTask(Long taskId) {
    // 1. 更新聚合
    TaskAggregate task = taskRepository.findById(taskId)
        .orElseThrow();
    task.markCompleted();
    taskRepository.save(task);

    // 2. 写入 Outbox (同一事务)
    OutboxMessage message = OutboxMessage.builder()
        .channel("TASK_COMPLETED")
        .dedupKey("TASK_" + taskId)
        .aggregateType("Task")
        .aggregateId(taskId)
        .payloadJson(toJson(TaskCompletedPayload.from(task)))
        .build();
    outboxRepository.saveOrUpdate(message);
}
```

</details>

### 场景 3: 批处理事件聚合

**案例**: 批量更新后发布汇总事件

<details>
<summary>查看批处理实现</summary>

```java
@Component
public class BatchEventAggregator {

    private final Map<Long, List<TaskCompletedEvent>> buffer = new ConcurrentHashMap<>();

    @EventListener
    public void collect(TaskCompletedEvent event) {
        buffer.computeIfAbsent(event.planId(), k -> new ArrayList<>())
            .add(event);
    }

    @Scheduled(fixedDelay = 5000) // 每5秒聚合一次
    @Transactional
    public void flush() {
        buffer.forEach((planId, events) -> {
            if (!events.isEmpty()) {
                // 发布批量事件
                eventPublisher.publishEvent(
                    new BatchTasksCompletedEvent(planId, events)
                );
            }
        });
        buffer.clear();
    }
}
```

</details>

---

## 📋 速查表

### 事件命名规范

| ✅ 正确 (过去时) | ❌ 错误 (现在时) |
|-----------------|------------------|
| TaskCompletedEvent | TaskCompleteEvent |
| SliceStatusChangedEvent | SliceStatusChangeEvent |
| PlanCreatedEvent | PlanCreateEvent |
| OutboxMessagePublishedEvent | OutboxMessagePublishEvent |

### 注解组合

| 场景 | 注解组合 |
|------|---------|
| 事务提交后处理 | `@TransactionalEventListener(phase = AFTER_COMMIT)`<br>`@Transactional(propagation = REQUIRES_NEW)` |
| 立即处理 | `@EventListener` |
| 异步处理 | `@EventListener`<br>`@Async` |
| 条件处理 | `@EventListener(condition = "#event.status == 'COMPLETED'")` |

### 错误处理策略

| 错误类型 | 处理方式 |
|---------|---------|
| OptimisticLockingFailureException | 记录警告，跳过（其他线程已处理） |
| IllegalStateException | 记录错误，依赖对账修复 |
| 网络异常 | 重试或写入死信队列 |
| 未知异常 | 记录错误，不抛出（让其他处理器继续） |

---

## 🔧 完整示例

### 完整的领域事件定义

<details>
<summary>查看 TaskCompletedEvent 完整实现</summary>

```java
package com.patra.ingest.domain.event;

import com.patra.common.domain.DomainEvent;
import java.time.Instant;

/// 任务执行完成时发布的领域事件。
/// 
/// 事件链: TaskCompletedEvent → SliceStatusChangedEvent → PlanAggregate 更新
public record TaskCompletedEvent(
    Long taskId,
    Long sliceId,
    Long planId,
    String status,
    String errorCode,
    String errorMessage,
    Instant finishedAt,
    Instant occurredAt)
    implements DomainEvent {

  // ✅ 在紧凑构造器中自动填充时间戳
  public TaskCompletedEvent {
    occurredAt = occurredAt == null ? Instant.now() : occurredAt;
  }

  // ✅ 成功完成的工厂方法
  public static TaskCompletedEvent of(
      Long taskId, Long sliceId, Long planId, String status, Instant finishedAt) {
    return new TaskCompletedEvent(
        taskId, sliceId, planId, status, null, null, finishedAt, Instant.now());
  }

  // ✅ 失败完成的工厂方法
  public static TaskCompletedEvent ofFailure(
      Long taskId, Long sliceId, Long planId, String status,
      String errorCode, String errorMessage, Instant finishedAt) {
    return new TaskCompletedEvent(
        taskId, sliceId, planId, status, errorCode, errorMessage, finishedAt, Instant.now());
  }
}
```

</details>

### 项目中的事件清单

| 事件 | 触发时机 | 目的 |
|-------|---------|---------|
| **TaskQueuedEvent** | Task 创建并持久化 | 度量指标、审计追踪 |
| **TaskCompletedEvent** | Task 到达终态 | 触发 Slice 状态更新 |
| **SliceStatusChangedEvent** | Slice 状态变化 | 触发 Plan 状态聚合 |
| **OutboxMessagePublishedEvent** | 消息发布到 MQ | 审计、度量指标 |
| **OutboxMessageDeferredEvent** | 消息重试已调度 | 监控重试率 |

---

## ⚠️ 常见问题与解决

### 问题 1: 事件重复处理

**症状**: 同一事件被多次处理导致数据不一致

**解决方案**:
```java
// ✅ 幂等性检查
if (oldStatus == newStatus) {
    log.debug("状态未变化，跳过更新");
    return;  // 幂等处理
}

// ✅ 使用乐观锁
try {
    slice.updateStatus(newStatus);
    sliceRepository.save(slice);
} catch (OptimisticLockingFailureException e) {
    log.warn("并发更新，跳过");
}
```

### 问题 2: 事件循环

**症状**: A → B → A 导致无限循环

**解决方案**:
```java
// ❌ 错误: 循环事件链
handleTaskCompleted() → publishEvent(TaskQueuedEvent)
handleTaskQueued() → publishEvent(TaskCompletedEvent)

// ✅ 正确: 线性事件链
TaskCompletedEvent → SliceStatusChangedEvent → PlanStatusChangedEvent
```

### 问题 3: 事务幻读

**症状**: 事务回滚但事件已发布

**解决方案**:
```java
// ✅ 使用 AFTER_COMMIT
@TransactionalEventListener(phase = AFTER_COMMIT)
@Transactional(propagation = REQUIRES_NEW)
```

---

## 🔗 Outbox 模式集成 {#outbox-pattern-integration}

### 内部 vs 外部事件

| 类型 | 机制 | 使用场景 |
|-----|-----|---------|
| **内部事件** | Spring ApplicationEventPublisher | 服务内聚合状态传播 |
| **外部事件** | Outbox Pattern + MQ | 跨服务集成、下游通知 |

### 跨服务事件实现

```java
@Transactional
public void completeTask(Long taskId) {
    // 1. 更新聚合
    TaskAggregate task = taskRepository.findById(taskId).orElseThrow();
    task.markCompleted();
    taskRepository.save(task);

    // 2. 写入 Outbox (同一事务)
    OutboxMessage message = OutboxMessage.builder()
        .channel("TASK_COMPLETED")
        .dedupKey("TASK_" + taskId)
        .aggregateType("Task")
        .aggregateId(taskId)
        .payloadJson(toJson(task))
        .build();
    outboxRepository.saveOrUpdate(message);
    // ✅ 原子性保证
}
```

---

## ✅ 最佳实践清单

### 设计原则
- [ ] 使用 record 定义不可变事件
- [ ] 使用过去时命名（TaskCompletedEvent 而非 TaskCompleteEvent）
- [ ] 包含完整上下文（避免处理器查询）
- [ ] 自动填充 occurredAt 时间戳

### 处理器实现
- [ ] AFTER_COMMIT + REQUIRES_NEW 组合
- [ ] 幂等性检查（状态对比）
- [ ] 处理 OptimisticLockingFailureException
- [ ] 记录错误但不抛出异常

### 测试要点
- [ ] 测试事件发布
- [ ] 测试处理器逻辑
- [ ] 测试幂等性
- [ ] 测试并发场景

---

## 📚 深入学习

<details>
<summary>高级主题</summary>

### 事件溯源 (Event Sourcing)
- 使用事件作为唯一真相源
- 通过重放事件重建状态
- 适用于审计要求高的场景

### CQRS + 事件驱动
- 命令端发布事件
- 查询端订阅并更新读模型
- 实现读写分离

### Saga 模式
- 长事务的分布式协调
- 补偿事务处理失败
- 基于事件的编排

</details>

---

**相关文档**:
- [outbox-pattern.md](outbox-pattern.md) - 外部事件发布
- [commandbus.md](../../rules/tech/commandbus.md) - CommandHandler 事件发布
