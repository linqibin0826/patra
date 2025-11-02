# Event-Driven Architecture

**Purpose**: Domain events enable loose coupling and reactive workflows across aggregates and bounded contexts.

---

## Table of Contents

1. [Overview](#overview)
2. [Domain Events](#domain-events)
3. [Event Handlers](#event-handlers)
4. [Event Chains](#event-chains)
5. [Integration with Outbox](#integration-with-outbox)
6. [Best Practices](#best-practices)

---

## Overview

### What is Event-Driven Architecture?

**Event-Driven Architecture (EDA)** uses domain events to communicate state changes between components. Events enable:
- ✅ **Loose coupling**: Aggregates don't know their consumers
- ✅ **Reactive workflows**: Automatic status propagation
- ✅ **Audit trail**: Complete event history
- ✅ **Eventually consistent updates**: Async processing

### Papertrace Event Flow

```
┌─────────────────────────────────────────────────────────────┐
│              Task Completion Event Chain                   │
├─────────────────────────────────────────────────────────────┤
│  1. Task completes execution                               │
│     → Emit TaskCompletedEvent                              │
│                                                             │
│  2. TaskCompletedEventHandler                              │
│     → Calculate Slice status (1:1 mapping)                 │
│     → Update Slice aggregate                               │
│     → Emit SliceStatusChangedEvent                         │
│                                                             │
│  3. SliceStatusChangedEventHandler                         │
│     → Aggregate all Slice statuses for Plan                │
│     → Update Plan aggregate                                │
│     → Emit PlanStatusChangedEvent (optional)               │
└─────────────────────────────────────────────────────────────┘
```

---

## Domain Events

### Event Design Pattern

Domain events in patra-ingest follow these conventions:

**✅ Event Best Practices:**
- Use Java `record` for immutability
- Past tense naming (e.g., `TaskCompletedEvent`, not `TaskCompleteEvent`)
- Include all relevant context (IDs, status, timestamps)
- Auto-populate `occurredAt` if not provided
- Provide factory methods for common scenarios

### Complete Event Example

**File**: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/event/TaskCompletedEvent.java`

```java
package com.patra.ingest.domain.event;

import com.patra.common.domain.DomainEvent;
import java.time.Instant;

/**
 * Domain event emitted when a task completes execution.
 *
 * <p>Event Chain: TaskCompletedEvent → SliceStatusChangedEvent → PlanAggregate update
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

  // ✅ Auto-populate timestamp in compact constructor
  public TaskCompletedEvent {
    occurredAt = occurredAt == null ? Instant.now() : occurredAt;
  }

  // ✅ Factory method for successful completion
  public static TaskCompletedEvent of(
      Long taskId, Long sliceId, Long planId, String status, Instant finishedAt) {
    return new TaskCompletedEvent(
        taskId, sliceId, planId, status, null, null, finishedAt, Instant.now());
  }

  // ✅ Factory method for failed completion
  public static TaskCompletedEvent ofFailure(
      Long taskId, Long sliceId, Long planId, String status,
      String errorCode, String errorMessage, Instant finishedAt) {
    return new TaskCompletedEvent(
        taskId, sliceId, planId, status, errorCode, errorMessage, finishedAt, Instant.now());
  }
}
```

### Event Examples from patra-ingest

| Event | Trigger | Purpose |
|-------|---------|---------|
| **TaskQueuedEvent** | Task created and persisted | Metrics, audit trail |
| **TaskCompletedEvent** | Task reaches terminal state | Trigger Slice status update |
| **SliceStatusChangedEvent** | Slice status changes | Trigger Plan status aggregation |
| **OutboxMessagePublishedEvent** | Message published to MQ | Audit, metrics |
| **OutboxMessageDeferredEvent** | Message retry scheduled | Monitor retry rate |

---

## Event Handlers

### Event Handler Pattern

**File**: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/eventhandler/TaskCompletedEventHandler.java`

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class TaskCompletedEventHandler {

  private final TaskRepository taskRepository;
  private final PlanSliceRepository sliceRepository;
  private final ApplicationEventPublisher eventPublisher;

  /**
   * Handles TaskCompletedEvent after the transaction commits.
   *
   * ✅ @TransactionalEventListener(phase = AFTER_COMMIT)
   * ✅ @Transactional(propagation = REQUIRES_NEW)
   */
  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(TaskCompletedEvent event) {
    try {
      log.debug("Handling TaskCompletedEvent taskId={} sliceId={}",
          event.taskId(), event.sliceId());

      // 1. Query associated Task (1:1 relationship with Slice)
      TaskAggregate task = taskRepository.findBySliceId(event.sliceId())
          .orElseThrow(() -> new IllegalStateException("Task not found"));

      // 2. Calculate new Slice status using Domain Service
      SliceStatus newStatus = SliceStatusCalculator.calculate(task.getStatus());

      // 3. Update Slice aggregate
      PlanSliceAggregate slice = sliceRepository.findById(event.sliceId())
          .orElseThrow(() -> new IllegalStateException("Slice not found"));

      SliceStatus oldStatus = slice.getStatus();

      // ✅ Idempotency check
      if (oldStatus == newStatus) {
        log.debug("Slice status unchanged, skip update");
        return;
      }

      slice.updateStatus(newStatus);
      sliceRepository.save(slice);

      log.info("Slice status updated sliceId={} {} -> {}",
          event.sliceId(), oldStatus, newStatus);

      // 4. Publish SliceStatusChangedEvent for next handler
      SliceStatusChangedEvent sliceEvent = SliceStatusChangedEvent.of(
          event.sliceId(), event.planId(), oldStatus.getCode(), newStatus.getCode());
      eventPublisher.publishEvent(sliceEvent);

    } catch (OptimisticLockingFailureException e) {
      // ✅ Optimistic lock conflict: another thread already updated
      log.warn("Optimistic lock conflict, skip sliceId={}", event.sliceId());
    } catch (Exception e) {
      // ✅ Log error, rely on reconciliation task to fix
      log.error("Failed to handle TaskCompletedEvent", e);
    }
  }
}
```

### Key Handler Patterns

#### 1. Transactional Event Listener

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
