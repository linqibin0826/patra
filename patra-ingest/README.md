# patra-ingest — Data Ingestion Orchestration Service

> **Data collection orchestrator** that breaks down ingestion jobs into executable tasks and manages their lifecycle.

---

## 📌 Purpose

`patra-ingest` is responsible for:

1. **Plan Orchestration**: Create execution plans from scheduler triggers
2. **Task Generation**: Break plans into atomic tasks (with idempotency)
3. **Window Resolution**: Determine time/volume windows for data collection
4. **Cursor Tracking**: Maintain watermarks for incremental collection
5. **Outbox Relay**: Publish task events reliably via Outbox pattern
6. **Execution Coordination**: Track task status, retries, and lease management

**Key Principle**: Ensure **at-least-once delivery** with **idempotent task execution**.

---

## 🏗️ Module Structure

```
patra-ingest/
├─ patra-ingest-api/                # External contracts
│  └─ src/main/java/.../api/
│     └─ (future: task worker APIs)
│
├─ patra-ingest-domain/             # Pure Java domain model
│  └─ src/main/java/.../domain/
│     ├─ model/
│     │  ├─ aggregate/              # Core aggregates
│     │  │  ├─ PlanAggregate.java       # Plan blueprint + state machine
│     │  │  ├─ TaskAggregate.java       # Task with lease + execution timeline
│     │  │  ├─ PlanSliceAggregate.java  # Plan slice (intermediate grouping)
│     │  │  └─ ScheduleInstanceAggregate.java  # Scheduler run tracking
│     │  ├─ vo/                     # Value objects
│     │  │  ├─ WindowSpec.java          # Window specification (TIME/CURSOR/...)
│     │  │  ├─ Batch.java               # Batch definition
│     │  │  ├─ CursorWatermark.java     # Watermark tracking
│     │  │  └─ ExecutionContext.java    # Task execution context
│     │  ├─ entity/                 # Domain entities
│     │  │  └─ OutboxMessage.java       # Outbox message entity
│     │  ├─ enums/                  # Domain enums
│     │  │  ├─ PlanStatus.java          # DRAFT/SLICING/READY/COMPLETED/FAILED
│     │  │  ├─ TaskStatus.java          # QUEUED/RUNNING/SUCCEEDED/FAILED/...
│     │  │  └─ OperationCode.java       # HARVEST/UPDATE/COMPENSATION
│     │  └─ snapshot/               # Config snapshots
│     │     └─ ProvenanceConfigSnapshot.java
│     ├─ port/                      # Repository ports
│     │  ├─ PlanRepository.java
│     │  ├─ TaskRepository.java
│     │  ├─ CursorRepository.java
│     │  ├─ OutboxRepository.java
│     │  └─ PatraRegistryPort.java      # External service port
│     ├─ event/                     # Domain events
│     │  └─ TaskQueuedEvent.java
│     ├─ policy/                    # Domain policies
│     ├─ exception/                 # Domain exceptions
│     └─ messaging/                 # Messaging contracts
│
├─ patra-ingest-app/                # Application layer (orchestration)
│  └─ src/main/java/.../app/
│     └─ usecase/
│        ├─ plan/                   # Plan ingestion use case
│        │  ├─ PlanIngestionOrchestrator.java    # Main orchestrator
│        │  ├─ PlanIngestionCommand.java         # Input command
│        │  ├─ PlanIngestionResult.java          # Output result
│        │  ├─ assembler/                        # Plan assembly logic
│        │  │  └─ PlanAssembler.java
│        │  ├─ expression/                       # Expression building
│        │  │  └─ PlanExpressionBuilder.java
│        │  ├─ window/                           # Window resolution
│        │  │  └─ PlanningWindowResolver.java
│        │  ├─ validator/                        # Pre-validation
│        │  │  └─ PlannerValidator.java
│        │  └─ publisher/                        # Event publishing
│        │     └─ TaskOutboxPublisher.java
│        └─ relay/                  # Outbox relay use case
│           └─ OutboxRelayOrchestrator.java
│
├─ patra-ingest-infra/              # Infrastructure layer
│  └─ src/main/java/.../infra/
│     ├─ persistence/
│     │  ├─ entity/                 # MyBatis-Plus DOs
│     │  │  ├─ IngestPlanDO.java
│     │  │  ├─ IngestTaskDO.java
│     │  │  ├─ IngestCursorDO.java
│     │  │  └─ IngestOutboxMessageDO.java
│     │  ├─ mapper/                 # MyBatis mappers
│     │  ├─ converter/              # DO ↔ Domain converters
│     │  └─ repository/             # Repository implementations
│     │     ├─ PlanRepositoryMpImpl.java
│     │     ├─ TaskRepositoryMpImpl.java
│     │     ├─ CursorRepositoryMpImpl.java
│     │     └─ OutboxRepositoryMpImpl.java
│     └─ rpc/                       # External service adapters
│        └─ PatraRegistryPortImpl.java  # Calls patra-registry via Feign
│
├─ patra-ingest-adapter/            # Adapter layer
│  └─ src/main/java/.../adapter/
│     ├─ inbound/
│     │  ├─ scheduler/              # Job schedulers
│     │  │  └─ PlanScheduler.java       # Cron-triggered planning
│     │  └─ mq/                     # MQ listeners (future)
│     └─ config/                    # Error mapping, tracing
│
└─ patra-ingest-boot/               # Executable module
   └─ src/main/java/.../
      └─ PatraIngestApplication.java
```

