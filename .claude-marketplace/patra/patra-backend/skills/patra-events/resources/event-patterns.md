# 领域事件模式详解

## 事件定义规范

```java
/// 使用 record 定义不可变事件
public record TaskCompletedEvent(
    Long taskId,
    Long sliceId,
    Long planId,
    String status,
    String errorCode,        // 失败时的错误码
    String errorMessage,     // 失败时的错误消息
    Instant finishedAt,
    Instant occurredAt
) implements DomainEvent {

    public TaskCompletedEvent {
        occurredAt = occurredAt == null ? Instant.now() : occurredAt;
    }

    /// 成功完成
    public static TaskCompletedEvent of(
            Long taskId, Long sliceId, Long planId, String status, Instant finishedAt) {
        return new TaskCompletedEvent(
            taskId, sliceId, planId, status, null, null, finishedAt, Instant.now());
    }

    /// 失败完成
    public static TaskCompletedEvent ofFailure(
            Long taskId, Long sliceId, Long planId, String status,
            String errorCode, String errorMessage, Instant finishedAt) {
        return new TaskCompletedEvent(
            taskId, sliceId, planId, status, errorCode, errorMessage, finishedAt, Instant.now());
    }
}
```

**位置**：`domain/event/`

## 事件处理器模式

### 标准模式：AFTER_COMMIT + REQUIRES_NEW

```java
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

            // 触发下一个事件（链式）
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

### 错误处理策略

| 错误类型 | 处理方式 |
|---------|---------|
| `OptimisticLockingFailureException` | 记录警告，跳过（其他线程已处理） |
| `IllegalStateException` | 记录错误，依赖对账修复 |
| 未知异常 | 记录错误，**不抛出**（让其他处理器继续） |

### 事件链设计原则

```
线性事件链（正确）：
TaskCompletedEvent → SliceStatusChangedEvent → PlanStatusChangedEvent

循环事件链（禁止）：
A → B → A（无限循环）
```

## 项目中的事件清单

| 事件 | 触发时机 | 目的 |
|------|---------|------|
| **TaskQueuedEvent** | Task 创建并持久化 | 度量指标、审计追踪 |
| **TaskCompletedEvent** | Task 到达终态 | 触发 Slice 状态更新 |
| **SliceStatusChangedEvent** | Slice 状态变化 | 触发 Plan 状态聚合 |
| **OutboxMessagePublishedEvent** | 消息发布到 MQ | 审计、度量指标 |

## 测试要点

- 验证事件发布（使用 `@RecordApplicationEvents`）
- 验证处理器逻辑（直接调用 handler.handle()）
- 验证幂等性（重复发送同一事件）
- 验证并发场景（乐观锁冲突）
