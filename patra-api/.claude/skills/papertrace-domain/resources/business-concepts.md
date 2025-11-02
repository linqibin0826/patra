# Papertrace Core Business Concepts

## Overview

This document describes Papertrace's core domain entities based on actual database schema and Java implementation. All entities are from `patra-ingest-domain` and `patra-registry-domain`.

**Entity Hierarchy**:
```
ScheduleInstanceAggregate (Trigger root)
  ↓ 1:N
PlanAggregate (Orchestration blueprint)
  ↓ 1:N
PlanSliceAggregate (Parallelism unit)
  ↓ 1:1
TaskAggregate (Executable unit)
  ↓ 1:N
TaskRun (Execution attempt)
  ↓ 1:N
TaskRunBatch (Pagination batch)
```

---

## 1. Provenance (Registry Domain)

### Definition
Data source from which literature is harvested (PubMed, EPMC, Crossref, etc.). Stored in `patra-registry`.

### Entity (Value Object)

**Table**: `reg_provenance`

**Java** (`patra-registry-domain`):
```java
public record Provenance(
    Long id,
    String code,                // 'PUBMED', 'EPMC', 'CROSSREF'
    String name,                // Display name
    String baseUrlDefault,      // Default base URL
    String timezoneDefault,     // Default timezone (e.g., 'UTC', 'America/New_York')
    String docsUrl,             // Documentation URL
    boolean active,             // Operational flag
    String lifecycleStatusCode  // 'ACTIVE', 'DEPRECATED', 'RETIRED'
)
```

**Key Fields**:
| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `code` | String | Unique identifier | `'PUBMED'` |
| `name` | String | Display name | `'PubMed'` |
| `baseUrlDefault` | String | Default API base URL | `'https://eutils.ncbi.nlm.nih.gov'` |
| `timezoneDefault` | String | Default timezone | `'UTC'` |
| `active` | boolean | Operational status | `true` |
| `lifecycleStatusCode` | String | Lifecycle status | `'ACTIVE'` |

**Note**: Provenance has NO `description`, `createdAt`, or `updatedAt` fields in the main table. Audit fields exist in `reg_prov_snapshot` table.

---

## 2. ScheduleInstanceAggregate (Ingest Domain)

### Definition
External trigger event that initiates plan creation (scheduler, manual trigger, webhook).

### Aggregate Root

**Table**: `ing_schedule_instance`

**Java** (`patra-ingest-domain`):
```java
public class ScheduleInstanceAggregate extends AggregateRoot<Long> {
    private String schedulerCode;      // 'QUARTZ', 'MANUAL', 'WEBHOOK'
    private String triggerTypeCode;    // 'CRON', 'ONCE', 'MANUAL'
    private Instant triggeredAt;
    private String provenanceCode;     // Plain String (not value object)
    private String operationCode;      // 'HARVEST', 'UPDATE', 'BACKFILL'
    private String statusCode;         // 'TRIGGERED', 'COMPLETED', 'FAILED'
}
```

**States**:
- **TRIGGERED**: Schedule fired
- **COMPLETED**: All associated plans succeeded
- **FAILED**: Any plan failed

**Purpose**: Root entity for plan creation, tracks trigger source and timing.

---

## 3. PlanAggregate (Ingest Domain)

### Definition
Orchestration blueprint containing expression prototype, config snapshot, window spec, and slicing strategy.

### Aggregate Root

**Table**: `ing_plan`

**Java** (`patra-ingest-domain`):
```java
public class PlanAggregate extends AggregateRoot<Long> {
    // Identifiers
    private Long scheduleInstanceId;       // FK to ScheduleInstance
    private String planKey;                // Idempotency: "PUBMED:HARVEST:1704067200000-1706745599999"

    // Provenance & Operation
    private String provenanceCode;         // Plain String
    private String operationCode;          // 'HARVEST', 'UPDATE', 'BACKFILL'

    // Expression
    private String exprProtoHash;          // SHA256(expr_proto_snapshot)
    private JsonNode exprProtoSnapshot;    // Expression prototype (no boundaries)

    // Configuration
    private JsonNode provenanceConfigSnapshot;  // Config snapshot from registry

    // Window & Slicing
    private JsonNode windowSpec;           // Serialized WindowSpec
    private String sliceStrategyCode;      // 'TIME', 'DATE', 'SINGLE'

    // State
    private String statusCode;             // 'DRAFT', 'SLICING', 'READY', 'ARCHIVED'
    private Instant createdAt;
}
```