---

## 🔑 Key Domain Concepts

### 1. Plan (Aggregate Root)

**Definition**: Blueprint for a data collection job, containing window specification, expression snapshot, and configuration snapshot.

**State Machine**:
```
DRAFT → SLICING → READY → COMPLETED
                      ↓
                   PARTIAL → FAILED
```

**Key Attributes**:
- `planKey` (String): Idempotency key = hash(provenance + operation + window + strategy)
- `provenanceCode` (String): Source code (e.g., `"pubmed"`)
- `operationCode` (OperationCode): HARVEST/UPDATE/COMPENSATION
- `windowSpec` (WindowSpec): Window boundary (TIME/CURSOR/VOLUME/SINGLE)
- `sliceStrategyCode` (String): How to break plan into slices (e.g., `"TIME"`, `"SINGLE"`)
- `exprProtoSnapshotJson` (String): Captured expression prototype
- `provenanceConfigSnapshotJson` (String): Captured config snapshot

**File**: [`PlanAggregate.java`](patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/aggregate/PlanAggregate.java:1)

### 2. Task (Aggregate Root)

**Definition**: Atomic work unit for data collection (e.g., fetch PubMed records 1-1000).

**State Machine**:
```
QUEUED → RUNNING → SUCCEEDED
            ↓
         FAILED → (retry) → QUEUED
            ↓
       CANCELLED
```

**Key Attributes**:
- `idempotentKey` (String): Business idempotency key
- `provenanceCode` (String): Source code
- `operationCode` (String): Operation type
- `paramsJson` (String): Task parameters (JSON)
- `priority` (Integer): Execution priority
- `scheduledAt` (Instant): When to execute
- `leaseInfo` (LeaseInfo): Lease ownership (owner, leasedUntil)
- `executionTimeline` (ExecutionTimeline): Start/finish timestamps
- `retryCount` (Integer): Number of retries attempted

**File**: [`TaskAggregate.java`](patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/aggregate/TaskAggregate.java:1)

### 3. WindowSpec (Sealed Interface)

**Definition**: Specification for collection window boundaries.

**Strategies**:
```java
public sealed interface WindowSpec {
    record Time(Instant from, Instant to) implements WindowSpec {}
    record IdRange(Long minId, Long maxId) implements WindowSpec {}
    record CursorLandmark(String cursorValue) implements WindowSpec {}
    record VolumeBudget(Integer maxRecords) implements WindowSpec {}
    record Single() implements WindowSpec {}  // No windowing
}
```

**File**: [`WindowSpec.java`](patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/vo/WindowSpec.java)

### 4. Cursor Watermark

**Definition**: Tracks progress of incremental collection (high-water mark).

**Types**:
- **Global Time Watermark**: Latest `collectedUntil` timestamp across all tasks
- **Cursor Value Watermark**: Last pagination cursor (for cursor-based APIs)

**File**: [`CursorWatermark.java`](patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/vo/CursorWatermark.java)

### 5. Outbox Pattern

**Components**:
1. **TaskQueuedEvent**: Domain event raised when task is created
2. **OutboxMessage**: Persistent event record (JSON payload)
3. **OutboxRelayOrchestrator**: Polls outbox table, publishes to MQ, marks as sent

**Guarantees**:
- **At-least-once delivery** (same transaction as task persistence)
- **Ordering** (via sequence number)
- **Idempotency** (consumer must deduplicate)

