# 领域建模模式

**目的**: 领域层中用于纯业务逻辑建模的 DDD 战术模式。

---

## 目录

- [Domain Layer Overview](#domain-layer-overview)
- [Aggregates](#aggregates)
- [Entities](#entities)
- [Value Objects](#value-objects)
- [Domain Events](#domain-events)
- [Domain Enums](#domain-enums)
- [Ports (Interfaces)](#ports-interfaces)
- [Factories](#factories)
- [Domain Services](#domain-services)
- [Key Principles](#key-principles)
- [Testing Domain Models](#testing-domain-models)

---

## 领域层 Overview

The Domain layer is the **heart of your application** and contains the pure business logic. It must remain **framework-independent** and rely only on Pure Java + Lombok.

### 领域层 Responsibilities

1. **Encapsulate business rules** in domain objects (Aggregates, Entities, Value Objects)
2. **Define domain contracts** through ports (interfaces)
3. **Emit domain events** for cross-aggregate communication
4. **Enforce invariants** through validation in constructors and methods
5. **Expose factory methods** for complex object creation

### 领域层 Structure

```
patra-ingest-domain/
├── model/
│   ├── aggregate/       # Aggregate roots (PlanAggregate, etc.)
│   ├── entity/          # Entities (TaskRun, OutboxMessage, etc.)
│   ├── vo/              # Value objects (WindowSpec, BatchPlan, etc.)
│   └── enums/           # Domain enums (OperationCode, PlanStatus, etc.)
├── event/               # Domain events (TaskCompletedEvent, etc.)
├── port/                # Repository & service ports (interfaces)
├── factory/             # Domain object factories
└── service/             # Domain services (stateless logic)
```

---

## Aggregates

### What is an Aggregate?

An **Aggregate** is a cluster of domain objects (entities and value objects) treated as a single unit for data changes. The **Aggregate Root** is the only object that external code can hold references to.

### Aggregate Design Principles

1. **Consistency Boundary**: All invariants within the aggregate must be consistent after each operation
2. **Transactional Boundary**: Save/update the entire aggregate in a single transaction
3. **External References**: Other aggregates can only reference the aggregate by its ID
4. **Small Aggregates**: Keep aggregates as small as possible (prefer composition over large trees)

### Complete Aggregate Example: PlanAggregate

**File**: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/aggregate/PlanAggregate.java`

```java
package com.patra.ingest.domain.model.aggregate;

import com.patra.common.domain.AggregateRoot;
import com.patra.ingest.domain.model.enums.OperationCode;
import com.patra.ingest.domain.model.enums.PlanStatus;
import com.patra.ingest.domain.model.vo.plan.WindowSpec;
import lombok.Getter;
import java.util.Objects;

/**
 * Aggregate root representing the blueprint of a single ingestion plan.
 *
 * <p>Idempotency: the {@code planKey} (source + operation + window + strategy hash)
 * is managed by repositories to prevent duplicate plans.
 *
 * <p>State machine: {@code DRAFT → SLICING → READY/PARTIAL → COMPLETED/FAILED}.
 */
@Getter
public class PlanAggregate extends AggregateRoot<Long> {

  // ========== Immutable Fields (Business Keys) ==========
  private final Long scheduleInstanceId;      // External trigger reference
  private final String planKey;                // Business idempotency key
  private final String provenanceCode;         // Source (e.g., "PUBMED")
  private final OperationCode operationCode;   // Operation type enum
  private final String exprProtoHash;          // Expression prototype hash
  private final String exprProtoSnapshotJson;  // Expression snapshot (JSON)
  private final String provenanceConfigSnapshotJson;
  private final String provenanceConfigHash;
  private final WindowSpec windowSpec;         // Window boundary specification
  private final String sliceStrategyCode;
  private final String sliceParamsJson;

  // ========== Mutable State ==========
  private PlanStatus status;  // Current state: DRAFT, SLICING, READY, COMPLETED, FAILED

  private PlanAggregate(
      Long id,
      Long scheduleInstanceId,
      String planKey,
      String provenanceCode,
      OperationCode operationCode,
      String exprProtoHash,
      String exprProtoSnapshotJson,
      String provenanceConfigSnapshotJson,
      String provenanceConfigHash,
      WindowSpec windowSpec,
      String sliceStrategyCode,
      String sliceParamsJson,
      PlanStatus status) {
    super(id);
    // ✅ Enforce invariants in constructor
    this.scheduleInstanceId = Objects.requireNonNull(scheduleInstanceId, "scheduleInstanceId must not be null");
    this.planKey = Objects.requireNonNull(planKey, "planKey must not be null");
    this.windowSpec = Objects.requireNonNull(windowSpec, "windowSpec must not be null");
    this.provenanceCode = provenanceCode;
    this.operationCode = operationCode;
    this.exprProtoHash = exprProtoHash;
    this.exprProtoSnapshotJson = exprProtoSnapshotJson;
    this.provenanceConfigSnapshotJson = provenanceConfigSnapshotJson;
    this.provenanceConfigHash = provenanceConfigHash;
    this.sliceStrategyCode = sliceStrategyCode;
    this.sliceParamsJson = sliceParamsJson;
    this.status = status == null ? PlanStatus.DRAFT : status;
  }

  // ========== Factory Method: Create New Aggregate ==========
  public static PlanAggregate create(
      Long scheduleInstanceId,
      String planKey,
      String provenanceCode,
      String operationCode,
      String exprProtoHash,
      String exprProtoSnapshotJson,
      String provenanceConfigSnapshotJson,
      String provenanceConfigHash,
      WindowSpec windowSpec,
      String sliceStrategyCode,
      String sliceParamsJson) {
    // Parse domain enum to normalize case and whitespace
    OperationCode op = operationCode == null ? null : OperationCode.fromCode(operationCode);
    return new PlanAggregate(
        null,  // ID is null for new aggregates
        scheduleInstanceId,
        planKey,
        provenanceCode,
        op,
        exprProtoHash,
        exprProtoSnapshotJson,
        provenanceConfigSnapshotJson,
        provenanceConfigHash,
        windowSpec,
        sliceStrategyCode,
        sliceParamsJson,
        PlanStatus.DRAFT);  // ✅ New aggregates start in DRAFT state
  }

  // ========== Factory Method: Restore from Persistence ==========
  public static PlanAggregate restore(
      Long id,
      Long scheduleInstanceId,
      String planKey,
      String provenanceCode,
      String operationCode,
      String exprProtoHash,
      String exprProtoSnapshotJson,
      String provenanceConfigSnapshotJson,
      String provenanceConfigHash,
      WindowSpec windowSpec,
      String sliceStrategyCode,
      String sliceParamsJson,
      PlanStatus status,
      long version) {
    OperationCode op = operationCode == null ? null : OperationCode.fromCode(operationCode);
    PlanAggregate aggregate = new PlanAggregate(
        id,  // ✅ ID is populated when restoring from DB
        scheduleInstanceId,
        planKey,
        provenanceCode,
        op,
        exprProtoHash,
        exprProtoSnapshotJson,
        provenanceConfigSnapshotJson,
        provenanceConfigHash,
        windowSpec,
        sliceStrategyCode,
        sliceParamsJson,
        status);
    aggregate.assignVersion(version);  // ✅ For optimistic locking
    return aggregate;
  }

  // ========== Business Methods (State Transitions) ==========

  /**
   * Transitions the plan from DRAFT to SLICING status.
   *
   * @throws IllegalStateException if plan is not in DRAFT status
   */
  public void startSlicing() {
    // ✅ Enforce state machine rules
    if (this.status != PlanStatus.DRAFT) {
      throw new IllegalStateException("Invalid plan status; slicing cannot start.");
    }
    this.status = PlanStatus.SLICING;
  }

  // ✅ Additional state transition methods: markReady(), updateStatus(), etc.
  // (Omitted for brevity - all follow similar pattern with validation and state updates)
}
```

### Key Aggregate Patterns

| Pattern | Example | Purpose |
|---------|---------|---------|
| **Private constructor** | `private PlanAggregate(...)` | Force use of factory methods |
| **Factory methods** | `create()`, `restore()` | Clear intent (new vs reconstructed) |
| **Immutable fields** | `final String planKey` | Business keys never change |
| **State machine** | `startSlicing()`, `markReady()` | Enforce valid state transitions |
| **Invariant validation** | `Objects.requireNonNull(planKey, ...)` | Guarantee consistency |

---

## Entities

### What is an Entity?

An **Entity** is an object with a unique identity that persists over time. Two entities are equal if they have the same ID, regardless of their other attributes.

### Entity vs Aggregate Root

- **Aggregate Root**: Top-level entity that defines consistency boundary
- **Entity**: Child object within an aggregate or standalone entity with simpler lifecycle

### Complete Entity Example: TaskRun

**File**: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/entity/TaskRun.java`

```java
package com.patra.ingest.domain.model.entity;

import com.patra.ingest.domain.model.enums.TaskRunStatus;
import com.patra.ingest.domain.model.vo.execution.*;
import com.patra.ingest.domain.model.vo.plan.WindowSpec;
import lombok.Getter;
import java.time.Instant;

/**
 * Entity representing a single task run attempt.
 *
 * <p>Identity: {@code id} (database-generated primary key)
 * <p>Lifecycle: Created by Task aggregate, updated by execution engine
 */
@Getter
public class TaskRun {
  // ========== Identity ==========
  private final Long id;
  private final Long taskId;  // Foreign key to parent Task
  private final int attemptNo;

  // ========== Immutable Context ==========
  private final String provenanceCode;
  private final String operationCode;

  // ========== Mutable State ==========
  private TaskRunStatus status;
  private RunStats stats;
  private Instant startedAt;
  private Instant finishedAt;
  private Instant lastHeartbeat;
  private String error;
  private TaskRunCheckpoint checkpoint;
  private WindowSpec windowSpec;
  private RunContext runContext;

  // ========== Constructor for New Entities ==========
  public TaskRun(Long id, Long taskId, int attemptNo, String provenanceCode, String operationCode) {
    this.id = id;
    this.taskId = taskId;
    this.attemptNo = attemptNo;
    this.provenanceCode = provenanceCode;
    this.operationCode = operationCode;
    this.status = TaskRunStatus.PENDING;  // ✅ Default initial state
    this.stats = RunStats.empty();
    this.checkpoint = TaskRunCheckpoint.empty();
    this.runContext = RunContext.empty();
    // Other fields remain null until initialized
  }

  // ✅ Factory method restore() for DB reconstruction (omitted for brevity)

  // ========== Business Methods ==========

  public void start(WindowSpec windowSpec, RunContext context) {
    this.status = TaskRunStatus.RUNNING;
    this.startedAt = Instant.now();
    this.windowSpec = windowSpec;
    this.runContext = context;
  }

  public void complete(RunStats finalStats) {
    this.status = TaskRunStatus.SUCCEEDED;
    this.stats = finalStats;
    this.finishedAt = Instant.now();
  }

  // ✅ Additional methods: fail(), updateHeartbeat(), saveCheckpoint()
  // (Omitted for brevity - all follow similar pattern of updating state)
}
```

### Complete Entity Example: OutboxMessage

**File**: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/entity/OutboxMessage.java`

```java
package com.patra.ingest.domain.model.entity;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing an outbox message for reliable event publishing.
 *
 * <p>Uses Builder pattern for complex construction with many optional fields.
 */
public final class OutboxMessage {

  // ========== Core Identity & Metadata ==========
  private final Long id;
  private final Long version;  // Optimistic locking
  private final String aggregateType;
  private final Long aggregateId;
  private final String channel;
  private final String opType;

  // ========== Routing & Deduplication ==========
  private final String partitionKey;
  private final String dedupKey;  // Idempotency key

  // ========== Payload ==========
  private final String payloadJson;
  private final String headersJson;

  // ========== Scheduling & Retry ==========
  private final Instant notBefore;      // Earliest publish time
  private final String statusCode;      // PENDING, PUBLISHED, FAILED
  private final Integer retryCount;
  private final Instant nextRetryAt;

  // ========== Error Tracking ==========
  private final String errorCode;
  private final String errorMsg;

  // ========== Lease Management ==========
  private final String leaseOwner;      // Instance that acquired lease
  private final Instant leaseExpireAt;

  // ========== Private Constructor (Builder Pattern) ==========
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
  // Standard getters for all fields (getId(), getChannel(), getPartitionKey(), etc.)

  // ========== Domain Methods ==========

  /**
   * Calculate the next attempt number for retry logging.
   */
  public int computeNextAttempt() {
    return retryCount + 1;
  }

  /**
   * Check if message has exceeded max retry limit.
   */
  public boolean hasExceededRetries(int maxRetries) {
    return retryCount >= maxRetries;
  }

  // ========== Builder Pattern ==========

  public static Builder builder() {
    return new Builder();
  }

  public static final class Builder {
    private Long id;
    private String aggregateType;
    private Long aggregateId;
    private String channel;
    private String dedupKey;
    private String statusCode;
    private Integer retryCount;
    // ... (other 10 fields omitted for brevity)

    // ✅ Fluent setters (showing key examples)
    public Builder id(Long id) { this.id = id; return this; }
    public Builder aggregateType(String aggregateType) { this.aggregateType = aggregateType; return this; }
    public Builder channel(String channel) { this.channel = channel; return this; }
    public Builder dedupKey(String dedupKey) { this.dedupKey = dedupKey; return this; }
    public Builder retryCount(Integer retryCount) { this.retryCount = retryCount; return this; }
    // ... (other 13 fluent setters omitted for brevity)

    public OutboxMessage build() {
      return new OutboxMessage(this);
    }
  }
}
```

### Key Entity Patterns

| Pattern | Example | Purpose |
|---------|---------|---------|
| **Identity-based equality** | Equals by `id` field | Two entities are same if IDs match |
| **Mutable state** | `status`, `stats` fields | Entity state evolves over time |
| **Factory methods** | `restore()` | Reconstruct from persistence |
| **Business methods** | `start()`, `complete()`, `fail()` | Encapsulate state transitions |
| **Builder pattern** | `OutboxMessage.builder()` | Handle complex construction |

---

## Value Objects

### What is a Value Object?

A **Value Object** is an immutable object that represents a descriptive aspect of the domain with no conceptual identity. Two value objects are equal if all their attributes are equal.

### Use `record` for Value Objects

Java 17+ `record` is **perfect** for value objects because:
- ✅ Immutable by default
- ✅ Automatic equals/hashCode based on fields
- ✅ Compact syntax
- ✅ No boilerplate getters

### Complete Value Object Example: WindowSpec (Sealed Interface)

**File**: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/vo/plan/WindowSpec.java`

```java
package com.patra.ingest.domain.model.vo.plan;

import com.patra.ingest.domain.model.enums.SliceStrategy;
import java.time.Instant;
import java.util.Map;

/**
 * Window specification value object. Sealed hierarchy ensures compile-time exhaustiveness.
 *
 * <p>Supports different strategies for partitioning data collection:
 * <ul>
 *   <li>TIME: Time-based windows (from/to timestamps)
 *   <li>ID_RANGE: ID-based windows (from/to IDs)
 *   <li>CURSOR_LANDMARK: Cursor/pagination-based windows
 *   <li>VOLUME_BUDGET: Volume-limited windows
 *   <li>SINGLE: Single window (no partitioning)
 * </ul>
 */
public sealed interface WindowSpec
    permits WindowSpec.Time,
        WindowSpec.IdRange,
        WindowSpec.CursorLandmark,
        WindowSpec.VolumeBudget,
        WindowSpec.Single {

  /** Get the strategy type of this window specification. */
  SliceStrategy strategy();

  /** Convert to JSON-serializable map for persistence layer. */
  Map<String, Object> toMap();

  // ========== Strategy Implementations (Records) ==========

  /**
   * Time-based window specification.
   *
   * @param from start timestamp (inclusive)
   * @param to end timestamp (exclusive)
   */
  record Time(Instant from, Instant to) implements WindowSpec {
    // ✅ Compact constructor for validation
    public Time {
      if (from == null || to == null) {
        throw new IllegalArgumentException("Time window requires both from and to");
      }
      if (from.isAfter(to)) {
        throw new IllegalArgumentException("from must be before or equal to to");
      }
    }

    @Override
    public SliceStrategy strategy() {
      return SliceStrategy.TIME;
    }

    @Override
    public Map<String, Object> toMap() {
      Map<String, Object> boundaryMap = Map.of("from", "CLOSED", "to", "OPEN");
      Map<String, Object> windowMap = Map.of(
          "from", from.toString(),
          "to", to.toString(),
          "boundary", boundaryMap,
          "timezone", "UTC");
      return Map.of("strategy", SliceStrategy.TIME.getCode(), "window", windowMap);
    }
  }

  /**
   * ID range-based window specification.
   *
   * @param from start ID (inclusive)
   * @param to end ID (inclusive)
   */
  record IdRange(Long from, Long to) implements WindowSpec {
    public IdRange {
      if (from == null || to == null) {
        throw new IllegalArgumentException("ID range requires both from and to");
      }
      if (from > to) {
        throw new IllegalArgumentException("from must be less than or equal to to");
      }
    }

    @Override
    public SliceStrategy strategy() {
      return SliceStrategy.ID_RANGE;
    }

    @Override
    public Map<String, Object> toMap() {
      Map<String, Object> windowMap = Map.of("from", from, "to", to);
      return Map.of("strategy", SliceStrategy.ID_RANGE.getCode(), "window", windowMap);
    }
  }

  // ✅ Additional implementations follow the same pattern:
  // - CursorLandmark(String from, String to): Cursor/landmark-based pagination
  // - VolumeBudget(Integer limit, String unit): Volume budget-based window
  // - Single(): Single window (no partitioning)
  // (Implementations omitted for brevity - all follow the validation + strategy() + toMap() pattern)

  // ========== Factory Methods ==========

  static Time ofTime(Instant from, Instant to) {
    return new Time(from, to);
  }

  static IdRange ofIdRange(Long from, Long to) {
    return new IdRange(from, to);
  }

  // ✅ Additional factory methods: ofCursor(), ofVolume(), ofSingle(), fromMap()
  // (Omitted for brevity - all follow the same constructor delegation pattern)
}
```

### Complete Value Object Example: BatchPlan

**File**: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/vo/batch/BatchPlan.java`

```java
package com.patra.ingest.domain.model.vo.batch;

import java.util.List;

/**
 * Value object representing a batch planning outcome.
 *
 * <p>Invariants:
 * <ul>
 *   <li>{@code batches} must not be {@code null} (but may be empty).
 *   <li>{@code totalBatches} must be greater than or equal to zero.
 * </ul>
 *
 * @param batches batch list
 * @param totalBatches total number of batches
 * @param exceedsLimit whether the batch limit has been exceeded
 */
public record BatchPlan(List<Batch> batches, int totalBatches, boolean exceedsLimit) {

  // ✅ Compact constructor validates invariants
  public BatchPlan {
    if (batches == null) {
      throw new IllegalArgumentException("batches must not be null");
    }
    if (totalBatches < 0) {
      throw new IllegalArgumentException("totalBatches must not be negative");
    }
  }

  // ✅ Factory methods: empty(), single(Batch)
  // ✅ Domain methods: hasBatches()
  // (Omitted for brevity)
}
```

### Key Value Object Patterns

| Pattern | Example | Purpose |
|---------|---------|---------|
| **Use `record`** | `record BatchPlan(...)` | Immutable, concise, automatic equals/hashCode |
| **Compact constructor** | `public BatchPlan { ... }` | Validate invariants at construction |
| **Factory methods** | `empty()`, `single()` | Convenient creation |
| **Self-validation** | `if (batches == null) throw ...` | Guarantee validity |
| **Sealed interface** | `sealed interface WindowSpec permits ...` | Type-safe polymorphism |

---

## Domain Events

### What is a Domain Event?

A **Domain Event** captures something significant that happened in the domain. Events are **immutable** and named in **past tense** (e.g., `PlanCreatedEvent`, `TaskCompletedEvent`).

### Domain Events Best Practices

1. **Past tense naming**: `TaskCompletedEvent` (not `TaskCompleteEvent`)
2. **Immutable**: Use `record` for events
3. **Rich context**: Include all relevant information (IDs, status, timestamps)
4. **Auto-timestamp**: Populate `occurredAt` in constructor if not provided
5. **Factory methods**: Provide convenient creation methods

### Complete Domain Event Example: TaskCompletedEvent

**File**: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/event/TaskCompletedEvent.java`

```java
package com.patra.ingest.domain.event;

import com.patra.common.domain.DomainEvent;
import java.time.Instant;

/**
 * Domain event emitted when a task completes execution (either successfully or with failure).
 *
 * <p>Trigger: fired after a task transitions to a terminal state (SUCCEEDED, FAILED, PARTIAL).
 *
 * <p>Usage:
 * <ul>
 *   <li>Aggregation: triggers recomputation of parent Slice status based on all child Tasks.
 *   <li>Metrics: measure task completion rate, success rate, and failure rate by provenance.
 *   <li>Audit: trace task lifecycle from creation to completion.
 *   <li>Monitoring: alert on high failure rates or stuck tasks.
 * </ul>
 *
 * <p>Idempotency: {@code taskId} acts as the unique key. Handlers must check if the status
 * has already been processed.
 *
 * <p>Event Chain: TaskCompletedEvent → SliceStatusChangedEvent → PlanAggregate status update.
 */
public record TaskCompletedEvent(
    /* Primary identifier of the completed task. */
    Long taskId,
    /* Identifier of the owning slice. */
    Long sliceId,
    /* Identifier of the owning plan. */
    Long planId,
    /* Final status of the task (SUCCEEDED, FAILED, CURSOR_PENDING, etc). */
    String status,
    /* Error code if task failed (null if succeeded). */
    String errorCode,
    /* Error message if task failed (null if succeeded). */
    String errorMessage,
    /* Timestamp when the task finished execution. */
    Instant finishedAt,
    /* Timestamp when the event occurred. */
    Instant occurredAt)
    implements DomainEvent {

  // ✅ Compact constructor ensures event timestamp is always populated
  public TaskCompletedEvent {
    occurredAt = occurredAt == null ? Instant.now() : occurredAt;
  }

  // ✅ Factory method for successful task completion
  public static TaskCompletedEvent of(
      Long taskId, Long sliceId, Long planId, String status, Instant finishedAt) {
    return new TaskCompletedEvent(
        taskId, sliceId, planId, status, null, null, finishedAt, Instant.now());
  }

  // ✅ Additional factory method: ofFailure() with error details
  // (Omitted for brevity - follows same pattern with error parameters populated)
}
```

**Additional Domain Event Examples:**
- `SliceStatusChangedEvent`: Emitted when slice status changes (sliceId, planId, oldStatus, newStatus)
- (All events follow the same record + auto-timestamp + factory method pattern)

### Key Domain Event Patterns

| Pattern | Example | Purpose |
|---------|---------|---------|
| **Past tense naming** | `TaskCompletedEvent` | Describes what happened |
| **Use `record`** | `record TaskCompletedEvent(...)` | Immutable, automatic equals/hashCode |
| **Auto-timestamp** | `occurredAt = occurredAt == null ? Instant.now() : occurredAt` | Always have event time |
| **Factory methods** | `of()`, `ofFailure()` | Convenient creation |
| **Rich context** | Include IDs, status, error details | Handlers have all info needed |
| **Javadoc usage** | Document trigger, usage, idempotency | Help event handler authors |

---

## Domain Enums

### What is a Domain Enum?

A **Domain Enum** represents a fixed set of domain concepts with business meaning. Enums in the domain layer should:
- Encapsulate business rules (validation, parsing, description)
- Provide type-safe alternatives to string constants
- Support parsing from external inputs with normalization

### Complete Domain Enum Example: OperationCode

**File**: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/enums/OperationCode.java`

```java
package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/**
 * Ingestion operation type (DICT: ing_operation).
 *
 * <p><b>Persistence mapping</b>
 * <ul>
 *   <li>ing_plan.operation_code → HARVEST/BACKFILL/UPDATE/METRICS
 *   <li>ing_task.operation_code → HARVEST/BACKFILL/UPDATE/METRICS
 * </ul>
 *
 * <p><b>Parsing/output contract</b>
 * <ul>
 *   <li>Always emit uppercase values via {@link #getCode()}.
 *   <li>Parse using {@link #fromCode(String)} which trims and uppercases; unknown values raise
 *       {@link IllegalArgumentException}.
 * </ul>
 */
@Getter
public enum OperationCode {
  /** Initial full ingestion (first run or rebuilt windows). */
  HARVEST("HARVEST", "Full ingestion"),
  /** Historical backfill to close gaps or correct data. */
  BACKFILL("BACKFILL", "Backfill ingestion"),
  /** Incremental updates driven by cursor progression. */
  UPDATE("UPDATE", "Incremental update"),
  /** Metrics/statistics-oriented operations (read-heavy). */
  METRICS("METRICS", "Metrics collection");

  private final String code;
  private final String description;

  OperationCode(String code, String description) {
    this.code = code;
    this.description = description;
  }

  /**
   * Parse the provided code into the enumeration.
   *
   * <p>✅ Normalizes input: trims whitespace and converts to uppercase.
   *
   * @param value string code (e.g., {@code "harvest"} or {@code " UPDATE "})
   * @return matching enum
   * @throws IllegalArgumentException when the value is null or unknown
   */
  public static OperationCode fromCode(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Operation code cannot be null");
    }
    String normalized = value.trim().toUpperCase();
    for (OperationCode oc : values()) {
      if (oc.code.equals(normalized)) {
        return oc;
      }
    }
    throw new IllegalArgumentException("Unknown operation code: " + value);
  }
}
```

### Key Domain Enum Patterns

| Pattern | Example | Purpose |
|---------|---------|---------|
| **Code + Description** | `HARVEST("HARVEST", "Full ingestion")` | Human-readable meaning |
| **Parsing method** | `fromCode(String)` | Safe conversion from external inputs |
| **Normalization** | `value.trim().toUpperCase()` | Handle case variations |
| **Validation** | `throw IllegalArgumentException` | Fail fast on invalid input |
| **Javadoc mapping** | Document persistence mapping | Help infra layer authors |

---

## Ports (Interfaces)

### What is a Port?

A **Port** is an interface defined in the Domain layer that represents a contract for external interactions. Ports follow the **Dependency Inversion Principle**: the domain defines what it needs, and the infrastructure implements it.

### Types of Ports

1. **Repository Ports**: Data access contracts (save, find, update, delete)
2. **External Service Ports**: Third-party API contracts (PubmedSearchPort, StoragePort)
3. **Messaging Ports**: Event publishing contracts (OutboxPublisherPort)

### Complete Repository Port Example: PlanRepository

**File**: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/PlanRepository.java`

```java
package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.PlanAggregate;
import java.util.Optional;

/**
 * Repository port for plan aggregates.
 *
 * <p>Persists, deduplicates, and retrieves ingestion plans to guarantee consistency during
 * creation and replay.
 *
 * <p>✅ Defined in Domain layer, implemented in Infrastructure layer.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface PlanRepository {

  /**
   * Persist or update a single plan aggregate.
   *
   * <p>✅ Handles both inserts (id == null) and updates (id != null).
   * <p>✅ Uses optimistic locking (version field) to prevent lost updates.
   *
   * @param plan plan aggregate containing window, trigger, and slicing strategy
   * @return persisted aggregate with populated ID and updated version
   */
  PlanAggregate save(PlanAggregate plan);

  /**
   * Retrieve a plan aggregate by {@code planKey}.
   *
   * <p>The planKey is the business idempotency key derived from:
   * <ul>
   *   <li>Provenance code</li>
   *   <li>Operation code</li>
   *   <li>Window specification</li>
   *   <li>Slicing strategy</li>
   * </ul>
   *
   * @param planKey unique plan key
   * @return matching plan or {@link Optional#empty()}
   */
  Optional<PlanAggregate> findByPlanKey(String planKey);

  /**
   * Check whether the given {@code planKey} already exists.
   *
   * <p>✅ More efficient than {@code findByPlanKey().isPresent()} when you only need existence check.
   *
   * @param planKey unique plan key
   * @return {@code true} when a plan exists
   */
  boolean existsByPlanKey(String planKey);

  /**
   * Retrieve a plan aggregate by identifier.
   *
   * @param planId plan identifier
   * @return plan aggregate or {@link Optional#empty()}
   */
  Optional<PlanAggregate> findById(Long planId);
}
```

### Key Port Patterns

| Pattern | Example | Purpose |
|---------|---------|---------|
| **Interface in Domain** | `public interface PlanRepository` | Domain owns the contract |
| **Domain types only** | `PlanAggregate`, not `PlanDO` | No infra leakage |
| **Optional returns** | `Optional<PlanAggregate>` | Explicit "not found" case |
| **Clear method names** | `findByPlanKey`, `existsByPlanKey` | Intent is obvious |
| **Javadoc contracts** | Document behavior, not implementation | Help implementers |

---

## Factories

### What is a Factory?

A **Factory** is a domain object responsible for creating complex domain objects. Factories encapsulate:
- Complex construction logic
- Validation and invariant enforcement
- Domain rules for object creation

### When to Use Factories

✅ **Use factories when:**
- Object construction is complex (many parameters, validation)
- Construction logic needs to be reused in multiple places
- Need to hide implementation details
- Domain rules govern object creation

❌ **Skip factories for:**
- Simple objects (use constructors or static factory methods on the class itself)
- Aggregates/entities with `create()` and `restore()` factory methods already

### Complete Factory Example: OutboxRelayLogFactory

**File**: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/factory/OutboxRelayLogFactory.java`

```java
package com.patra.ingest.domain.factory;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.entity.OutboxRelayLog;
import com.patra.ingest.domain.model.enums.RelayStatus;
import com.patra.ingest.domain.model.vo.relay.RelayBatchId;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Factory for creating OutboxRelayLog domain entities.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Encapsulate relay log construction logic in Domain layer
 *   <li>Use OutboxMessage's domain methods (computeNextAttempt, etc.)
 *   <li>Ensure consistent log creation across different relay outcomes
 * </ul>
 *
 * <p>Design principles:
 * <ul>
 *   <li><strong>Pure Java</strong>: No Spring dependencies (NO @Component annotation)
 *   <li><strong>Instance factory</strong>: Injectable Clock for deterministic testing
 *   <li><strong>Single responsibility</strong>: Only constructs OutboxRelayLog instances
 *   <li><strong>Domain rules</strong>: Delegates to OutboxMessage for business logic
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * // Boot layer registers as bean:
 * @Bean
 * public OutboxRelayLogFactory factory(Clock clock) {
 *   return new OutboxRelayLogFactory(clock);
 * }
 *
 * // Application layer uses injected factory:
 * OutboxRelayLog log = factory.createForPublished(message, batchId, ...);
 * }</pre>
 */
public class OutboxRelayLogFactory {

  private final Clock clock;

  public OutboxRelayLogFactory(Clock clock) {
    this.clock = clock;
  }

  /**
   * Creates relay log for successful message publishing.
   *
   * <p>Scenario: Message successfully sent to downstream broker.
   */
  public OutboxRelayLog createForPublished(
      OutboxMessage message,
      RelayBatchId batchId,
      String leaseOwner,
      Instant startTime,
      Instant publishedAt) {

    return OutboxRelayLog.builder()
        .outboxMessageId(message.getId())
        .relayBatchId(batchId.getValue())
        .channel(message.getChannel())
        .partitionKey(message.getPartitionKey())
        .leaseOwner(leaseOwner)
        .attemptNumber(message.computeNextAttempt())  // ✅ Uses domain method
        .relayStatus(RelayStatus.PUBLISHED)
        .startedAt(startTime)
        .completedAt(publishedAt)
        .durationMs((int) Duration.between(startTime, publishedAt).toMillis())
        .build();
  }

  // ✅ Additional factory methods: createForDeferred(), createForLeaseMissed(), createForFailed()
  // (Implementations omitted for brevity - all follow same builder pattern with status-specific fields)
}
```

### Key Factory Patterns

| Pattern | Example | Purpose |
|---------|---------|---------|
| **Pure Java class** | No `@Component` | Keep domain framework-free |
| **Injectable dependencies** | `Clock clock` | Testability (deterministic time) |
| **Semantic methods** | `createForPublished()`, `createForFailed()` | Intent is clear |
| **Domain method delegation** | `message.computeNextAttempt()` | Reuse domain logic |
| **Consistent construction** | All logs have same fields | Guarantee invariants |

---

## Domain Services

### What is a Domain Service?

A **Domain Service** is a stateless operation that doesn't naturally belong to any Aggregate or Entity. Use domain services for:
- Operations spanning multiple aggregates
- Calculations based on multiple domain objects
- Complex validations or business rules

### When to Use Domain Services

✅ **Use domain services when:**
- Operation involves multiple aggregates
- Logic doesn't fit naturally in any single aggregate
- Need stateless reusable logic

❌ **Avoid domain services for:**
- Operations that belong to a specific aggregate (put them in the aggregate)
- Infrastructure concerns (use Application layer or Infrastructure layer)

### Complete Domain Service Example: SliceStatusCalculator

**File**: `patra-ingest/patra-ingest-domain/src/main/java/com/patra/ingest/domain/service/SliceStatusCalculator.java`

```java
package com.patra.ingest.domain.service;

import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.ingest.domain.model.enums.TaskStatus;

/**
 * Domain Service for calculating Slice status based on its associated Task (enforces 1:1
 * relationship).
 *
 * <p>This is a stateless pure function that directly maps Task status to Slice status.
 *
 * <p>After refactoring, Slice:Task is a 1:1 relationship, so no aggregation is needed. The mapping
 * rules are:
 *
 * <ul>
 *   <li>TaskStatus.PENDING or QUEUED → SliceStatus.PENDING (awaiting Task generation/execution)
 *   <li>TaskStatus.RUNNING → SliceStatus.ASSIGNED (Task in progress)
 *   <li>TaskStatus.SUCCEEDED or FAILED → SliceStatus.FINISHED (Task reached terminal state)
 * </ul>
 *
 * <p><b>Note:</b> Slice no longer distinguishes success/failure. Query the Task directly for
 * execution results.
 */
public final class SliceStatusCalculator {

  private SliceStatusCalculator() {
    // ✅ Utility class, prevent instantiation
  }

  /**
   * Calculates the Slice status based on its associated Task status (1:1 mapping).
   *
   * @param taskStatus the status of the single associated Task (must not be null)
   * @return the corresponding Slice status
   * @throws IllegalArgumentException if taskStatus is null
   */
  public static SliceStatus calculate(TaskStatus taskStatus) {
    if (taskStatus == null) {
      throw new IllegalArgumentException("Task status cannot be null");
    }

    // ✅ Use switch expression for exhaustive matching
    return switch (taskStatus) {
      case PENDING, QUEUED -> SliceStatus.PENDING;
      case RUNNING -> SliceStatus.ASSIGNED;
      case SUCCEEDED, FAILED -> SliceStatus.FINISHED;
    };
  }

  /**
   * Checks if a Task status is a terminal state (no further transitions expected).
   *
   * @param status task status
   * @return true if terminal, false otherwise
   */
  public static boolean isTerminal(TaskStatus status) {
    return status == TaskStatus.SUCCEEDED || status == TaskStatus.FAILED;
  }
}
```

### Key Domain Service Patterns

| Pattern | Example | Purpose |
|---------|---------|---------|
| **Stateless** | `final class`, `private constructor` | No instance state |
| **Static methods** | `public static SliceStatus calculate(...)` | Clear it's stateless |
| **Pure functions** | No side effects | Predictable, testable |
| **Domain types only** | `TaskStatus`, `SliceStatus` | No framework dependencies |
| **Validation** | `if (taskStatus == null) throw ...` | Fail fast |

---

## Key Principles

### 领域层 Constraints

| ✅ Allowed | ❌ Forbidden |
|-----------|-------------|
| Pure Java | Spring annotations (`@Component`, `@Service`) |
| Lombok (`@Getter`, `@Builder`) | Framework dependencies (Spring, JPA, MyBatis) |
| Hutool utilities | Database annotations (`@TableName`, `@TableId`) |
| Java 17+ features (`record`, `sealed`) | Jackson annotations (`@JsonProperty`) |
| Domain events | Direct HTTP/RPC calls |
| Port interfaces | Direct infrastructure access |

### Domain Design Checklist

Before committing domain code, verify:

- [ ] **No framework dependencies**: Only Pure Java + Lombok + Hutool
- [ ] **Invariants enforced**: Validation in constructors
- [ ] **Immutability where appropriate**: Value objects use `record`, immutable fields are `final`
- [ ] **Factory methods**: `create()` for new, `restore()` for DB reconstruction
- [ ] **Domain events**: Emitted for significant state changes
- [ ] **Ports for external access**: No direct infrastructure dependencies
- [ ] **Clear naming**: Aggregates/Entities are nouns, Events are past tense
- [ ] **Business logic in domain**: Not in Application or Infrastructure layers

---

## Testing Domain Models

### Unit Testing Domain Objects

Domain models are **highly testable** because they have no framework dependencies.

#### 测试 Aggregates

```java
@Test
void should_create_plan_in_draft_status() {
  // Given
  WindowSpec window = WindowSpec.ofTime(
      Instant.parse("2024-01-01T00:00:00Z"),
      Instant.parse("2024-12-31T23:59:59Z"));

  // When
  PlanAggregate plan = PlanAggregate.create(
      12345L,
      "PUBMED_HARVEST_2024",
      "PUBMED",
      "HARVEST",
      "expr_hash_123",
      "{}",
      "{}",
      "config_hash_456",
      window,
      "TIME",
      "{}");

  // Then
  assertThat(plan.getStatus()).isEqualTo(PlanStatus.DRAFT);
  assertThat(plan.getPlanKey()).isEqualTo("PUBMED_HARVEST_2024");
}

@Test
void should_transition_from_draft_to_slicing() {
  // Given
  PlanAggregate plan = createDraftPlan();

  // When
  plan.startSlicing();

  // Then
  assertThat(plan.getStatus()).isEqualTo(PlanStatus.SLICING);
}

// ... (additional tests for state machine validation omitted for brevity)
```

#### 测试 Value Objects

```java
@Test
void should_create_and_validate_value_objects() {
  // Test WindowSpec
  WindowSpec window = WindowSpec.ofTime(
      Instant.parse("2024-01-01T00:00:00Z"),
      Instant.parse("2024-12-31T23:59:59Z"));
  assertThat(window).isInstanceOf(WindowSpec.Time.class);
  assertThat(window.strategy()).isEqualTo(SliceStrategy.TIME);

  // Test BatchPlan
  BatchPlan plan = BatchPlan.single(new Batch(1, 100));
  assertThat(plan.hasBatches()).isTrue();
}

// ... (validation tests omitted for brevity)
```

#### 测试 Domain Services

```java
@Test
void should_calculate_slice_status_and_check_terminal_state() {
  // Test status calculation
  assertThat(SliceStatusCalculator.calculate(TaskStatus.PENDING))
      .isEqualTo(SliceStatus.PENDING);
  assertThat(SliceStatusCalculator.calculate(TaskStatus.SUCCEEDED))
      .isEqualTo(SliceStatus.FINISHED);

  // Test terminal state check
  assertThat(SliceStatusCalculator.isTerminal(TaskStatus.SUCCEEDED)).isTrue();
  assertThat(SliceStatusCalculator.isTerminal(TaskStatus.PENDING)).isFalse();
}
```

#### 测试 Factories

```java
@Test
void should_create_relay_log_for_published_message() {
  // Given
  Clock fixedClock = Clock.fixed(Instant.parse("2024-01-15T10:00:00Z"), ZoneId.of("UTC"));
  OutboxRelayLogFactory factory = new OutboxRelayLogFactory(fixedClock);

  OutboxMessage message = OutboxMessage.builder()
      .id(123L)
      .channel("literature.created")
      .partitionKey("PUBMED_12345")
      .retryCount(2)
      .build();

  RelayBatchId batchId = new RelayBatchId("batch_001");
  Instant startTime = Instant.parse("2024-01-15T09:59:55Z");
  Instant publishedAt = Instant.parse("2024-01-15T09:59:59Z");

  // When
  OutboxRelayLog log = factory.createForPublished(
      message, batchId, "instance-01", startTime, publishedAt);

  // Then
  assertThat(log.getOutboxMessageId()).isEqualTo(123L);
  assertThat(log.getRelayBatchId()).isEqualTo("batch_001");
  assertThat(log.getAttemptNumber()).isEqualTo(3);  // retryCount + 1
  assertThat(log.getRelayStatus()).isEqualTo(RelayStatus.PUBLISHED);
  assertThat(log.getDurationMs()).isEqualTo(4000);  // 4 seconds
}
```

---

**相关文件：**
- [architecture-overview.md](architecture-overview.md) - Hexagonal Architecture + DDD overview
- [dependency-rules.md](dependency-rules.md) - Layer dependency rules and validation
- [complete-examples.md](complete-examples.md) - End-to-end feature examples
- [orchestrator-coordinator-patterns.md](orchestrator-coordinator-patterns.md) - Application layer patterns
- [mybatis-plus-patterns.md](mybatis-plus-patterns.md) - Infrastructure layer persistence

---

**📝 Status**: ✅ **已完成** - Comprehensive guide to all DDD tactical patterns from patra-ingest domain layer with real examples.
