# Plan/Task 工作流与生命周期

## 概览

本文档描述了 Patra 中 **ScheduleInstance → Plan → PlanSlice → Task → TaskRun** 的完整生命周期，基于实际的数据库模式和代码实现。

**核心架构**：
- **四层层次结构**：ScheduleInstance → Plan → PlanSlice → Task（1:1:1）
- **PlanSlice 作为并行单元**：每个切片恰好对应一个任务
- **Outbox 模式**：通过事务性 outbox 进行任务分发
- **基于租约的执行**：分布式任务锁定机制

---

## Entity Hierarchy

```
ScheduleInstanceAggregate (Trigger)
    ↓ 1:N
PlanAggregate (Blueprint: expr prototype + config snapshot + window + strategy)
    ↓ 1:N
PlanSliceAggregate (Slice: localized expr + window slice + signature)
    ↓ 1:1 (enforced by DB: UNIQUE KEY uk_task_slice(slice_id))
TaskAggregate (Executable unit with lease + timeline)
    ↓ 1:N
TaskRun (Execution attempt)
    ↓ 1:N
TaskRunBatch (Pagination batch with tokens)
```

**Database Enforcement**:
```sql
-- ing_task table
UNIQUE KEY uk_task_slice(slice_id)  -- ONE task per slice
```

---

## ScheduleInstance Lifecycle

### 目的
External trigger event that initiates plan creation (e.g., scheduler, manual trigger, webhook).

### Table Schema
```sql
CREATE TABLE ing_schedule_instance (
  id BIGINT PRIMARY KEY,
  scheduler_code VARCHAR(64),           -- 'QUARTZ', 'MANUAL', 'WEBHOOK'
  trigger_type_code VARCHAR(32),        -- 'CRON', 'ONCE', 'MANUAL'
  triggered_at TIMESTAMP(3),
  provenance_code VARCHAR(64),
  operation_code VARCHAR(32),           -- 'HARVEST', 'UPDATE', 'BACKFILL'
  status_code VARCHAR(32)               -- 'TRIGGERED', 'COMPLETED', 'FAILED'
);
```

### State Machine
```
TRIGGERED → COMPLETED (all plans succeeded)
         ↘ FAILED (any plan failed)
```

---

## Plan Lifecycle

### 目的
Orchestration blueprint containing expression prototype, config snapshot, window spec, and slicing strategy.

### Table Schema
```sql
CREATE TABLE ing_plan (
  id BIGINT PRIMARY KEY,
  schedule_instance_id BIGINT,          -- FK to ing_schedule_instance
  plan_key VARCHAR(255) UNIQUE,         -- Idempotency key
  provenance_code VARCHAR(64),
  operation_code VARCHAR(32),
  expr_proto_hash VARCHAR(64),          -- SHA256(expr_proto_snapshot)
  expr_proto_snapshot JSON,             -- Expression prototype (no boundaries)
  provenance_config_snapshot JSON,      -- Config snapshot from registry
  window_spec JSON,                     -- WindowSpec (Time/IdRange/etc.)
  slice_strategy_code VARCHAR(32),      -- 'TIME', 'DATE', 'SINGLE'
  status_code VARCHAR(32),              -- 'DRAFT', 'SLICING', 'READY', 'ARCHIVED'
  created_at TIMESTAMP(3)
);
```

### State Machine
```
DRAFT → SLICING → READY → ARCHIVED
```

**State Descriptions**:
| State | Description | Next Transitions |
|-------|-------------|------------------|
| **DRAFT** | Plan created, not yet sliced | SLICING |
| **SLICING** | SlicePlanner generating slices | READY |
| **READY** | All slices + tasks created | ARCHIVED |
| **ARCHIVED** | Execution completed (terminal) | - |

### Idempotency Key
```java
planKey = String.format("%s:%s:%d-%d",
    provenanceCode,
    operationCode,
    windowFrom.toEpochMilli(),
    windowTo.toEpochMilli()
);
// Example: "PUBMED:HARVEST:1704067200000-1706745599999"
```

---

## PlanSlice Lifecycle (CRITICAL - Previously Missing)

### 目的
Parallelism unit representing a time/ID slice of the plan window. Each slice becomes exactly ONE task.

### Table Schema
```sql
CREATE TABLE ing_plan_slice (
  id BIGINT PRIMARY KEY,
  plan_id BIGINT NOT NULL,              -- FK to ing_plan
  slice_no INT NOT NULL,                -- Sequence number (1..N)
  slice_signature_hash VARCHAR(64),     -- SHA256(normalized windowSpec)
  expr_hash VARCHAR(64),                -- SHA256(expr_snapshot)
  expr_snapshot_json TEXT,              -- Localized expression (with boundaries)
  window_spec_json JSON,                -- Narrowed window (e.g., 2024-01-01 to 2024-01-07)
  status_code VARCHAR(32),              -- 'PENDING', 'ASSIGNED', 'FINISHED'
  created_at TIMESTAMP(3),
  UNIQUE KEY uk_plan_slice(plan_id, slice_no)
);
```

