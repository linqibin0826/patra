# patra-ingest Data Ingestion Flow

> This document illustrates the complete business flow of patra-ingest service from Plan (Planning) → Relay (Relaying) → Execution.
>
> **Code Version**: 2025-01-19
>
> **Related Docs**: [patra-ingest README](../../patra-ingest/README.md)

---

## 1. Overview Flow

Complete data flow from scheduler trigger to task execution completion.

```mermaid
flowchart TD
    %% Style definitions
    classDef phaseClass fill:#e1f5ff,stroke:#01579b,stroke-width:2px
    classDef dbClass fill:#fff3e0,stroke:#e65100,stroke-width:2px
    classDef mqClass fill:#f3e5f5,stroke:#4a148c,stroke-width:2px

    %% Trigger
    Scheduler[("⏰ Scheduler<br/>(Cron Job)")]

    %% Plan Phase
    subgraph Plan["📋 Plan Phase (PlanIngestionOrchestrator)"]
        P1["1. Load Config Snapshot<br/>(ScheduleInstance + ProvenanceConfig)"]
        P2["2. Resolve Window<br/>(Cursor Watermark + Window)"]
        P3["3. Build Expression<br/>(Expression Descriptor)"]
        P4["4. Pre-validation<br/>(Window/Capacity/Backpressure)"]
        P5["5. Assemble Plan<br/>(Plan/Slices/Tasks)<br/>with Idempotency Check"]
        P6["6. Persist<br/>(Plan → Slices → Tasks → Outbox)"]

        P1 --> P2 --> P3 --> P4 --> P5 --> P6
    end

    %% Relay Phase
    subgraph Relay["🔄 Relay Phase (OutboxRelayOrchestrator)"]
        R1["1. Build Relay Plan<br/>(RelayPlan)"]
        R2["2. Fetch Pending Messages<br/>(Lease-based Fetch)"]
        R3["3. Publish to MQ<br/>(Publish Messages)"]
        R4["4. Mark as Sent<br/>(Update Status)"]

        R1 --> R2 --> R3 --> R4
    end

    %% Execution Phase
    subgraph Execution["⚙️ Execution Phase (TaskExecutionUseCase)"]
        E1["Phase 0: Prepare<br/>(Idempotency Check + Lease + Load Context)"]
        E2["Phase 1: Execute<br/>(Batch Planning + Batch Execution)"]
        E3["Phase 2: Complete<br/>(Update Status + Advance Cursor + Release Lease)"]

        E1 --> E2 --> E2
        E2 --> E3
    end

    %% Database Tables
    DB_Plan[("💾 ingest_plan")]:::dbClass
    DB_Slice[("💾 ingest_plan_slice")]:::dbClass
    DB_Task[("💾 ingest_task")]:::dbClass
    DB_Outbox[("💾 ingest_outbox_message")]:::dbClass
    DB_Cursor[("💾 ingest_cursor")]:::dbClass

    %% Message Queue
    MQ[("📨 Message Queue<br/>(RabbitMQ/RocketMQ)")]:::mqClass

    %% External Services
    Registry["🗂️ patra-registry<br/>(Provenance Config)"]
    ExternalAPI["🌐 External API<br/>(PubMed/EPMC/Crossref)"]

    %% Connections
    Scheduler -->|Trigger| Plan

    Plan -->|Write| DB_Plan
    Plan -->|Write| DB_Slice
    Plan -->|Write| DB_Task
    Plan -->|Write<br/>Same Txn| DB_Outbox
    Plan -->|Query Watermark| DB_Cursor
    Plan -->|Fetch Config| Registry

    DB_Outbox -->|Scheduled Poll| Relay
    Relay -->|Publish Task Events| MQ
    Relay -->|Update Status| DB_Outbox

    MQ -->|Consume Tasks| Execution
    Execution -->|Update Status| DB_Task
    Execution -->|Advance Watermark| DB_Cursor
    Execution -->|Call| ExternalAPI

    %% Apply styles
    class Plan,Relay,Execution phaseClass
```

