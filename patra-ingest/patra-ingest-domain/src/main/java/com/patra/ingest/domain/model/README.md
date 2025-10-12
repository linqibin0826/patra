# Domain Model Package

This package contains the **core domain model** for the ingestion service, following DDD principles.

---

## Package Structure

```
model/
├─ aggregate/               # Aggregate roots (consistency boundaries)
│  ├─ PlanAggregate.java         # Plan blueprint + state machine
│  ├─ TaskAggregate.java         # Task with lease + execution tracking
│  ├─ PlanSliceAggregate.java    # Slice grouping (intermediate)
│  └─ ScheduleInstanceAggregate.java  # Scheduler run tracking
│
├─ vo/                      # Value objects (immutable)
│  ├─ WindowSpec.java            # Window boundary specification (sealed)
│  ├─ Batch.java                 # Batch definition
│  ├─ CursorWatermark.java       # Watermark tracking VO
│  ├─ ExecutionContext.java      # Task execution context
│  ├─ ExecutionTimeline.java     # Start/finish timestamps
│  ├─ LeaseInfo.java             # Lease ownership info
│  ├─ TaskParams.java            # Task parameters
│  ├─ SliceSpec.java             # Slice specification
│  ├─ PlannerWindow.java         # Planning window
│  └─ ...                        # Other VOs
│
├─ entity/                  # Domain entities (not aggregate roots)
│  └─ OutboxMessage.java         # Outbox message entity
│
├─ enums/                   # Domain enumerations
│  ├─ PlanStatus.java            # Plan state machine
│  ├─ TaskStatus.java            # Task state machine
│  ├─ OperationCode.java         # HARVEST/UPDATE/COMPENSATION
│  └─ ...
│
├─ snapshot/                # Configuration snapshots
│  └─ ProvenanceConfigSnapshot.java  # Captured registry config
│
└─ read/                    # (future: read models for CQRS)
```

---

## Key Concepts

### Aggregates

**Aggregate Root** = Transaction boundary + Consistency boundary

| Aggregate | Purpose | State Machine |
|-----------|---------|---------------|
| **PlanAggregate** | Blueprint for collection job | DRAFT → SLICING → READY → COMPLETED/FAILED |
| **TaskAggregate** | Atomic work unit | QUEUED → RUNNING → SUCCEEDED/FAILED |
| **PlanSliceAggregate** | Grouping of tasks within a plan | PENDING → PROCESSING → COMPLETED |
| **ScheduleInstanceAggregate** | Scheduler run tracking | STARTED → COMPLETED/FAILED |

**Rules**:
- Only aggregates can have mutable state
- State changes via behavior methods (no public setters)
- Aggregates raise domain events on state transitions
- No direct references between aggregates (use IDs)

### Value Objects

**Value Object** = Immutable, equality by value (not identity)

**Common patterns**:
```java
// Sealed interface for polymorphic VOs
public sealed interface WindowSpec {
    record Time(Instant from, Instant to) implements WindowSpec {}
    record IdRange(Long minId, Long maxId) implements WindowSpec {}
    // ...
}

// Record for simple VOs
public record Batch(
    String batchId,
    List<String> itemIds
) { }

// Class for VOs with business logic
public class CursorWatermark {
    private final Instant watermark;

    public boolean isAfter(Instant other) { ... }
}
```

### Enumerations

**Domain Enum** = Type-safe constants with business logic

**Pattern**:
```java
public enum TaskStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED;
    }
}
```

---

## Design Patterns

### 1. Aggregate Factory Methods

**Problem**: Aggregates have complex construction logic (validation, defaults).

**Solution**: Private constructor + static factory methods.

```java
public class PlanAggregate extends AggregateRoot<Long> {

    // Private constructor
    private PlanAggregate(...) { }

    // Factory for new instances
    public static PlanAggregate create(...) {
        // Validation + defaults
        return new PlanAggregate(...);
    }

    // Factory for restoring from persistence
    public static PlanAggregate restore(Long id, ..., long version) {
        PlanAggregate aggregate = new PlanAggregate(id, ...);
        aggregate.assignVersion(version);
        return aggregate;
    }
}
```

### 2. Sealed WindowSpec

**Problem**: Window strategies are fixed and known (not extensible at runtime).

**Solution**: Sealed interface with record implementations.