### State Machine
```
PENDING → ASSIGNED → FINISHED
```

**State Descriptions**:
| State | Description | Next Transitions |
|-------|-------------|------------------|
| **PENDING** | Slice created, task not yet created | ASSIGNED |
| **ASSIGNED** | Task created and dispatched | FINISHED |
| **FINISHED** | Task execution completed (terminal) | - |

### Slice Signature Hash (Idempotency)
```java
// Normalize window spec (canonical JSON)
String normalizedWindowSpec = canonicalizer.canonicalize(windowSpec);

// Hash for idempotency
sliceSignatureHash = sha256(normalizedWindowSpec);

// Example:
// WindowSpec.Time(from=2024-01-01T00:00:00Z, to=2024-01-07T23:59:59Z)
// → SHA256("Time{from:1704067200000,to:1704671999000}")
```

### Expression Localization
```java
// Plan expression (prototype, no boundaries)
JsonNode exprProto = plan.getExprProtoSnapshot();

// Localize: inject slice window boundaries
ExprSnapshot localizedExpr = expressionLocalizer.localize(
    exprProto,
    slice.getWindowSpec()  // e.g., Time(2024-01-01, 2024-01-07)
);

// Hash localized expression
exprHash = sha256(localizedExpr);

// Store in slice
slice.setExprSnapshotJson(objectMapper.writeValueAsString(localizedExpr));
slice.setExprHash(exprHash);
```

---

## Task Lifecycle

### 目的
Executable unit representing one plan slice. Managed via lease-based distributed execution.

### Table Schema
```sql
CREATE TABLE ing_task (
  id BIGINT PRIMARY KEY,
  slice_id BIGINT UNIQUE,               -- FK to ing_plan_slice (1:1 enforced)
  idempotent_key VARCHAR(255) UNIQUE,   -- Base64Url(SHA256(...))
  provenance_code VARCHAR(64),
  operation_code VARCHAR(32),
  expr_hash VARCHAR(64),                -- Same as slice.expr_hash
  params_json TEXT,                     -- {"sliceNo": N} (NOT API params)
  priority INT,
  status_code VARCHAR(32),              -- 'QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED'
  retry_count INT DEFAULT 0,
  scheduled_at TIMESTAMP(3),
  lease_owner VARCHAR(128),             -- Worker instance ID
  leased_until TIMESTAMP(3),            -- Lease expiration
  created_at TIMESTAMP(3)
);
```

### State Machine
```
QUEUED → RUNNING → SUCCEEDED
              ↘ FAILED (retries exhausted)
              ↻ QUEUED (retry)
```

**State Descriptions**:
| State | Description | Next Transitions |
|-------|-------------|------------------|
| **QUEUED** | Task created, waiting for worker | RUNNING |
| **RUNNING** | Worker acquired lease, executing | SUCCEEDED, FAILED, QUEUED (retry) |
| **SUCCEEDED** | Task completed successfully (terminal) | - |
| **FAILED** | Task failed, retries exhausted (terminal) | - |

### Idempotent Key
```java
String composite = String.format("%s|%s|%s",
    provenanceCode,
    operationCode,
    sliceSignatureHash
);

byte[] hash = sha256(composite);
String idempotentKey = Base64.getUrlEncoder().withoutPadding().encodeToString(hash);

// Example: "x3jK8pL2mN9qR4tV6wY8zA"
```

### Task Parameters
```json
{
  "sliceNo": 5
}
```
**NOTE**: Task params contain ONLY metadata (sliceNo), NOT API parameters. API parameters come from expression rendering at TaskRun level.

### Lease Mechanism
```java
// Acquire lease
UPDATE ing_task
SET lease_owner = ?,
    leased_until = NOW() + INTERVAL ? SECOND,
    status_code = 'RUNNING'
WHERE id = ?
  AND (leased_until IS NULL OR leased_until < NOW())
  AND status_code = 'QUEUED';

// Renew lease (heartbeat)
UPDATE ing_task
SET leased_until = NOW() + INTERVAL ? SECOND
WHERE id = ? AND lease_owner = ?;

// Release lease (on failure or completion)
UPDATE ing_task
SET lease_owner = NULL,
    leased_until = NULL
WHERE id = ? AND lease_owner = ?;
```

---