**Key Points**:
- **Plan Phase**: Completes in a single `@Transactional` transaction, ensuring atomicity of Plan/Task/Outbox writes
- **Outbox Pattern**: Implements at-least-once message delivery semantics via Outbox table
- **Relay Phase**: Independent scheduled job that polls Outbox table and publishes to MQ
- **Execution Phase**: Consumes tasks from MQ, executes them, and updates status and cursor

---

## 2. Plan Phase Detailed Flow

Detailed flow of `PlanIngestionOrchestrator.ingestPlan()` method with 6 core phases.

```mermaid
flowchart TD
    Start([Start: PlanIngestionCommand])

    %% Phase 1
    P1_1["Persist Schedule Instance<br/>(ScheduleInstanceAggregate)"]
    P1_2["Fetch Provenance Config Snapshot<br/>(patraRegistryPort.fetchConfig)"]
    P1_3["Build Trigger Norm<br/>(PlanTriggerNorm)"]

    %% Phase 2
    P2_1["Query Cursor Watermark<br/>(cursorRepository.findLatestGlobalTimeWatermark)"]
    P2_2["Resolve Planning Window<br/>(planningWindowResolver.resolveWindow)<br/>Consider: Trigger Params + Config + Cursor"]

    %% Phase 3
    P3["Build Plan Expression Descriptor<br/>(planExpressionBuilder.build)<br/>Generate hash + JSON snapshot (uncompiled)"]

    %% Phase 4
    P4_1["Count Queued Tasks<br/>(taskRepository.countQueuedTasks)"]
    P4_2{"Pre-validation<br/>(plannerValidator.validateBeforeAssemble)<br/>Check: Window Sanity / Capacity / Backpressure"}

    %% Phase 5
    P5_1["Assemble Plan<br/>(planAssembler.assemble)<br/>Generate Plan/Slices/Tasks (in-memory)"]
    P5_2{"Idempotency Check<br/>planKey exists?"}
    P5_3["Compensation Flow<br/>Retry failed/cancelled tasks"]

    %% Phase 6
    P6_1["Persist Plan<br/>planRepository.save"]
    P6_2["Persist Slices<br/>planSliceRepository.saveAll"]
    P6_3["Persist Tasks<br/>taskRepository.saveAll"]
    P6_4["Collect TaskQueuedEvents<br/>collectQueuedEvents"]
    P6_5["Publish to Outbox<br/>taskOutboxPublisher.publish<br/>Same Transaction"]

    End([Return: PlanIngestionResult])

    %% Flow connections
    Start --> P1_1 --> P1_2 --> P1_3
    P1_3 --> P2_1 --> P2_2
    P2_2 --> P3
    P3 --> P4_1 --> P4_2

    P4_2 -->|Validation Failed| Error1[Throw PlanValidationException]
    P4_2 -->|Validation Passed| P5_1

    P5_1 --> P5_2
    P5_2 -->|Exists| P5_3 --> End
    P5_2 -->|New Plan| P6_1

    P6_1 --> P6_2 --> P6_3 --> P6_4 --> P6_5
    P6_5 --> End

    %% Styles
    classDef errorClass fill:#ffebee,stroke:#c62828,stroke-width:2px
    class Error1 errorClass
```

**Phase Descriptions**:

| Phase | Responsibility | Key Components |
|-------|---------------|----------------|
| Phase 1 | Initialize schedule instance and load config | `ScheduleInstanceRepository`, `PatraRegistryPort` |
| Phase 2 | Resolve execution window (cursor-aware) | `CursorRepository`, `PlanningWindowResolver` |
| Phase 3 | Build expression prototype snapshot | `PlanExpressionBuilder` |
| Phase 4 | Pre-validate (avoid invalid plans) | `PlannerValidator`, `TaskRepository` |
| Phase 5 | Assemble Plan/Slices/Tasks | `PlanAssembler`, Slicing strategies (TIME/DATE/SINGLE) |
| Phase 6 | Persist and publish events | `PlanRepository`, `TaskOutboxPublisher` |

