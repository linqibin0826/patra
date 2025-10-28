# Domain Base Classes (patra-common)

This package provides **framework-agnostic base classes** for implementing DDD aggregates and events across all Papertrace microservices.

---

## Package Contents

```
domain/
├─ AggregateRoot.java        # Base class for aggregate roots
├─ ReadOnlyAggregate.java    # Base for read-only aggregates (CQRS read side)
└─ DomainEvent.java          # Interface for domain events
```

---

## Core Classes

### 1. AggregateRoot<ID>

**Purpose**: Abstract base class for all aggregate roots in the domain layer.

**Key Features**:
- **Identity management**: Assigns and tracks aggregate ID
- **Optimistic locking**: Version field for concurrency control
- **Domain events**: Collects events raised during state changes
- **Pure Java**: NO framework dependencies (Serializable only)

**Source**: [`AggregateRoot.java`](AggregateRoot.java:1)

#### Usage Example

```java
public class PlanAggregate extends AggregateRoot<Long> {

    private final String planKey;
    private PlanStatus status;

    // Private constructor
    private PlanAggregate(Long id, String planKey, PlanStatus status) {
        super(id);  // Set ID
        this.planKey = planKey;
        this.status = status;
    }

    // Factory for new aggregates
    public static PlanAggregate create(String planKey) {
        return new PlanAggregate(null, planKey, PlanStatus.DRAFT);
    }

    // Factory for restoring from persistence
    public static PlanAggregate restore(Long id, String planKey, PlanStatus status, long version) {
        PlanAggregate aggregate = new PlanAggregate(id, planKey, status);
        aggregate.assignVersion(version);  // Set version for optimistic locking
        return aggregate;
    }

    // Domain behavior
    public void startSlicing() {
        if (this.status != PlanStatus.DRAFT) {
            throw new IllegalStateException("Can only slice from DRAFT state");
        }
        this.status = PlanStatus.SLICING;

        // Raise domain event
        addDomainEvent(new PlanSlicingStartedEvent(getId(), Instant.now()));
    }
}
```

#### Key Methods

| Method | Purpose | When to Call |
|--------|---------|--------------|
| `assignId(ID id)` | Assign ID after persistence | Repository after INSERT |
| `assignVersion(long version)` | Set optimistic lock version | Repository after SELECT/UPDATE |
| `isTransient()` | Check if not yet persisted | Before saving |
| `addDomainEvent(DomainEvent event)` | Register domain event | In domain behaviors |
| `pullDomainEvents()` | Drain events for publishing | App layer after persistence |
| `peekDomainEvents()` | View events (debug/test) | Unit tests |

#### Identity Management

**Before persistence**:
```java
PlanAggregate plan = PlanAggregate.create("plan-123");
assertTrue(plan.isTransient());  // ID is null
assertNull(plan.getId());
```

**After persistence**:
```java
PlanAggregate saved = repository.save(plan);
assertFalse(saved.isTransient());  // ID assigned
assertNotNull(saved.getId());
```

#### Optimistic Locking

**Version field** prevents lost updates:

```java
// Thread A reads plan (version=1)
PlanAggregate planA = repository.findById(123L);

// Thread B reads same plan (version=1)
PlanAggregate planB = repository.findById(123L);

// Thread A updates (version becomes 2)
planA.startSlicing();
repository.save(planA);  // Success (version 1 → 2)

// Thread B updates (stale version=1)
planB.markReady();
repository.save(planB);  // ❌ OptimisticLockException (expected version 2, found 1)
```

**MyBatis-Plus** handles this via `@Version` annotation:
```java
@Data
@TableName("ingest_plan")
public class IngestPlanDO {
    @TableId
    private Long id;

    @Version  // Auto-incremented on UPDATE
    private Long version;
}
```

#### Domain Events

**Lifecycle**:
1. **Raise**: Aggregate calls `addDomainEvent()` during behavior
2. **Collect**: App layer calls `pullDomainEvents()` after persistence
3. **Publish**: App layer publishes to outbox/message bus
4. **Clear**: `pullDomainEvents()` clears internal event list