## TaskRun Lifecycle

### 目的
Represents one execution attempt of a task (supports retries).

### Table Schema
```sql
CREATE TABLE ing_task_run (
  id BIGINT PRIMARY KEY,
  task_id BIGINT NOT NULL,              -- FK to ing_task
  attempt_no INT NOT NULL,              -- 1, 2, 3, ...
  status_code VARCHAR(32),              -- 'PENDING', 'RUNNING', 'PARTIAL', 'SUCCEEDED', 'FAILED'
  started_at TIMESTAMP(3),
  completed_at TIMESTAMP(3),
  checkpoint JSON,                      -- Cursor state for resume
  stats JSON,                           -- {"recordsFetched": 1500, "recordsStored": 1498}
  error_message TEXT,
  UNIQUE KEY uk_task_attempt(task_id, attempt_no)
);
```

### State Machine
```
PENDING → RUNNING → PARTIAL (paginating)
                 → SUCCEEDED
                 → FAILED
```

**State Descriptions**:
| State | Description |
|-------|-------------|
| **PENDING** | Run created, not started |
| **RUNNING** | Actively fetching/processing |
| **PARTIAL** | Paused mid-pagination (checkpoint saved) |
| **SUCCEEDED** | Run completed successfully |
| **FAILED** | Run failed (triggers task retry) |

---

## TaskRunBatch Lifecycle

### 目的
Represents one pagination batch within a task run.

### Table Schema
```sql
CREATE TABLE ing_task_run_batch (
  id BIGINT PRIMARY KEY,
  run_id BIGINT NOT NULL,               -- FK to ing_task_run
  batch_no INT NOT NULL,                -- 1, 2, 3, ...
  before_token VARCHAR(512),            -- Cursor before fetch
  after_token VARCHAR(512),             -- Cursor after fetch
  records_fetched INT,
  records_stored INT,
  idempotent_key VARCHAR(255) UNIQUE,   -- For deduplication
  created_at TIMESTAMP(3),
  UNIQUE KEY uk_run_batch(run_id, batch_no)
);
```

**Pagination Tokens**:
- **before_token**: Cursor/offset before fetching this batch
- **after_token**: Cursor/offset to use for next batch

**Example** (PubMed offset-based):
```
Batch 1: before_token=0,    after_token=1000  (fetched retstart=0)
Batch 2: before_token=1000, after_token=2000  (fetched retstart=1000)
Batch 3: before_token=2000, after_token=3000  (fetched retstart=2000)
```

**Example** (Cursor-based):
```
Batch 1: before_token=null,       after_token="cursor_abc123"
Batch 2: before_token="cursor_abc123", after_token="cursor_def456"
```

---

## Complete Workflow (End-to-End)

### Workflow Diagram
```
1. ScheduleInstance Created (TRIGGERED)
   ↓
2. PlanAggregate Created (DRAFT)
   - Fetch config from registry
   - Fetch expr prototype from registry
   - Calculate window spec
   - Select slicing strategy
   ↓
3. PlanAggregate → SLICING
   - SlicePlanner.slice(context) → List<SlicePlan>
   ↓
4. PlanSliceAggregates Created (PENDING)
   - For each SlicePlan:
     * Localize expression (inject window boundaries)
     * Calculate slice signature hash
     * Create PlanSliceAggregate
   ↓
5. PlanAggregate → READY
   ↓
6. TaskAggregates Created (QUEUED)
   - For each PlanSlice:
     * Create ONE TaskAggregate (1:1 relationship)
     * Calculate idempotent key
     * Emit TaskQueuedEvent
   ↓
7. Outbox Pattern
   - ing_outbox_message created (same transaction)
   - Relay publishes to MQ
   ↓
8. Worker Claims Task (lease acquired)
   - Task → RUNNING
   - PlanSlice → ASSIGNED
   ↓
9. TaskRun Created (attempt #1)
   ↓
10. TaskRunBatches Created (pagination loop)
    - Batch 1: fetch page 1
    - Batch 2: fetch page 2
    - ...
    - Batch N: fetch page N (no more results)
   ↓
11. TaskRun → SUCCEEDED
    - Task → SUCCEEDED
    - PlanSlice → FINISHED
   ↓
12. Plan → ARCHIVED (all slices finished)
    - ScheduleInstance → COMPLETED
```

### Code Flow (Simplified)