**File**: [`OutboxMessage.java`](patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/entity/OutboxMessage.java)

---

## 🔄 Plan Ingestion Flow

### High-Level Sequence

```
1. Scheduler triggers (cron or manual)
   ↓
2. PlanIngestionOrchestrator.ingestPlan(command)
   ↓
3. Phase 1: Persist schedule instance + load provenance config
   ↓
4. Phase 2: Query cursor watermark + resolve planning window
   ↓
5. Phase 3: Build plan expression (uncompiled snapshot)
   ↓
6. Phase 4: Pre-validation (window sanity, backpressure, capacity)
   ↓
7. Phase 5: Assemble plan/slices/tasks (with idempotency check)
   ↓
8. Phase 6: Persist plan → slices → tasks (in transaction)
   ↓
9. Phase 7: Collect TaskQueuedEvents → publish to Outbox
   ↓
10. Outbox relay picks up messages → publishes to MQ
   ↓
11. Task workers consume from MQ → execute → update task status
```

### Code Entry Point

[`PlanIngestionOrchestrator.ingestPlan()`](patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/plan/PlanIngestionOrchestrator.java:133)

### Key Phases Explained

#### Phase 1: Load Configuration

```java
// Persist schedule instance (idempotent)
ScheduleInstanceAggregate schedule = scheduleInstanceRepository.saveOrUpdateInstance(...);

// Fetch provenance config snapshot from registry
ProvenanceConfigSnapshot configSnapshot = patraRegistryPort.fetchConfig(
    provenanceCode, operationCode
);
```

**Purpose**: Capture config at plan creation time (immutable snapshot).

#### Phase 2: Window Resolution

```java
// Query latest watermark
Instant cursorWatermark = cursorRepository.findLatestGlobalTimeWatermark(...);

// Resolve window based on trigger params + config + cursor
PlannerWindow window = planningWindowResolver.resolveWindow(
    triggerNorm, configSnapshot, cursorWatermark, Instant.now()
);
```

**Example**:
- Trigger says: `windowFrom=2025-01-01`, `windowTo=2025-01-10`
- Cursor watermark: `2025-01-05`
- Resolved window: `[2025-01-05, 2025-01-10)` (start from cursor)

#### Phase 3: Expression Building

```java
// Build plan expression descriptor (hash + JSON snapshot)
PlanExpressionDescriptor expressionDescriptor = planExpressionBuilder.build(
    triggerNorm, configSnapshot
);
```

**Purpose**: Capture expression prototype before compilation (stored in plan).

#### Phase 4: Pre-Validation

```java
// Count queued tasks (backpressure check)
long queuedTasks = taskRepository.countQueuedTasks(provenanceCode, operationCode);

// Validate window, capacity, queue pressure
plannerValidator.validateBeforeAssemble(
    triggerNorm, configSnapshot, window, queuedTasks
);
```

**Checks**:
- Window not empty
- Window not too large (max range)
- Queue capacity not exceeded

#### Phase 5: Plan Assembly

```java
// Assemble plan/slices/tasks (in-memory, not persisted yet)
PlanAssemblyResult assembly = planAssembler.assemble(assemblyRequest);

// Check idempotency: does planKey already exist?
PlanAggregate existingPlan = planRepository.findByPlanKey(draftPlan.getPlanKey());
if (existingPlan != null) {
    // Reuse existing plan, retry failed tasks
    ...
}
```

**Idempotency**:
- `planKey` = hash(provenance + operation + window + strategy)
- If duplicate, skip creation and retry failed tasks

#### Phase 6: Persistence

```java
// Persist plan
PlanAggregate persistedPlan = planRepository.save(draftPlan);

// Persist slices (bind to plan)
List<PlanSliceAggregate> persistedSlices = planSliceRepository.saveAll(slices);

// Persist tasks (bind to plan + slices)
List<TaskAggregate> persistedTasks = taskRepository.saveAll(tasks);
```

**Transaction**: All 3 steps are in the same transaction.

#### Phase 7: Event Publishing

```java
// Collect domain events from tasks
List<TaskQueuedEvent> queuedEvents = collectQueuedEvents(persistedTasks);

// Publish to Outbox (in same transaction)
taskOutboxPublisher.publish(queuedEvents, persistedPlan, schedule);
```

**Outbox**: Converts events → `OutboxMessage` → persists to `ingest_outbox_message` table.

---