**Example Flow**:
```java
// 1. Aggregate raises event
public void startSlicing() {
    this.status = PlanStatus.SLICING;
    addDomainEvent(new PlanSlicingStartedEvent(getId(), Instant.now()));
}

// 2. App layer collects and publishes
@Transactional
public void executeUseCase() {
    PlanAggregate plan = repository.findById(123L);
    plan.startSlicing();

    repository.save(plan);  // Persist state change

    List<DomainEvent> events = plan.pullDomainEvents();  // Drain events
    eventPublisher.publish(events);  // Publish to outbox
}
```

**Why not publish directly from aggregate?**
- Aggregates are **pure domain** (no infrastructure concerns)
- Publishing requires transaction coordination (app layer responsibility)
- Testability (mock publisher in app layer, not domain)

---

### 2. ReadOnlyAggregate<ID>

**Purpose**: Base for read-only aggregates (CQRS read side).

**Differences from `AggregateRoot`**:
- NO domain events (read-only)
- NO state transitions (immutable after creation)
- Used for query models optimized for reads

**Usage**:
```java
public class ProvenanceConfiguration extends ReadOnlyAggregate<Long> {

    private final Provenance provenance;
    private final HttpConfig httpConfig;
    // ... other configs

    // No setters, no state changes
    public boolean isComplete() {
        return provenance != null && provenance.isActive();
    }
}
```

**Source**: [`ReadOnlyAggregate.java`](ReadOnlyAggregate.java)

---

### 3. DomainEvent

**Purpose**: Marker interface for domain events.

**Contract**:
```java
public interface DomainEvent extends Serializable {

    /**
     * Returns the event type identifier (e.g., "ingest.task.queued").
     * Used for routing and deserialization.
     */
    String eventType();
}
```

**Implementation Pattern** (record):
```java
public record TaskQueuedEvent(
    Long taskId,
    Long planId,
    String provenanceCode,
    Instant queuedAt
) implements DomainEvent {

    @Override
    public String eventType() {
        return "ingest.task.queued";
    }

    // Factory method
    public static TaskQueuedEvent of(Long taskId, Long planId, String provenanceCode, Instant queuedAt) {
        return new TaskQueuedEvent(taskId, planId, provenanceCode,
            queuedAt != null ? queuedAt : Instant.now());
    }
}
```

**Source**: [`DomainEvent.java`](DomainEvent.java)

---

## Design Patterns

### 1. Factory Methods over Constructors

**Problem**: Aggregate construction requires validation, defaults, business rules.

**Solution**: Private constructor + static factory methods.

```java
public class TaskAggregate extends AggregateRoot<Long> {

    // Private constructor (force use of factories)
    private TaskAggregate(...) { }

    // Factory for new instances
    public static TaskAggregate create(...) {
        // Validation + defaults
        return new TaskAggregate(null, ...);
    }

    // Factory for restoring from DB
    public static TaskAggregate restore(Long id, ..., long version) {
        TaskAggregate aggregate = new TaskAggregate(id, ...);
        aggregate.assignVersion(version);
        return aggregate;
    }
}
```

**Benefits**:
- Clear intent (`create` vs. `restore`)
- Encapsulates validation
- Prevents invalid construction

### 2. Tell, Don't Ask

**Problem**: Exposing internal state leads to business logic leaking into app layer.

**Anti-pattern**:
```java
// BAD: App layer checks state and mutates aggregate
if (plan.getStatus() == PlanStatus.DRAFT) {
    plan.setStatus(PlanStatus.SLICING);  // ❌ Exposes internal state
}
```

**Pattern**:
```java
// GOOD: Aggregate encapsulates state transition
plan.startSlicing();  // ✅ Validates state machine internally
```

**Rule**: Aggregates should **expose behavior**, not state.

### 3. Event Sourcing Lite

**Problem**: Need audit trail of state changes without full event sourcing.

**Solution**: Raise domain events for important state transitions.

```java
public void markRunning(Instant startedAt) {
    this.status = TaskStatus.RUNNING;
    this.executionTimeline = executionTimeline.onStart(startedAt);

    // Event for observability/audit
    addDomainEvent(new TaskStartedEvent(getId(), startedAt));
}
```

**Benefits**:
- Audit trail (who did what when)
- Integration with other services (async notifications)
- Debugging (event log shows state transitions)

---

## Testing Guidelines