**Key Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `planKey` | String | Idempotency key |
| `exprProtoHash` | String | SHA256 of expression prototype |
| `exprProtoSnapshot` | JsonNode | Expression without boundaries |
| `provenanceConfigSnapshot` | JsonNode | Immutable config snapshot |
| `windowSpec` | JsonNode | WindowSpec (Time/IdRange/Single/etc.) |
| `sliceStrategyCode` | String | Slicing strategy |
| `statusCode` | String | DRAFT/SLICING/READY/ARCHIVED |

**Plan Key Format**:
```
{provenanceCode}:{operationCode}:{windowFromMillis}-{windowToMillis}
Example: "PUBMED:HARVEST:1704067200000-1706745599999"
```

---

## 4. PlanSliceAggregate (Ingest Domain)

### Definition
Parallelism unit representing a time/ID slice of the plan window. Each slice becomes exactly ONE task.

### Aggregate Root

**Table**: `ing_plan_slice`

**Java** (`patra-ingest-domain`):
```java
public class PlanSliceAggregate extends AggregateRoot<Long> {
    // Identity
    private Long planId;                   // FK to Plan
    private Integer sliceNo;               // Sequence number (1..N)

    // Signature & Expression
    private String sliceSignatureHash;     // SHA256(normalized windowSpec)
    private String exprHash;               // SHA256(expr_snapshot)
    private String exprSnapshotJson;       // Localized expression (TEXT, with boundaries)

    // Window
    private JsonNode windowSpecJson;       // Narrowed window (e.g., 2024-01-01 to 2024-01-07)

    // State
    private String statusCode;             // 'PENDING', 'ASSIGNED', 'FINISHED'
    private Instant createdAt;
}
```

**Key Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `sliceNo` | Integer | Sequence number within plan |
| `sliceSignatureHash` | String | SHA256 of normalized window (idempotency) |
| `exprHash` | String | SHA256 of localized expression |
| `exprSnapshotJson` | String (TEXT) | Expression with injected boundaries |
| `windowSpecJson` | JsonNode | Slice window spec |
| `statusCode` | String | PENDING/ASSIGNED/FINISHED |

**Slice Signature Hash**:
```
Input: WindowSpec.Time(from=2024-01-01T00:00:00Z, to=2024-01-07T23:59:59Z)
Canonical: "Time{from:1704067200000,to:1704671999000}"
Hash: SHA256(canonical) → "a7f3e2d1c4b8..."
```

**States**:
- **PENDING**: Slice created, task not created yet
- **ASSIGNED**: Task created and dispatched
- **FINISHED**: Task execution completed

---

## 5. TaskAggregate (Ingest Domain)

### Definition
Executable unit representing one plan slice. Managed via lease-based distributed execution.

### Aggregate Root

**Table**: `ing_task`

**Java** (`patra-ingest-domain`):
```java
public class TaskAggregate extends AggregateRoot<Long> {
    // Identity
    private Long sliceId;                  // FK to PlanSlice (1:1, UNIQUE)
    private String idempotentKey;          // Base64Url(SHA256(provenance|operation|sliceSignatureHash))

    // Provenance & Operation
    private String provenanceCode;         // Plain String
    private String operationCode;          // 'HARVEST', 'UPDATE', 'BACKFILL'

    // Expression
    private String exprHash;               // Same as slice.exprHash

    // Parameters
    private String paramsJson;             // JSON: {"sliceNo": 5}

    // Scheduling
    private Integer priority;              // Execution priority
    private Instant scheduledAt;           // When to execute

    // Lease (Distributed Locking)
    private LeaseInfo leaseInfo;           // Owner, expiration

    // State
    private String statusCode;             // 'QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED'
    private Integer retryCount;            // Current retry count

    // Timeline
    private ExecutionTimeline executionTimeline;  // Start/end times

    private Instant createdAt;
}
```

**Key Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `sliceId` | Long | 1:1 FK to PlanSlice (UNIQUE) |
| `idempotentKey` | String | Base64Url encoded SHA256 hash |
| `paramsJson` | String | Task metadata (NOT API params) |
| `leaseInfo` | LeaseInfo | Lease owner + expiration |
| `statusCode` | String | QUEUED/RUNNING/SUCCEEDED/FAILED |
| `retryCount` | Integer | Current retry attempt |
| `executionTimeline` | ExecutionTimeline | Timing information |