```java
public sealed interface WindowSpec {
    record Time(Instant from, Instant to) implements WindowSpec {}
    record IdRange(Long minId, Long maxId) implements WindowSpec {}
    record CursorLandmark(String cursorValue) implements WindowSpec {}
    record VolumeBudget(Integer maxRecords) implements WindowSpec {}
    record Single() implements WindowSpec {}
}

// Usage with pattern matching
if (windowSpec instanceof WindowSpec.Time timeSpec) {
    Instant from = timeSpec.from();
    Instant to = timeSpec.to();
}
```

**Benefits**:
- Type-safe exhaustiveness checking
- Clear domain vocabulary
- Easy pattern matching

### 3. Domain Events

**Problem**: Aggregates need to communicate state changes without tight coupling.

**Solution**: Domain events raised via `addDomainEvent()`, pulled by app layer.

```java
public class TaskAggregate extends AggregateRoot<Long> {

    public void markRunning(Instant startedAt) {
        this.status = TaskStatus.RUNNING;
        this.executionTimeline = executionTimeline.onStart(startedAt);

        // Raise event
        addDomainEvent(new TaskStartedEvent(getId(), startedAt));
    }
}

// App layer pulls and publishes
List<DomainEvent> events = task.pullDomainEvents();
eventPublisher.publish(events);
```

### 4. Snapshot Pattern

**Problem**: Configuration from `patra-registry` may change during task execution.

**Solution**: Capture config snapshot at plan creation time.

```java
public class PlanAggregate {
    private final String provenanceConfigSnapshotJson;  // Immutable
    private final String provenanceConfigHash;          // For change detection

    // Config never changes for this plan
}
```

---

## State Machines

### PlanAggregate States

```
[DRAFT] ──startSlicing()──> [SLICING] ──markReady()──> [READY]
                                             │
                                             ├──> [PARTIAL] (some tasks failed)
                                             │
                                             └──> [COMPLETED] (all tasks succeeded)
                                                        │
                                                        └──> [FAILED] (terminal failure)
```

### TaskAggregate States

```
[QUEUED] ──markRunning()──> [RUNNING] ──markSucceeded()──> [SUCCEEDED]
                                │
                                ├──markFailed()──> [FAILED] ──prepareForRetry()──> [QUEUED]
                                │
                                ├──markPartial()──> [PARTIAL]
                                │
                                └──markCancelled()──> [CANCELLED]
```

---

## Validation Strategy

### Constructor Validation

**Canonical constructor** validates invariants:
```java
public record Provenance(String code, String name, ...) {
    public Provenance {
        Objects.requireNonNull(code, "code required");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code cannot be blank");
        }
    }
}
```

### Aggregate Behavior Validation

**Behavior methods** enforce state machine rules:
```java
public void startSlicing() {
    if (this.status != PlanStatus.DRAFT) {
        throw new IllegalStateException("Can only start slicing from DRAFT state");
    }
    this.status = PlanStatus.SLICING;
}
```

---

## Testing Guidelines

### Unit Tests (Aggregates)

```java
@Test
void testPlanStateTransition() {
    // Given
    PlanAggregate plan = PlanAggregate.create(...);

    // When
    plan.startSlicing();

    // Then
    assertEquals(PlanStatus.SLICING, plan.getStatus());
}
```

### Unit Tests (Value Objects)

```java
@Test
void testWindowSpecPatternMatching() {
    // Given
    WindowSpec window = new WindowSpec.Time(from, to);

    // When/Then
    assertTrue(window instanceof WindowSpec.Time);
    assertEquals(from, ((WindowSpec.Time) window).from());
}
```

---

## Common Pitfalls

### ❌ DON'T: Expose mutable state

```java
// BAD
public class PlanAggregate {
    private PlanStatus status;

    public void setStatus(PlanStatus status) {  // ❌
        this.status = status;
    }
}
```

### ✅ DO: Encapsulate with behavior methods

```java
// GOOD
public class PlanAggregate {
    private PlanStatus status;

    public void startSlicing() {  // ✅
        // Validates state machine
        if (this.status != PlanStatus.DRAFT) {
            throw new IllegalStateException("Invalid state");
        }
        this.status = PlanStatus.SLICING;
    }
}
```

### ❌ DON'T: Reference other aggregates directly

```java
// BAD
public class TaskAggregate {
    private PlanAggregate plan;  // ❌ Direct reference
}
```

### ✅ DO: Use aggregate IDs

```java
// GOOD
public class TaskAggregate {
    private Long planId;  // ✅ Reference by ID
}
```

---

**See Also**:
- [Main patra-ingest README](../../../../../README.md)
- [Architecture Guide](../../../../../../../docs/ARCHITECTURE.md)
- [Development Guide](../../../../../../../docs/DEV-GUIDE.md)