**Idempotency Mechanism**:
- `planKey = hash(provenance + operation + window + strategy)`
- If `planKey` exists, go to compensation flow (retry failed tasks)
- Ensures no duplicate Plans when scheduler triggers repeatedly

---

## 3. Relay Phase Flow

Outbox message processing flow of `OutboxRelayOrchestrator.relay()` method.

```mermaid
flowchart LR
    Start([Start: OutboxRelayCommand])

    %% Feature toggle check
    Check{"Feature Toggle<br/>enabled?"}
    Disabled[Return Empty Report<br/>RelayReport.empty]

    %% Build plan
    BuildPlan["Build Relay Plan<br/>(RelayPlanBuilder.build)<br/>Decide: Batch Size / Lease Params"]

    %% Executor
    subgraph Executor["OutboxRelayExecutor.execute()"]
        E1["1. Fetch Pending Messages<br/>(Lease-based Batch Query)<br/>Status: PENDING/RETRY"]
        E2["2. Publish to MQ One by One<br/>(RelayEventPublisher.publish)"]
        E3["3. Update Message Status<br/>Success → SENT<br/>Failure → RETRY/FAILED"]
        E4["4. Handle Lease Errors<br/>(leaseMissed count)"]

        E1 --> E2 --> E3 --> E4
    end

    %% Publish audit events
    PublishEvent["Publish Audit Events<br/>(RelayEventPublisher)"]

    %% Return report
    End([Return: RelayReport<br/>with statistics])

    %% Flow connections
    Start --> Check
    Check -->|disabled| Disabled --> End
    Check -->|enabled| BuildPlan
    BuildPlan --> Executor
    Executor --> PublishEvent
    PublishEvent --> End

    %% Styles
    classDef dbClass fill:#fff3e0,stroke:#e65100,stroke-width:2px
    classDef mqClass fill:#f3e5f5,stroke:#4a148c,stroke-width:2px

    DB[("ingest_outbox_message")]:::dbClass
    MQ[("Message Queue")]:::mqClass

    E1 -.->|Read| DB
    E2 -.->|Publish| MQ
    E3 -.->|Update| DB
```

**Key Mechanisms**:
- **Lease Mechanism**: Uses `lease_owner` and `lease_expire_at` fields to prevent multiple Relay instances from processing same message
- **Retry Strategy**: Failed messages are marked as `RETRY` and retried in next poll
- **Batch Processing**: Processes a batch of messages (default 100) per poll to avoid long transactions

**RelayReport Statistics**:
- `fetched`: Number of messages fetched
- `published`: Number of messages successfully published
- `retried`: Number of messages retried
- `failed`: Number of messages failed
- `leaseMissed`: Number of messages with lease misses

---

## 4. Execution Phase Flow

Three-phase execution flow of `TaskExecutionUseCaseImpl.execute()` method.