**Idempotent Key Format**:
```
Composite: "{provenanceCode}|{operationCode}|{sliceSignatureHash}"
Hash: SHA256(composite)
Encode: Base64Url(hash)
Example: "x3jK8pL2mN9qR4tV6wY8zA"
```

**Task Parameters** (paramsJson):
```json
{
  "sliceNo": 5
}
```
**NOTE**: Contains ONLY metadata. API parameters come from expression rendering.

**States**:
- **QUEUED**: Created, waiting for worker
- **RUNNING**: Worker acquired lease
- **SUCCEEDED**: Completed successfully
- **FAILED**: Failed, retries exhausted

---

## 6. LeaseInfo (Value Object)

### Definition
Distributed task locking information.

**Java** (`patra-ingest-domain`):
```java
public record LeaseInfo(
    String leaseOwner,        // Worker instance ID
    Instant leasedUntil       // Lease expiration time
) {
    public boolean isExpired() {
        return leasedUntil != null && Instant.now().isAfter(leasedUntil);
    }

    public boolean isOwnedBy(String workerId) {
        return leaseOwner != null && leaseOwner.equals(workerId);
    }
}
```

**Database Representation**:
```sql
-- ing_task table
lease_owner VARCHAR(128),
leased_until TIMESTAMP(3)
```

**Purpose**: Prevent multiple workers from executing the same task concurrently.

---

## 7. ExecutionTimeline (Value Object)

### Definition
Task execution timing information.

**Java** (`patra-ingest-domain`):
```java
public record ExecutionTimeline(
    Instant startedAt,
    Instant completedAt,
    Instant lastHeartbeatAt
) {
    public Duration duration() {
        if (startedAt == null || completedAt == null) {
            return null;
        }
        return Duration.between(startedAt, completedAt);
    }

    public boolean isRunning() {
        return startedAt != null && completedAt == null;
    }
}
```

**Purpose**: Track task execution lifecycle.

---

## 8. TaskRun (Entity)

### Definition
Represents one execution attempt of a task (supports retries).

**Table**: `ing_task_run`

**Java** (`patra-ingest-domain`):
```java
public class TaskRun extends Entity<Long> {
    private Long taskId;               // FK to Task
    private Integer attemptNo;         // 1, 2, 3, ...
    private String statusCode;         // 'PENDING', 'RUNNING', 'PARTIAL', 'SUCCEEDED', 'FAILED'
    private Instant startedAt;
    private Instant completedAt;
    private JsonNode checkpoint;       // Cursor state for resume
    private JsonNode stats;            // {"recordsFetched": 1500, "recordsStored": 1498}
    private String errorMessage;       // Error details if failed
}
```

**Key Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `attemptNo` | Integer | Retry attempt number |
| `statusCode` | String | PENDING/RUNNING/PARTIAL/SUCCEEDED/FAILED |
| `checkpoint` | JsonNode | Resume point for pagination |
| `stats` | JsonNode | Execution statistics |
| `errorMessage` | String | Failure reason |

**States**:
- **PENDING**: Created, not started
- **RUNNING**: Actively executing
- **PARTIAL**: Paused mid-pagination (checkpoint saved)
- **SUCCEEDED**: Completed successfully
- **FAILED**: Failed (triggers task retry)

**Checkpoint Example**:
```json
{
  "cursor": "abc123def456",
  "lastProcessedId": 9876543,
  "batchNo": 25
}
```

---

## 9. TaskRunBatch (Entity)

### Definition
Represents one pagination batch within a task run.

**Table**: `ing_task_run_batch`

**Java** (`patra-ingest-domain`):
```java
public class TaskRunBatch extends Entity<Long> {
    private Long runId;                // FK to TaskRun
    private Integer batchNo;           // 1, 2, 3, ...
    private String beforeToken;        // Cursor/offset before fetch
    private String afterToken;         // Cursor/offset after fetch
    private Integer recordsFetched;    // Records returned by API
    private Integer recordsStored;     // Records actually stored
    private String idempotentKey;      // Deduplication key
    private Instant createdAt;
}
```

**Key Fields**:
| Field | Type | Description |
|-------|------|-------------|
| `batchNo` | Integer | Sequence number within run |
| `beforeToken` | String | Pagination cursor before fetch |
| `afterToken` | String | Pagination cursor after fetch |
| `recordsFetched` | Integer | API response count |
| `recordsStored` | Integer | Successfully persisted count |

**Pagination Tokens** (PubMed offset-based example):
```
Batch 1: beforeToken="0",    afterToken="1000"
Batch 2: beforeToken="1000", afterToken="2000"
Batch 3: beforeToken="2000", afterToken="3000"
```