## 🔌 Cross-Module Integration

### Expression Compiler Integration (Adapter Binding)

`patra-ingest` compiles expressions at execution time and binds the compiled output (provider‑named params) directly into provider request models. No manual string concatenation.

Flow:
- `ExecutionContextLoader` restores snapshots (Task → Slice → Plan) and compiles the expression via `ExpressionCompilerPort`.
- The compiled result provides:
  - `compiledQuery` — aggregated boolean query string (bridged via `std_key=query`)
  - `compiledParams` — provider‑named params map (e.g., PubMed `mindate/maxdate/datetype`, Crossref `query/filter`)
- Provider adapters/assemblers read only from `compiledParams` (and `compiledQuery` when appropriate).

Example (PubMed count planning):

```java
// ExecutionContextLoaderImpl.loadContext(...) builds the context
return new ExecutionContext(
    taskId, runId, task.getProvenanceCode(), task.getOperationCode(),
    configSnapshot,
    task.getExprHash(),
    compilationResult.query(),        // compiledQuery (bridged via std_key=query)
    compilationResult.params(),       // compiledParams (provider-named)
    compilationResult.normalizedExpression(),
    windowSpec);

// PubmedBatchPlanner uses compiled outputs
int total = searchPort.estimateCount(compiledQuery, compiledParams, configSnapshot);

// Infra adapter builds request from provider-named params only
ESearchRequest request = PubMedESearchRequestAssembler.buildCount(compiledParams);
```

Rules of engagement:
- Do not reconstruct `query`/`filter` in adapters — always use compiler output.
- MULTI std_keys use join transforms by default. Repeated params require enabling `expr.multi.repeat-enabled=true` (not recommended by default).
- Respect STRICT mode in prod (`expr.strict=true`) to catch incomplete seed configs early.

### Calling patra-registry

**Port Interface**: [`PatraRegistryPort`](patra-ingest-domain/src/main/java/com/patra/ingest/domain/port/PatraRegistryPort.java)

**Implementation**: [`PatraRegistryPortImpl`](patra-ingest-infra/src/main/java/com/patra/ingest/infra/rpc/PatraRegistryPortImpl.java)

**Example**:
```java
@Component
@RequiredArgsConstructor
public class PatraRegistryPortImpl implements PatraRegistryPort {

    private final ProvenanceClient provenanceClient;  // Feign client

    @Override
    public ProvenanceConfigSnapshot fetchConfig(ProvenanceCode code, OperationCode operation) {
        ProvenanceConfigResp resp = provenanceClient.getConfiguration(
            code,
            operation.getCode(),
            Instant.now()
        );
        return convertToSnapshot(resp);
    }
}
```

**Dependency**: `patra-registry-api` (Feign client interface).

---

## 🛠️ How to Extend

### Adding a New Slicing Strategy

**Example**: Add `ID_RANGE` slicing (split by ID ranges instead of time).

#### Step 1: Define Slicer

```java
// app/usecase/plan/slicer/IdRangeSlicingStrategy.java
@Component
public class IdRangeSlicingStrategy implements SlicingStrategy {

    @Override
    public boolean supports(String strategyCode) {
        return "ID_RANGE".equalsIgnoreCase(strategyCode);
    }

    @Override
    public List<SliceSpec> slice(PlannerWindow window, SliceParamsJson params) {
        // Parse window as WindowSpec.IdRange
        // Split into ranges: [1, 1000], [1001, 2000], ...
        // Return list of SliceSpec
    }
}
```

#### Step 2: Register in PlanAssembler

```java
// PlanAssembler.java
private final List<SlicingStrategy> slicingStrategies;  // Auto-wired

public PlanAssemblyResult assemble(PlanAssemblyRequest request) {
    SlicingStrategy strategy = slicingStrategies.stream()
        .filter(s -> s.supports(request.sliceStrategyCode()))
        .findFirst()
        .orElseThrow(() -> new PlanAssemblyException("Unsupported strategy: " + request.sliceStrategyCode()));

    List<SliceSpec> slices = strategy.slice(request.window(), request.sliceParams());
    // ...
}
```

#### Step 3: Update WindowSpec

```java
// WindowSpec.java
public sealed interface WindowSpec {
    record Time(Instant from, Instant to) implements WindowSpec {}
    record IdRange(Long minId, Long maxId) implements WindowSpec {}  // Use this
    // ...
}
```

### Adding a New Task Status

**Example**: Add `SKIPPED` status for tasks that don't need execution.