```mermaid
flowchart TD
    Start([Start: TaskReadyCommand<br/>from MQ Consumer])

    %% Phase 0: Prepare
    subgraph Prepare["Phase 0: Prepare (PrepareTaskExecutionUseCase)"]
        PR1["1. Idempotency Check<br/>(IdempotencyChecker)<br/>Check if task already succeeded"]
        PR2{"Task Already<br/>Succeeded?"}
        PR3["2. Lease Acquisition<br/>(LeaseManagementService)<br/>CAS update lease_owner"]
        PR4{"Lease Acquired?"}
        PR5["3. Load Execution Context<br/>(ExecutionContextLoader)<br/>Restore snapshots + Compile expression"]
        PR6["4. Start Heartbeat Renewal<br/>(HeartbeatRenewalService)"]
        PR7["5. Create Execution Session<br/>(ExecutionSession)"]

        PR1 --> PR2
        PR2 -->|Yes| Skip1[Skip Execution]
        PR2 -->|No| PR3
        PR3 --> PR4
        PR4 -->|Failed| Skip2[Skip Execution<br/>Other worker owns it]
        PR4 -->|Success| PR5
        PR5 --> PR6 --> PR7
    end

    %% Phase 1: Execute
    subgraph Execute["Phase 1: Execute (ExecuteTaskBatchesUseCase)"]
        EX1["1. Batch Planning<br/>(BatchPlanner)<br/>Select strategy by provenance<br/>e.g., PubmedBatchPlanner"]
        EX2["2. Batch Execution<br/>(BatchExecutor)<br/>Call external API to fetch data"]
        EX3["3. Data Storage<br/>Save to target tables"]
        EX4{"All Batches<br/>Complete?"}

        EX1 --> EX2 --> EX3 --> EX4
        EX4 -->|No| EX2
    end

    %% Phase 2: Complete
    subgraph Complete["Phase 2: Complete (CompleteTaskExecutionUseCase)"]
        C1{"Execution<br/>Result?"}
        C2["Success Path<br/>1. Update task status → SUCCEEDED<br/>2. Advance cursor watermark (CursorAdvancer)<br/>3. Release lease"]
        C3["Failure Path<br/>1. Update task status → FAILED<br/>2. Record error message<br/>3. Release lease"]
        C4["Stop Heartbeat Renewal"]

        C1 -->|Success| C2
        C1 -->|Failure| C3
        C2 --> C4
        C3 --> C4
    end

    End([Task Execution Complete])
    Error([Exception Handling<br/>cleanup session])

    %% Flow connections
    Start --> Prepare
    Prepare --> PR7
    PR7 --> Execute
    Execute --> EX4
    EX4 -->|Yes| Complete
    Complete --> C4
    C4 --> End

    Skip1 --> End
    Skip2 --> End

    %% Exception handling
    Prepare -.->|Exception| Error
    Execute -.->|Exception| Error
    Complete -.->|Exception| Error
    Error --> End

    %% Styles
    classDef skipClass fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px
    classDef errorClass fill:#ffebee,stroke:#c62828,stroke-width:2px
    class Skip1,Skip2 skipClass
    class Error errorClass
```

**Three-Phase Details**:

### Phase 0: Prepare (Preparation Phase)
- **Idempotency Guarantee**: Check if task already succeeded to avoid duplicate processing
- **Lease Mechanism**: Acquire task execution right via CAS update of `lease_owner`
- **Context Restoration**:
  - Restore config snapshots from Task → Slice → Plan hierarchy
  - Compile expression (via `patra-expr-kernel`)
  - Generate `ExecutionContext` (includes compiledQuery + compiledParams)
- **Heartbeat Renewal**: Start background thread to periodically renew lease

### Phase 1: Execute (Execution Phase)
- **Batch Planning**: Select appropriate `BatchPlanner` based on provenance
  - e.g., `PubmedBatchPlanner` requests planning metadata (count + WebEnv/QueryKey) so Execute stage can reuse PubMed History Server caches instead of re-running ESearch for every batch
- **Batch Execution**: Call external APIs (PubMed/EPMC/Crossref) to fetch data
- **Data Storage**: Save to corresponding business tables (literature/provenance, etc.)

### Phase 2: Complete (Completion Phase)
- **Success Path**:
  1. Update task status to `SUCCEEDED`
  2. Advance cursor watermark (`CursorAdvancer`)
  3. Release lease
- **Failure Path**:
  1. Update task status to `FAILED`
  2. Record error message (`lastErrorMsg`)
  3. Release lease (allow retry)
- **Resource Cleanup**: Stop heartbeat renewal thread

