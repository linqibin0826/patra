# Plan Ingestion Use Case

This package contains the **core orchestration logic** for ingesting collection plans — the most complex use case in `patra-ingest`.

---

## Package Structure

```
plan/
├─ PlanIngestionOrchestrator.java    # Main orchestrator (entry point)
├─ PlanIngestionUseCase.java         # Interface contract
├─ command/
│  └─ PlanIngestionCommand.java      # Input command DTO
├─ dto/
│  ├─ PlanIngestionResult.java       # Output result DTO
│  └─ PlanAssemblyResult.java        # Assembly result (internal)
├─ assembler/
│  ├─ PlanAssembler.java             # Assembles plan/slices/tasks
│  ├─ PlanAssemblyRequest.java       # Assembly input
│  └─ ...                            # Slicer strategies
├─ expression/
│  ├─ PlanExpressionBuilder.java     # Builds plan expression
│  └─ PlanExpressionDescriptor.java  # Expression descriptor VO
├─ window/
│  └─ PlanningWindowResolver.java    # Resolves execution window
├─ validator/
│  └─ PlannerValidator.java          # Pre-validation logic
└─ publisher/
   └─ TaskOutboxPublisher.java       # Publishes task events to outbox
```

---

## Orchestration Flow

### Entry Point

**Class**: `PlanIngestionOrchestrator`
**Method**: `ingestPlan(PlanIngestionCommand) → PlanIngestionResult`
**Transaction**: `@Transactional` (entire flow is atomic)

### 7-Phase Flow

```
1. Schedule Instance + Config Snapshot
   ↓
2. Cursor Watermark + Window Resolution
   ↓
3. Expression Building
   ↓
4. Pre-Validation
   ↓
5. Plan Assembly (with idempotency check)
   ↓
6. Persistence (Plan → Slices → Tasks)
   ↓
7. Event Publishing (TaskQueuedEvent → Outbox)
```

---

## Phase Details

### Phase 1: Schedule Instance + Config Snapshot

**目的**: Initialize scheduler tracking and capture immutable config.

**Code**:
```java
// Persist schedule instance (idempotent by scheduler_job_id + scheduler_log_id)
ScheduleInstanceAggregate schedule = persistScheduleInstanceSafely(request);

// Fetch provenance config from registry
ProvenanceConfigSnapshot configSnapshot = patraRegistryPort.fetchConfig(
    provenanceCode, operationCode
);

// Build trigger norm (value object)
PlanTriggerNorm norm = buildTriggerNorm(schedule, request);
```

**Key Points**:
- Schedule instance tracks "who triggered" (manual vs. cron)
- Config snapshot is **immutable** — captured at plan creation time
- `PlanTriggerNorm` = normalized representation of trigger inputs

---

### Phase 2: Cursor Watermark + Window Resolution

**目的**: Determine execution window based on cursor and trigger params.

**Code**:
```java
// Query latest watermark (for incremental collection)
Instant cursorWatermark = cursorRepository.findLatestGlobalTimeWatermark(
    provenanceCode, operationCode
);

// Resolve planning window
PlannerWindow window = planningWindowResolver.resolveWindow(
    norm, configSnapshot, cursorWatermark, Instant.now()
);
```

**Resolution Logic**:
- If trigger specifies `windowFrom`/`windowTo`: Use those
- If cursor exists: Start from cursor (even if trigger says otherwise)
- Apply `WindowOffsetConfig` defaults if no explicit bounds

**Example**:
```
Trigger:   windowFrom=2025-01-01, windowTo=2025-01-10
Cursor:    2025-01-05 (already collected up to here)
Resolved:  [2025-01-05, 2025-01-10)  ← Start from cursor
```

**Components**:
- `PlanningWindowResolver`: Main resolution logic
- `CursorRepository`: Queries watermarks

---

### Phase 3: Expression Building

**目的**: Build plan-level expression descriptor (uncompiled snapshot).

**Code**:
```java
PlanExpressionDescriptor expressionDescriptor = planExpressionBuilder.build(
    norm, configSnapshot
);

// Descriptor contains:
// - hash: SHA-256 of expression prototype
// - jsonSnapshot: JSON serialization (for storage)
```

**Why Uncompiled?**
- Expression compilation happens **per task** (not per plan)
- Plan stores **prototype** (template)
- Tasks compile with actual runtime params (e.g., concrete date ranges)

**Components**:
- `PlanExpressionBuilder`: Builds descriptor from config
- `patra-expr-kernel`: (Separate module) Handles compilation later