### Unit Tests (Aggregate Behavior)

```java
@Test
void testAggregateStateTransition() {
    // Given
    PlanAggregate plan = PlanAggregate.create("plan-123");

    // When
    plan.startSlicing();

    // Then
    assertEquals(PlanStatus.SLICING, plan.getStatus());

    // Verify event
    List<DomainEvent> events = plan.peekDomainEvents();
    assertEquals(1, events.size());
    assertTrue(events.get(0) instanceof PlanSlicingStartedEvent);
}
```

### Unit Tests (Event Pulling)

```java
@Test
void testEventPulling() {
    // Given
    TaskAggregate task = TaskAggregate.create(...);
    task.markRunning(Instant.now());

    // When
    List<DomainEvent> events = task.pullDomainEvents();

    // Then
    assertEquals(1, events.size());

    // Second pull returns empty (events cleared)
    List<DomainEvent> events2 = task.pullDomainEvents();
    assertTrue(events2.isEmpty());
}
```

### Integration Tests (Optimistic Locking)

```java
@Test
void testOptimisticLockingConflict() {
    // Given: Persist initial aggregate
    PlanAggregate plan = PlanAggregate.create("plan-123");
    plan = repository.save(plan);
    Long planId = plan.getId();

    // When: Two threads load same aggregate
    PlanAggregate planA = repository.findById(planId).get();
    PlanAggregate planB = repository.findById(planId).get();

    // Thread A updates
    planA.startSlicing();
    repository.save(planA);  // Success

    // Thread B updates (stale version)
    planB.markReady();

    // Then: OptimisticLockException
    assertThrows(OptimisticLockException.class, () ->
        repository.save(planB)
    );
}
```

---

## Common Pitfalls

### ❌ DON'T: Bypass aggregate encapsulation

```java
// BAD: App layer directly sets aggregate fields
plan.setStatus(PlanStatus.SLICING);  // ❌ Violates encapsulation
```

### ✅ DO: Use behavior methods

```java
// GOOD: Aggregate validates state transition
plan.startSlicing();  // ✅ Encapsulated behavior
```

### ❌ DON'T: Share aggregates across threads

```java
// BAD: Aggregates are not thread-safe
PlanAggregate plan = repository.findById(123L);
executor.submit(() -> plan.startSlicing());  // ❌ Race condition
executor.submit(() -> plan.markReady());     // ❌ Race condition
```

### ✅ DO: One aggregate per thread/transaction

```java
// GOOD: Each thread loads fresh copy
executor.submit(() -> {
    PlanAggregate plan = repository.findById(123L);
    plan.startSlicing();
    repository.save(plan);
});
```

### ❌ DON'T: Publish events before persisting aggregate

```java
// BAD: Event published but aggregate save fails
plan.startSlicing();
eventPublisher.publish(plan.pullDomainEvents());  // ❌ Out of order
repository.save(plan);  // May fail, but event already sent
```

### ✅ DO: Persist then publish (in same transaction)

```java
// GOOD: Events published only if save succeeds
plan.startSlicing();
repository.save(plan);  // Commit state change
eventPublisher.publish(plan.pullDomainEvents());  // Then publish
```

---

## Framework Independence

**Key Principle**: Domain layer has **ZERO framework dependencies**.

**Allowed**:
- ✅ Java SE classes (`java.time.*`, `java.util.*`)
- ✅ `Serializable` (for remoting/caching)
- ✅ `patra-common` (shared domain utilities)

**Forbidden**:
- ❌ Spring annotations (`@Component`, `@Transactional`, `@Autowired`)
- ❌ MyBatis annotations (`@TableName`, `@TableId`)
- ❌ Jackson annotations (`@JsonProperty`, `@JsonIgnore`)
- ❌ Lombok (except `@Getter` on private fields, acceptable)

**Why?**
- **Testability**: Domain logic testable without Spring context
- **Portability**: Easy to migrate frameworks (Spring → Quarkus)
- **Focus**: Business logic isolated from infrastructure concerns

---

**See Also**:
- [Architecture Guide](../../../../../docs/ARCHITECTURE.md)
- [Development Guide](../../../../../docs/DEV-GUIDE.md)
- [patra-ingest Domain Model README](../../../../../../../patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/README.md)