**Pagination Tokens** (Cursor-based example):
```
Batch 1: beforeToken=null,          afterToken="cursor_abc123"
Batch 2: beforeToken="cursor_abc123", afterToken="cursor_def456"
```

---

## 10. WindowSpec (Sealed Interface)

### Definition
Window boundary specification with multiple strategies.

**Java** (`patra-ingest-domain`):
```java
public sealed interface WindowSpec permits
    WindowSpec.Time,
    WindowSpec.IdRange,
    WindowSpec.CursorLandmark,
    WindowSpec.VolumeBudget,
    WindowSpec.Single
{
    record Time(Instant from, Instant to) implements WindowSpec {}
    record IdRange(Long from, Long to) implements WindowSpec {}
    record CursorLandmark(String from, String to) implements WindowSpec {}
    record VolumeBudget(Long limit, String unit) implements WindowSpec {}
    record Single() implements WindowSpec {}
}
```

**Variants**:
| Variant | Use Case | Example |
|---------|----------|---------|
| **Time** | Time-based windows | `Time(2024-01-01T00:00:00Z, 2024-01-31T23:59:59Z)` |
| **IdRange** | ID-based pagination | `IdRange(1L, 1000000L)` |
| **CursorLandmark** | Cursor-based APIs | `CursorLandmark("start", "end")` |
| **VolumeBudget** | Volume-limited | `VolumeBudget(10000L, "RECORDS")` |
| **Single** | No slicing | `Single()` |

---

## Entity Relationships

### Database Foreign Keys

```sql
ing_schedule_instance
  ↓ (1:N via schedule_instance_id)
ing_plan
  ↓ (1:N via plan_id)
ing_plan_slice
  ↓ (1:1 via slice_id, UNIQUE)
ing_task
  ↓ (1:N via task_id)
ing_task_run
  ↓ (1:N via run_id)
ing_task_run_batch
```

### Key Constraints

**1:1 Slice-Task Relationship**:
```sql
ALTER TABLE ing_task
ADD UNIQUE KEY uk_task_slice(slice_id);
```

**Plan Idempotency**:
```sql
ALTER TABLE ing_plan
ADD UNIQUE KEY uk_plan_key(plan_key);
```

**Task Idempotency**:
```sql
ALTER TABLE ing_task
ADD UNIQUE KEY uk_task_idempotent(idempotent_key);
```

---

## Aggregate vs Entity vs Value Object

### Aggregates (Have Identity + Lifecycle)
- ScheduleInstanceAggregate
- PlanAggregate
- PlanSliceAggregate
- TaskAggregate

### Entities (Have Identity, Owned by Aggregates)
- TaskRun (owned by TaskAggregate)
- TaskRunBatch (owned by TaskRun)

### Value Objects (Immutable, No Identity)
- Provenance (in Registry context)
- WindowSpec
- LeaseInfo
- ExecutionTimeline
- ProvenanceConfigSnapshot

---

## Naming Conventions

**Actual vs Documented**:
| Skill Documentation | Actual Code |
|---------------------|-------------|
| `BatchPlan` | `PlanAggregate` |
| `BatchTask` | `TaskAggregate` |
| `Slice` | `PlanSliceAggregate` |
| `businessKey` | `idempotentKey` |
| `Map<String, Object> params` | `String paramsJson` |
| `ProvenanceCode` (value object) | `String provenanceCode` |

---

## Summary

**Core Entities**:
1. **ScheduleInstanceAggregate**: Trigger root
2. **PlanAggregate**: Orchestration blueprint
3. **PlanSliceAggregate**: Parallelism unit (1:1 with Task)
4. **TaskAggregate**: Executable unit with lease
5. **TaskRun**: Execution attempt
6. **TaskRunBatch**: Pagination batch

**Key Concepts**:
- **1:1:1 Hierarchy**: Plan → PlanSlice → Task (enforced by DB)
- **Idempotency**: Plan key, Task idempotent key, Slice signature hash
- **Lease-Based Execution**: Distributed task locking via LeaseInfo
- **Snapshot Isolation**: Config + Expression snapshots at Plan level
- **Pagination**: Handled at TaskRunBatch level (not Task params)

**Database Tables**:
- `ing_schedule_instance`, `ing_plan`, `ing_plan_slice`, `ing_task`, `ing_task_run`, `ing_task_run_batch`
- `reg_provenance` (in patra-registry)
