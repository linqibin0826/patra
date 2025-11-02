# 事件驱动架构

**目的**: 领域事件实现聚合根和限界上下文之间的松耦合和响应式工作流。

---

## 目录

1. [概述](#概述)
2. [领域事件](#领域事件)
3. [事件处理器](#事件处理器)
4. [事件链](#事件链)
5. [与 Outbox 模式的集成](#与-outbox-模式的集成)
6. [最佳实践](#最佳实践)

---

## 概述

### 什么是事件驱动架构?

**事件驱动架构 (EDA)** 使用领域事件在组件之间传递状态变化。事件的优势:
- ✅ **松耦合**: 聚合根不需要知道其消费者
- ✅ **响应式工作流**: 自动传播状态
- ✅ **审计追踪**: 完整的事件历史
- ✅ **最终一致性更新**: 异步处理

### Papertrace 事件流

```
┌─────────────────────────────────────────────────────────────┐
│              Task Completion Event Chain                    │
├─────────────────────────────────────────────────────────────┤
│  1. Task execution completes                                │
│     → Publishes TaskCompletedEvent                          │
│                                                             │
│  2. TaskCompletedEventHandler                               │
│     → Calculates Slice status (1:1 mapping)                 │
│     → Updates Slice aggregate                               │
│     → Publishes SliceStatusChangedEvent                     │
│                                                             │
│  3. SliceStatusChangedEventHandler                          │
│     → Aggregates all Slice statuses for Plan                │
│     → Updates Plan aggregate                                │
│     → Publishes PlanStatusChangedEvent (optional)           │
└─────────────────────────────────────────────────────────────┘
```

---

## 领域事件

### 事件设计模式

patra-ingest 中的领域事件遵循以下约定:

**✅ 事件最佳实践:**
- 使用 Java `record` 实现不可变性
- 使用过去时命名 (例如: `TaskCompletedEvent`, 而非 `TaskCompleteEvent`)
- 包含所有相关上下文 (ID、状态、时间戳)
- 自动填充 `occurredAt` (如果未提供)
- 为常见场景提供工厂方法

### 完整事件示例

**文件**: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/event/TaskCompletedEvent.java`

```java
package com.patra.ingest.domain.event;

import com.patra.common.domain.DomainEvent;
import java.time.Instant;

/**
 * 任务执行完成时发布的领域事件。
 *
 * <p>事件链: TaskCompletedEvent → SliceStatusChangedEvent → PlanAggregate 更新
 */
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

### patra-ingest 事件示例

| 事件 | 触发时机 | 目的 |
|-------|---------|---------|
| **TaskQueuedEvent** | Task 创建并持久化 | 度量指标、审计追踪 |
| **TaskCompletedEvent** | Task 到达终态 | 触发 Slice 状态更新 |
| **SliceStatusChangedEvent** | Slice 状态变化 | 触发 Plan 状态聚合 |
| **OutboxMessagePublishedEvent** | 消息发布到 MQ | 审计、度量指标 |
| **OutboxMessageDeferredEvent** | 消息重试已调度 | 监控重试率 |

---

## 事件处理器

### 事件处理器模式

**文件**: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/eventhandler/TaskCompletedEventHandler.java`

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskCompletedEventHandler {

  private final TaskRepository taskRepository;
  private final PlanSliceRepository sliceRepository;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * 在事务提交后处理 TaskCompletedEvent。
   *
   * ✅ @TransactionalEventListener(phase = AFTER_COMMIT)
   * ✅ @Transactional(propagation = REQUIRES_NEW)
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(TaskCompletedEvent event) {
    try {
      log.debug("处理 TaskCompletedEvent taskId={} sliceId={}",
          event.taskId(), event.sliceId());

      // 1. 查询关联的 Task (与 Slice 1:1 关系)
      TaskAggregate task = taskRepository.findBySliceId(event.sliceId())
          .orElseThrow(() -> new IllegalStateException("Task 未找到"));

      // 2. 使用领域服务计算新的 Slice 状态
      SliceStatus newStatus = SliceStatusCalculator.calculate(task.getStatus());

      // 3. 更新 Slice 聚合根
      PlanSliceAggregate slice = sliceRepository.findById(event.sliceId())
          .orElseThrow(() -> new IllegalStateException("Slice 未找到"));

      SliceStatus oldStatus = slice.getStatus();

      // ✅ 幂等性检查
      if (oldStatus == newStatus) {
        log.debug("Slice 状态未变化,跳过更新");
        return;
      }

      slice.updateStatus(newStatus);
      sliceRepository.save(slice);

      log.info("Slice 状态已更新 sliceId={} {} -> {}",
          event.sliceId(), oldStatus, newStatus);

      // 4. 发布 SliceStatusChangedEvent 给下一个处理器
      SliceStatusChangedEvent sliceEvent = SliceStatusChangedEvent.of(
          event.sliceId(), event.planId(), oldStatus.getCode(), newStatus.getCode());
      eventPublisher.publishEvent(sliceEvent);

    } catch (OptimisticLockingFailureException e) {
      // ✅ 乐观锁冲突: 其他线程已更新
      log.warn("乐观锁冲突,跳过 sliceId={}", event.sliceId());
    } catch (Exception e) {
      // ✅ 记录错误,依赖对账任务修复
      log.error("处理 TaskCompletedEvent 失败", e);
    }
  }
}
```

### 核心处理器模式

#### 1. 事务性事件监听器

```java
// ✅ GOOD: AFTER_COMMIT ensures event published only if transaction succeeds
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void handle(TaskCompletedEvent event) {
  // Handler logic
}
```

```java
// ❌ BAD: BEFORE_COMMIT publishes even if transaction rolls back
@EventListener
public void handle(TaskCompletedEvent event) {
  // Event visible even if transaction fails
}
```

**Why AFTER_COMMIT:**
- Event only visible if source transaction commits
- Prevents ghost events from rollback scenarios
- New transaction isolates handler failures

#### 2. Idempotency Checks

```java
// ✅ GOOD: Check if work already done
SliceStatus oldStatus = slice.getStatus();
if (oldStatus == newStatus) {
  log.debug("Status unchanged, skip update");
  return;  // ✅ Idempotent
}
```

#### 3. Optimistic Lock Handling

```java
// ✅ GOOD: Handle concurrent updates gracefully
try {
  slice.updateStatus(newStatus);
  sliceRepository.save(slice);
} catch (OptimisticLockingFailureException e) {
  // ✅ Another thread already updated, skip
  log.warn("Optimistic lock conflict, skip");
}
```

#### 4. Error Handling Strategy

```java
// ✅ GOOD: Log errors, don't crash
try {
  // Handler logic
} catch (Exception e) {
  log.error("Handler failed, rely on reconciliation", e);
  // ✅ Don't throw: let other handlers continue
}
```

---

## Event Chains

### Status Propagation Chain

patra-ingest uses **event chains** to propagate status changes:

```
Task completes
    ↓
TaskCompletedEvent (taskId=1, status=SUCCEEDED)
    ↓
TaskCompletedEventHandler
    ↓ (calculates Slice status)
    ↓
SliceStatusChangedEvent (sliceId=1, PENDING → FINISHED)
    ↓
SliceStatusChangedEventHandler
    ↓ (aggregates all Slices for Plan)
    ↓
PlanAggregate.updateStatus(COMPLETED)
```

### Event Chain Benefits

| Benefit | Description |
|---------|-------------|
| **Loose coupling** | Task doesn't know about Slice or Plan |
| **Single responsibility** | Each handler has one job |
| **Eventually consistent** | Updates propagate asynchronously |
| **Testable** | Mock event publisher to test in isolation |

### Event Chain Example

**Scenario**: PubMed harvest completes

```
1. Task execution completes
   → TaskAggregate.markSucceeded()
   → eventPublisher.publishEvent(TaskCompletedEvent.of(taskId, sliceId, planId, "SUCCEEDED", now))

2. TaskCompletedEventHandler
   → Calculate SliceStatus: SUCCEEDED → FINISHED
   → Update PlanSliceAggregate.status = FINISHED
   → eventPublisher.publishEvent(SliceStatusChangedEvent.of(sliceId, planId, "PENDING", "FINISHED"))

3. SliceStatusChangedEventHandler
   → Query all Slices for Plan
   → Aggregate: if all FINISHED → Plan.COMPLETED
   → Update PlanAggregate.status = COMPLETED
```

---

## Integration with Outbox

### Events to External Systems

For **cross-service communication**, use the **Outbox Pattern** (see [outbox-pattern.md](outbox-pattern.md)).

```java
// ✅ GOOD: Write to Outbox table in same transaction
@Transactional
public void ingestPlan(PlanIngestionCommand command) {
  // 1. Create domain aggregate
  PlanAggregate plan = PlanAggregate.create(...);
  planRepository.save(plan);

  // 2. Write to Outbox (same transaction)
  OutboxMessage message = OutboxMessage.builder()
      .channel("INGEST_PLAN")
      .dedupKey("PLAN_" + plan.getId())
      .aggregateType("PLAN")
      .aggregateId(plan.getId())
      .payloadJson(toJson(plan))
      .build();
  outboxRepository.saveOrUpdate(message);

  // ✅ Both saved atomically
}
```

### Internal vs External Events

| Type | Mechanism | Use Case |
|------|-----------|----------|
| **Internal Events** | Spring `ApplicationEventPublisher` | Aggregate status propagation within service |
| **External Events** | Outbox Pattern + MQ | Cross-service integration, downstream notifications |

**Internal Event Example:**
```java
// ✅ Within patra-ingest service
eventPublisher.publishEvent(TaskCompletedEvent.of(...));
```

**External Event Example:**
```java
// ✅ To downstream services (patra-workflow, analytics)
OutboxMessage msg = OutboxMessage.builder()
    .channel("TASK_COMPLETED")
    .dedupKey("TASK_" + taskId)
    .payloadJson(toJson(taskCompletedData))
    .build();
outboxRepository.saveOrUpdate(msg);
```

---

## Best Practices

### ✅ DO

| Practice | Reason |
|----------|--------|
| **Use `record` for events** | Immutability, concise syntax |
| **Past tense naming** | Events describe what happened |
| **Include all context** | Handlers shouldn't query for more data |
| **Auto-populate timestamps** | Consistent event timing |
| **Use AFTER_COMMIT listeners** | Prevent ghost events |
| **REQUIRES_NEW transactions** | Isolate handler failures |
| **Idempotency checks** | Handle duplicate events |
| **Handle OptimisticLockingFailureException** | Gracefully handle concurrent updates |
| **Log, don't throw** | Let other handlers continue |

### ❌ DON'T

| Anti-pattern | Problem |
|--------------|---------|
| **Mutable event classes** | Events can be modified after publish |
| **Present tense names** | `TaskCompleteEvent` sounds like a command |
| **Query in handlers** | Breaks loose coupling, adds latency |
| **Synchronous handlers** | Blocks source transaction |
| **Throwing exceptions** | Stops event processing chain |
| **Skipping idempotency** | Duplicate processing on retries |
| **Circular event chains** | A → B → A causes infinite loop |

### Event Naming Convention

```java
// ✅ GOOD: Past tense, describes state change
TaskCompletedEvent
SliceStatusChangedEvent
OutboxMessagePublishedEvent
PlanCreatedEvent

// ❌ BAD: Present tense, sounds like command
TaskCompleteEvent
SliceStatusChangeEvent
OutboxMessagePublishEvent
PlanCreateEvent
```

### Handler Error Strategy

```java
// ✅ GOOD: Defensive error handling
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void handle(TaskCompletedEvent event) {
  try {
    // Handler logic
  } catch (OptimisticLockingFailureException e) {
    // ✅ Expected: concurrent update, skip
    log.warn("Optimistic lock conflict, skip taskId={}", event.taskId());
  } catch (Exception e) {
    // ✅ Unexpected: log error, rely on reconciliation
    log.error("Handler failed for taskId={}", event.taskId(), e);
    // DON'T throw: let other handlers continue
  }
}
```

### Testing Events

```java
// ✅ GOOD: Test event publishing
@Test
void should_publish_task_completed_event() {
  // Given
  TaskAggregate task = createTask();
  ArgumentCaptor<TaskCompletedEvent> eventCaptor =
      ArgumentCaptor.forClass(TaskCompletedEvent.class);

  // When
  task.markSucceeded(Instant.now());
  orchestrator.completeTask(task);

  // Then
  verify(eventPublisher).publishEvent(eventCaptor.capture());
  TaskCompletedEvent event = eventCaptor.getValue();
  assertThat(event.taskId()).isEqualTo(task.getId());
  assertThat(event.status()).isEqualTo("SUCCEEDED");
}
```

```java
// ✅ GOOD: Test handler logic
@Test
void should_update_slice_status_on_task_completed() {
  // Given
  TaskCompletedEvent event = TaskCompletedEvent.of(
      taskId, sliceId, planId, "SUCCEEDED", Instant.now());
  when(taskRepository.findBySliceId(sliceId))
      .thenReturn(Optional.of(successfulTask));
  when(sliceRepository.findById(sliceId))
      .thenReturn(Optional.of(pendingSlice));

  // When
  handler.handle(event);

  // Then
  verify(sliceRepository).save(argThat(slice ->
      slice.getStatus() == SliceStatus.FINISHED));
  verify(eventPublisher).publishEvent(any(SliceStatusChangedEvent.class));
}
```

### Avoiding Circular Events

```java
// ❌ BAD: Circular event chain
@TransactionalEventListener
public void handleTaskCompleted(TaskCompletedEvent event) {
  // ...
  eventPublisher.publishEvent(new TaskQueuedEvent(...));  // ❌ Creates new task!
}

@TransactionalEventListener
public void handleTaskQueued(TaskQueuedEvent event) {
  // ...
  eventPublisher.publishEvent(new TaskCompletedEvent(...));  // ❌ Marks completed!
}
// Result: Infinite loop!
```

```java
// ✅ GOOD: Linear event chain
TaskCompletedEvent → SliceStatusChangedEvent → PlanStatusChangedEvent
// Each event flows in one direction
```

---

**Related Files:**
- [outbox-pattern.md](outbox-pattern.md) - External event publishing via Outbox
- [domain-modeling-patterns.md](domain-modeling-patterns.md) - Domain event examples
- [orchestrator-coordinator-patterns.md](orchestrator-coordinator-patterns.md) - Event publishing from orchestrators

---

**📝 Status**: ✅ **COMPLETE** - Comprehensive guide to Event-Driven Architecture from patra-ingest with Spring Events integration.