**PlanAssemblerImpl.assemble()**:
```java
// 1. Create Plan
PlanAggregate plan = PlanAggregate.create(
    scheduleInstanceId,
    planKey,
    provenanceCode,
    operationCode,
    exprProtoHash,
    exprProtoSnapshot,
    configSnapshot,
    windowSpec,
    sliceStrategyCode,
    PlanStatus.DRAFT
);

// 2. Generate Slices
plan.transitionToSlicing();
SlicePlanningResult sliceResult = slicePlanner.slice(context);
List<PlanSliceAggregate> slices = sliceResult.aggregates();

// 3. Mark Plan as READY
plan.transitionToReady();

// 4. Create Tasks (1:1 with slices)
List<TaskAggregate> tasks = new ArrayList<>();
for (PlanSliceAggregate slice : slices) {
    TaskAggregate task = TaskAggregate.create(
        slice.getId(),
        idempotentKey,
        provenanceCode,
        operationCode,
        slice.getExprHash(),
        paramsJson,  // {"sliceNo": slice.getSliceNo()}
        TaskStatus.QUEUED
    );
    tasks.add(task);

    // Emit domain event
    task.addDomainEvent(new TaskQueuedEvent(task.getId()));
}

// 5. Persist all (transactional)
planRepository.save(plan);
sliceRepository.saveAll(slices);
taskRepository.saveAll(tasks);
outboxRepository.saveAll(extractOutboxMessages(tasks));

return new PlanAssemblyResult(plan, slices, tasks);
```

---

## Outbox Pattern (Task Dispatch)

### 目的
Transactional event publishing for task dispatch to message queue.

### Table Schema
```sql
CREATE TABLE ing_outbox_message (
  id BIGINT PRIMARY KEY,
  aggregate_type VARCHAR(64),           -- 'TASK'
  aggregate_id BIGINT,                  -- task_id
  event_type VARCHAR(128),              -- 'TaskQueuedEvent'
  payload JSON,                         -- Event data
  status_code VARCHAR(32),              -- 'PENDING', 'PUBLISHED', 'FAILED'
  created_at TIMESTAMP(3)
);
```

### Flow
```
1. Task created → TaskQueuedEvent emitted
2. Event saved to ing_outbox_message (same transaction)
3. OutboxRelayScheduler polls PENDING messages
4. Relay publishes to MQ (RabbitMQ/Kafka)
5. Message marked as PUBLISHED
```

---

## Best Practices

### 1. Always Use Plan Key for Idempotency
```java
// Good
String planKey = String.format("%s:%s:%d-%d", ...);
PlanAggregate existingPlan = planRepository.findByPlanKey(planKey);
if (existingPlan != null) {
    return existingPlan;  // Idempotent
}

// Bad
PlanAggregate plan = PlanAggregate.create(...);  // May create duplicate
```

### 2. Respect 1:1 Slice-Task Relationship
```java
// Database enforces this, but application should too
for (PlanSliceAggregate slice : slices) {
    TaskAggregate task = TaskAggregate.create(...);
    // ONE task per slice
}
```

### 3. Use Outbox for Task Dispatch
```java
// Good (transactional)
task.addDomainEvent(new TaskQueuedEvent(task.getId()));
taskRepository.save(task);
outboxRepository.saveAll(extractOutboxMessages(task));

// Bad (non-transactional, may lose messages)
taskRepository.save(task);
mqPublisher.publish(new TaskQueuedMessage(...));  // May fail after save
```

---

## Troubleshooting

### Issue: "Plan key violation"
**Diagnosis**: Duplicate plan creation for same window.
**Fix**: Check `ing_plan.plan_key` uniqueness, implement idempotency check.

### Issue: "Slice without task"
**Diagnosis**: Task creation failed after slice creation.
**Fix**: Check `uk_task_slice` constraint, verify transaction boundaries.

### Issue: "Task lease never released"
**Diagnosis**: Worker crashed without releasing lease.
**Fix**: Implement lease expiration cleanup job.

---

## Summary

**Key Architecture**:
- ✅ 4-layer hierarchy: ScheduleInstance → Plan → PlanSlice → Task (1:1:1)
- ✅ PlanSliceAggregate as parallelism unit
- ✅ Outbox pattern for reliable task dispatch
- ✅ Lease-based distributed execution
- ✅ TaskRunBatch for pagination handling

**Correct State Machines**:
- Plan: DRAFT → SLICING → READY → ARCHIVED
- Slice: PENDING → ASSIGNED → FINISHED
- Task: QUEUED → RUNNING → SUCCEEDED/FAILED
- TaskRun: PENDING → RUNNING → PARTIAL/SUCCEEDED/FAILED

**No**:
- ❌ Plan → Task direct relationship (must go through PlanSlice)
- ❌ Multiple tasks per slice (1:1 enforced by DB)
- ❌ API params in Task.paramsJson (only sliceNo metadata)
- ❌ Wrong state names (CREATED/RUNNING/COMPLETED)