---

### Phase 4: Pre-Validation

**目的**: Fail fast before expensive assembly.

**Code**:
```java
// Count queued tasks (backpressure check)
long queuedTasks = taskRepository.countQueuedTasks(provenanceCode, operationCode);

// Validate
plannerValidator.validateBeforeAssemble(
    norm, configSnapshot, window, queuedTasks
);
```

**Checks**:
| Validation | Reason | Exception |
|------------|--------|-----------|
| Window not empty | No data to collect | `PlanValidationException.WINDOW_INVALID` |
| Window not too large | Prevent runaway plans | `PlanValidationException.WINDOW_TOO_LARGE` |
| Queue capacity OK | Backpressure control | `PlanValidationException.CAPACITY_EXCEEDED` |

**Components**:
- `PlannerValidator`: Validates window, capacity, constraints

---

### Phase 5: Plan Assembly

**目的**: Assemble in-memory plan/slices/tasks (not persisted yet).

**Code**:
```java
PlanAssemblyRequest assemblyRequest = new PlanAssemblyRequest(
    norm, window, configSnapshot, expressionDescriptor
);

PlanAssemblyResult assembly = planAssembler.assemble(assemblyRequest);

// Check idempotency
PlanAggregate existingPlan = planRepository.findByPlanKey(draftPlan.getPlanKey());
if (existingPlan != null) {
    // Idempotency hit: reuse existing plan, retry failed tasks
    return compensationFlow(existingPlan);
}
```

**Assembly Steps**:
1. **Generate planKey**: `hash(provenance + operation + window + strategy)`
2. **Apply slicing strategy**: Break window into slices (e.g., daily slices)
3. **Generate tasks**: One task per slice (or batch within slice)
4. **Assign IDs**: Temporary slice sequence numbers (replaced after persistence)

**Idempotency**:
- If `planKey` already exists: Skip creation, retry failed/cancelled tasks
- This allows safe retries from scheduler without duplicating work

**Components**:
- `PlanAssembler`: Main assembly logic
- `SlicingStrategy` implementations: TIME, DATE, SINGLE, etc.

---

### Phase 6: Persistence

**目的**: Persist plan → slices → tasks in correct order.

**Code**:
```java
// 1. Persist plan
PlanAggregate persistedPlan = planRepository.save(draftPlan);

// 2. Persist slices (bind to plan ID)
slices.forEach(slice -> slice.bindPlan(persistedPlan.getId()));
List<PlanSliceAggregate> persistedSlices = planSliceRepository.saveAll(slices);

// 3. Persist tasks (bind to plan + slice IDs)
tasks.forEach(task -> task.bindPlanAndSlice(persistedPlan.getId(), sliceId));
List<TaskAggregate> persistedTasks = taskRepository.saveAll(tasks);
```

**Important**:
- **Order matters**: Plan → Slices → Tasks (FK constraints)
- **Same transaction**: All-or-nothing atomicity
- **ID assignment**: Aggregates get IDs after persistence

**Components**:
- `PlanRepository`, `PlanSliceRepository`, `TaskRepository`

---

### Phase 7: Event Publishing

**目的**: Publish task queued events via Outbox pattern.

**Code**:
```java
// Collect domain events from tasks
List<TaskQueuedEvent> queuedEvents = collectQueuedEvents(persistedTasks);

// Publish to outbox (in same transaction)
taskOutboxPublisher.publish(queuedEvents, persistedPlan, schedule);
```

**Outbox Flow**:
1. Convert `TaskQueuedEvent` → `OutboxMessage` entity
2. Persist `OutboxMessage` to `ingest_outbox_message` table
3. **Relay** (separate job) polls outbox and publishes to MQ
4. Task workers consume from MQ

**Guarantees**:
- **At-least-once delivery** (outbox in same transaction as tasks)
- **Ordering** (via sequence number)
- **No message loss** (durable storage before MQ)

**Components**:
- `TaskOutboxPublisher`: Converts events → outbox messages
- `OutboxRelayOrchestrator`: (Separate use case) Polls and relays

---

## Key Collaborators

### Internal (App Layer)

| Component | Responsibility |
|-----------|----------------|
| **PlanAssembler** | Generate plan/slices/tasks from window + config |
| **PlanningWindowResolver** | Compute execution window (cursor-aware) |
| **PlanExpressionBuilder** | Build expression descriptor (uncompiled) |
| **PlannerValidator** | Pre-validate window, capacity, constraints |
| **TaskOutboxPublisher** | Publish task events to outbox |