#### Step 1: Add to Enum

```java
// domain/model/enums/TaskStatus.java
public enum TaskStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    PARTIAL,
    CURSOR_PENDING,
    CANCELLED,
    SKIPPED  // NEW
}
```

#### Step 2: Add Aggregate Method

```java
// TaskAggregate.java
public void markSkipped(String reason) {
    this.status = TaskStatus.SKIPPED;
    this.lastErrorMsg = "Skipped: " + reason;
}
```

#### Step 3: Update State Machine Logic

```java
// Update retry logic to skip SKIPPED tasks
private boolean shouldRetry(TaskAggregate task) {
    TaskStatus status = task.getStatus();
    return status == TaskStatus.FAILED || status == TaskStatus.CANCELLED;
    // SKIPPED tasks are NOT retried
}
```

---

## 🗄️ Database Schema Overview

### Tables

| Table | Purpose |
|-------|---------|
| `ingest_schedule_instance` | Scheduler run tracking (idempotency) |
| `ingest_plan` | Plan blueprints |
| `ingest_plan_slice` | Plan slices (intermediate grouping) |
| `ingest_task` | Atomic tasks |
| `ingest_cursor` | Watermark tracking (global + slice-level) |
| `ingest_outbox_message` | Outbox pattern event queue |

**Key Relationships**:
- `ingest_plan.schedule_instance_id` → `ingest_schedule_instance.id`
- `ingest_plan_slice.plan_id` → `ingest_plan.id`
- `ingest_task.plan_id` → `ingest_plan.id`
- `ingest_task.slice_id` → `ingest_plan_slice.id`

**Indexes**:
- `idx_plan_key` on `ingest_plan(plan_key)` (idempotency)
- `idx_task_idempotent_key` on `ingest_task(idempotent_key)` (idempotency)
- `idx_task_status_scheduled` on `ingest_task(status, scheduled_at)` (worker polling)
- `idx_outbox_status_seq` on `ingest_outbox_message(status, seq)` (relay polling)

---

## 🧪 Testing

### Unit Tests (Domain)

```bash
mvn test -pl patra-ingest-domain
```

**Focus**: Aggregate state machines, window resolution logic.

### Integration Tests (Orchestrator)

```bash
mvn verify -pl patra-ingest-app
```

**Focus**: End-to-end plan ingestion, idempotency checks.

### Repository Tests (Infra)

```bash
mvn verify -pl patra-ingest-infra
```

**Focus**: MyBatis queries, cursor watermark updates.

---

## 📊 Observability

### Logs

- **INFO**: Major milestones (e.g., "Plan ingestion success, planId=123, taskCount=50")
- **DEBUG**: Diagnostic details (e.g., "Window resolved: [2025-01-01, 2025-01-10)")
- **ERROR**: Failures (e.g., "Plan assembly failed: WINDOW_INVALID")

### Metrics

**Dependencies**: `spring-boot-starter-actuator` is included in `patra-ingest-boot` for Micrometer metrics support.

**Outbox Metrics** (published by `OutboxMetrics`):
- `papertrace.outbox.publish.total` (Counter) — Total publish attempts with `aggregateType`, `opType`, `status` tags
- `papertrace.outbox.publish.duration` (Timer) — Publish operation duration
- `papertrace.outbox.publish.batch.size` (DistributionSummary) — Batch size distribution

**Planned Metrics**:
- `plan.ingestion.duration` (histogram)
- `task.queue.size` (gauge)
- `cursor.watermark.lag` (gauge) — Time between now and watermark
- `outbox.relay.lag` (gauge) — Number of pending outbox messages

**Access metrics**:
```bash
# Health check
curl http://localhost:8082/actuator/health

# View all metrics
curl http://localhost:8082/actuator/metrics

# View specific outbox metrics
curl http://localhost:8082/actuator/metrics/papertrace.outbox.publish.total
```

---

## 🚀 Running Locally

```bash
# Start MySQL + MQ
docker-compose up -d

# Run migrations
# ...

# Start service
cd patra-ingest/patra-ingest-boot
mvn spring-boot:run
```

**Default Port**: 8082

---

## 🔗 Related Documentation

- [Main README](../README.md)
- [Architecture Guide](../docs/ARCHITECTURE.md)
- [Development Guide](../docs/DEV-GUIDE.md)
- [patra-registry README](../patra-registry/README.md)

---

**Last Updated**: 2025-01-14
