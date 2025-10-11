Purpose and Responsibilities
- Orchestrate scheduled literature ingestion: plan windows/slices, enqueue tasks, execute batches, and publish outbox events with strong idempotency and observability.

Package Layout
- Adapter: inbound schedulers (XXL-Job) and RocketMQ consumers
- App: use cases for planning, execution, and outbox relay
- Domain: aggregates, VOs, ports, and domain events (pure Java)
- Infra: MyBatis-Plus mappers/repos, MQ publisher, RPC adapters, config
- Boot: Spring Boot app and baseline configs

Core Flows
1) Plan Ingestion
   - Entrypoints: scheduler jobs subclassing `AbstractProvenanceScheduleJob` (e.g., `PubmedHarvestJob`).
   - Orchestrator: `PlanIngestionOrchestrator` performs schedule persist → config snapshot → window resolve → expression build → validations → assemble → persist → outbox publish.
   - Outbox: publishes TaskReady messages by channel/partition key for ordered execution.
2) Execute Task
   - Consumer: `ingestTaskReadyConsumer` parses payload to `TaskReadyCommand` and invokes `TaskExecutionUseCase`.
   - Orchestrator: `TaskExecutionUseCaseImpl` runs Prepare → Execute → Complete with leases, heartbeats, and checkpointing.
3) Outbox Relay
   - Orchestrator: `OutboxRelayOrchestrator` builds a relay plan, executes a batch publish, emits audit events.
   - Publisher: `RocketMqOutboxPublisher` uses `StreamBridge` to deliver messages with KEYS/TAGS and partitionKey.

Domain Model
- Aggregates: `PlanAggregate`, `PlanSliceAggregate`, `TaskAggregate`, `ScheduleInstanceAggregate`.
- Key VOs: `WindowSpec`, `SliceSpec`, `PlannerWindow`, `IdempotentKey`, `ExecutionTimeline`, `TaskReadyMessage`, `RelayPlan`.
- Ports (selected): `PatraRegistryPort`, `ExpressionCompilerPort`, `TaskRepository`, `PlanRepository`, `PlanSliceRepository`, `OutboxPublisherPort`, `OutboxRelayStore`.

Database Schema (Flyway)
- Migration file: `patra-ingest-infra/src/main/resources/db/migration/V0.1.0__init_ingest_schema.sql`
- Tables (excerpt):
  - `ing_schedule_instance`: one external trigger; stores scheduler identifiers and params.
  - `ing_plan`: plan blueprint; includes window/time indexes, expression snapshot/hash, status.
  - `ing_plan_slice`: slice inventory with `slice_signature_hash` and expr hash; unique per plan.
  - `ing_task`: tasks by slice with idempotency key, lease/heartbeat/retry/status fields.
  - `ing_task_run`: attempt records per task (retries and replays are new attempts).
  - `ing_task_run_batch`: fine-grained page/token batches with separate idempotency key.
  - `ing_outbox_message`: generic outbox with channel/opType/dedupKey, partitioning/lease indexes.
  - `ing_cursor_event`: cursor change events for watermarks and lineage.

Scheduling Jobs (XXL-Job)
- Base: `AbstractProvenanceScheduleJob` handles param parsing, orchestration, and result reporting.
- Example: `PubmedHarvestJob` binds `ProvenanceCode.PUBMED` + `OperationCode.HARVEST` and delegates to base execution.
- Param JSON (supported fields): `windowFrom`, `windowTo`, `priority`, `step`, `schedulerLogId`, `triggeredAt`, plus arbitrary pass-through fields → `triggerParams`.

Planning Use Case
- `PlanIngestionOrchestrator#ingestPlan` phases:
  1. Persist schedule instance; load `ProvenanceConfigSnapshot` via `PatraRegistryPort`.
  2. Resolve planner window from cursor watermark + config + trigger.
  3. Build plan-level expression descriptor (hash and JSON snapshot only).
  4. Validate window sanity, queue pressure, and capacity.
  5. Assemble plan/slices/tasks blueprints.
  6. Persist aggregates and publish `TaskQueuedEvent`s via `TaskOutboxPublisher`.
- Idempotency: de-dup by `planKey`; reuse and retry failed tasks when a plan already exists.

Execution Use Case
- `TaskExecutionUseCaseImpl#execute` orchestrates Prepare → Execute → Complete with structured logging and cleanup.
- Prepare: lease acquisition, idempotent skip if already succeeded.
- Execute: batched processing with `BatchExecutor` (paged/tokenized), stats, and per-batch idempotency.
- Complete: cursor advance and result persistence.

Outbox Relay
- Toggle: `patra.ingest.outbox-relay.enabled` (see app properties).
- `OutboxRelayOrchestrator#relay` builds plan (channel filter, batch size, lease owner) and updates message states atomically.
- Publisher: `RocketMqOutboxPublisher` enforces channel whitelist; publishes with headers: `KEYS`, `TAGS`, and `partitionKey`.

Streaming Contracts
- Consumer: `ingestTaskReadyConsumer` listens to `INGEST_TASK_READY` with concurrency/backoff configured in `ingest-mq-config.yaml`.
- Payload DTO: `TaskReadyPayload` contains `taskId` and `idempotentKey`; headers include RocketMQ KEYS/TAGS/topic/messageId and `partitionKey`.
- See `docs/contracts/events/task-ready.md` for event contract.

Configuration
- Boot `application.yaml` includes:
  - MySQL/Flyway: datasource + baseline settings.
  - Redis: `spring.data.redis.url` for leases and heartbeats.
  - Nacos: config/discovery imports for `patra-ingest.yaml` and `patra.yaml`.
  - Execution defaults: `papertrace.ingest.exec.lease-ttl-seconds`, `heartbeat-interval-seconds`.
  - Task execution: `task.execution.*` for lease duration/renewal, heartbeat thresholds, batch caps.
- MQ binding: `ingest-mq-config.yaml` defines binder, destination `INGEST_TASK_READY`, group, concurrency, and backoff.
- Outbox publisher: `papertrace.ingest.outbox.publisher` and `allowed-channels` whitelist.

Observability
- Logging: include trace/correlation IDs and keys (planId, sliceId, taskId, idempotentKey, sliceSignatureHash).
- Metrics (recommend): plan creation counts, slice/task counts, outbox publish/relay timers, execution success/failure counters, MQ consumer lag.

Testing Strategy
- Domain: invariants for plan/slice/task, idempotency keys, window calculations.
- App: window resolution, expression hashing, idempotent plan reuse, relay execution planning.
- Adapter: XXL-Job param parsing, MQ consumer header handling and error path (retries).
- Infra: MyBatis mappers, RocketMQ publishing, and repository behaviors.

Run Locally
- `./mvnw -pl patra-ingest/patra-ingest-boot -am spring-boot:run`

Open TODOs
- Add failure-queue metrics and dead-letter handling; expand slicers and backpressure; refine cursor lineage audits.