### External (Ports)

| Port | Responsibility | Implementation |
|------|----------------|----------------|
| **PatraRegistryPort** | Fetch provenance config snapshot | `PatraRegistryPortImpl` (Feign) |
| **CursorRepository** | Query/update watermarks | `CursorRepositoryMpImpl` |
| **PlanRepository** | CRUD for plans | `PlanRepositoryMpImpl` |
| **TaskRepository** | CRUD for tasks | `TaskRepositoryMpImpl` |
| **ScheduleInstanceRepository** | Track scheduler runs | `ScheduleInstanceRepositoryMpImpl` |

---

## Error Handling

### Exception Mapping

| Exception | Phase | Reason | HTTP Status |
|-----------|-------|--------|-------------|
| `PlanValidationException` | Phase 4 | Invalid window/capacity | 400 Bad Request |
| `PlanAssemblyException` | Phase 5 | Empty result/slicing failed | 500 Internal Error |
| `PlanPersistenceException` | Phase 6 | DB constraint violation | 500 Internal Error |

### Retry Strategy

**Transient failures** (DB deadlock, network timeout):
- Scheduler **retries entire flow** on next cron trigger
- Idempotency via `planKey` prevents duplicate work

**Permanent failures** (invalid config, missing provenance):
- **No retry** — fix root cause first
- Schedule instance marked as `FAILED`

---

## Testing Strategy

### Unit Tests

**Focus**: Individual components (assembler, validator, window resolver)

```java
@Test
void testWindowResolverWithCursor() {
    // Given
    Instant cursor = Instant.parse("2025-01-05T00:00:00Z");
    PlanTriggerNorm norm = new PlanTriggerNorm(..., windowFrom, windowTo);

    // When
    PlannerWindow window = resolver.resolveWindow(norm, config, cursor, now);

    // Then
    assertEquals(cursor, window.from());  // Start from cursor
}
```

### Integration Tests

**Focus**: End-to-end orchestration (with in-memory DB)

```java
@SpringBootTest
@Transactional
class PlanIngestionOrchestratorTest {

    @Test
    void testIngestPlanSuccess() {
        // Given
        PlanIngestionCommand command = new PlanIngestionCommand(...);

        // When
        PlanIngestionResult result = orchestrator.ingestPlan(command);

        // Then
        assertNotNull(result.planId());
        assertEquals(10, result.taskCount());  // Expected tasks
    }
}
```

---

## Performance Considerations

### Batch Operations

- **Slice persistence**: `saveAll()` uses batch insert
- **Task persistence**: `saveAll()` uses batch insert
- Avoid N+1 queries (load config once, reuse for all tasks)

### Backpressure Control

```java
// Limit queued tasks per provenance+operation
long queuedTasks = taskRepository.countQueuedTasks(provenance, operation);
if (queuedTasks > MAX_QUEUED_TASKS) {
    throw new PlanValidationException("Queue capacity exceeded");
}
```

### Transaction Scope

- **Keep transactions short**: Single `@Transactional` on orchestrator method
- **Avoid nested transactions**: All repository calls inherit outer transaction
- **Rollback on exception**: Spring automatically rolls back on unchecked exceptions

---

## Observability

### Logging

```java
log.info("plan-ingest start, provenance={}, op={}",
    provenanceCode, operationCode);

log.debug("plan-ingest window resolved=[{}, {})",
    window.from(), window.to());

log.info("plan-ingest success, planId={}, taskCount={}",
    planId, taskCount);
```

### Metrics (Planned)

- `plan_ingestion_duration_seconds` (histogram)
- `plan_ingestion_task_count` (histogram)
- `plan_assembly_failures_total` (counter)

---

## Extension Points

### Adding New Slicing Strategy

**Interface**: `SlicingStrategy`

```java
@Component
public class MySlicingStrategy implements SlicingStrategy {

    @Override
    public boolean supports(String strategyCode) {
        return "MY_STRATEGY".equals(strategyCode);
    }

    @Override
    public List<SliceSpec> slice(PlannerWindow window, SliceParamsJson params) {
        // Custom slicing logic
    }
}
```

**Registration**: Auto-wired by Spring into `PlanAssembler`.

---

**See Also**:
- [patra-ingest README](../../../../../README.md)
- [Domain Model Package](../../../domain/model/README.md)
- [Development Guide](../../../../../../../../docs/DEV-GUIDE.md)