**Exception Handling**:
- Any exception triggers `session.cleanup()`
- Ensures lease release and heartbeat stop to avoid resource leaks
- Exception is re-thrown; upstream (MQ consumer) decides whether to retry

---

## 5. Data Flow Diagram

Data flow relationships between database tables.

```mermaid
flowchart TD
    %% Database tables
    Schedule[("ingest_schedule_instance<br/>Schedule Instance")]
    Plan[("ingest_plan<br/>Plan")]
    Slice[("ingest_plan_slice<br/>Slice")]
    Task[("ingest_task<br/>Task")]
    Outbox[("ingest_outbox_message<br/>Outbox Message")]
    Cursor[("ingest_cursor<br/>Cursor Watermark")]

    %% External tables
    Literature[("literature<br/>Literature Data")]
    Provenance[("provenance_record<br/>Provenance Record")]

    %% Flow relationships
    Schedule -->|1. Plan phase creates| Plan
    Plan -->|2. Generate slices| Slice
    Slice -->|3. Generate tasks| Task
    Task -->|4. Publish events<br/>same transaction| Outbox

    Outbox -->|5. Relay phase<br/>publish to MQ| MQ["Message Queue"]
    MQ -->|6. Execution phase<br/>consume task| Task

    Task -->|7. On success<br/>advance watermark| Cursor
    Task -->|8. Store data| Literature
    Task -->|9. Store provenance| Provenance

    Cursor -.->|10. Next Plan phase<br/>query watermark| Plan

    %% Styles
    classDef coreTable fill:#e1f5ff,stroke:#01579b,stroke-width:2px
    classDef bizTable fill:#f3e5f5,stroke:#4a148c,stroke-width:2px
    classDef mqClass fill:#fff3e0,stroke:#e65100,stroke-width:2px

    class Schedule,Plan,Slice,Task,Outbox,Cursor coreTable
    class Literature,Provenance bizTable
    class MQ mqClass
```

**Key Constraints**:
- `ingest_plan.schedule_instance_id` → `ingest_schedule_instance.id`
- `ingest_plan_slice.plan_id` → `ingest_plan.id`
- `ingest_task.plan_id` → `ingest_plan.id`
- `ingest_task.slice_id` → `ingest_plan_slice.id`

**Idempotency Indexes**:
- `ingest_plan.plan_key` (UNIQUE)
- `ingest_task.idempotent_key` (UNIQUE)

**Performance Indexes**:
- `ingest_task(status, scheduled_at)` - Worker polling for tasks
- `ingest_outbox_message(status, seq)` - Relay polling for messages

---

## 6. Key Design Patterns

### 6.1 Outbox Pattern
- **Purpose**: Guarantee at-least-once message delivery semantics
- **Implementation**: Plan phase writes Task + Outbox messages in same transaction
- **Advantage**: Avoids distributed transactions, ensures data consistency

### 6.2 Idempotency Mechanism
- **Plan Level**: `planKey = hash(provenance + operation + window + strategy)`
- **Task Level**: `idempotentKey` business idempotency key
- **Execution Level**: Check task status before execution, skip if already succeeded

### 6.3 Lease Mechanism
- **Relay Phase**: Outbox messages use `lease_owner` + `lease_expire_at` to prevent duplicate processing
- **Execution Phase**: Tasks use lease mechanism to ensure single-worker execution
- **Heartbeat Renewal**: Periodically renew lease during execution to prevent long-task lease expiration

### 6.4 Cursor Advancement
- **Purpose**: Support incremental data collection
- **Implementation**: Advance `ingest_cursor` table watermark after each successful task
- **Query**: Next Plan phase continues from watermark position

---

## 7. References

- [patra-ingest README](../../patra-ingest/README.md)
- [Plan Ingestion Use Case README](../../patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/plan/README.md)
- [Architecture Documentation](../ARCHITECTURE.md)
- [Development Guide](../DEV-GUIDE.md)

---

**Last Updated**: 2025-01-19
