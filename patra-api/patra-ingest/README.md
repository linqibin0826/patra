# patra-ingest

## Overview
- Orchestrates scheduled literature ingestion: plan time windows and slices, enqueue tasks, execute batches, and publish outbox events with strong idempotency and observability.

## Module Layout
- `patra-ingest-adapter` — inbound schedulers (XXL-Job) and RocketMQ consumers
- `patra-ingest-app` — application use cases for planning, execution, and outbox relay
- `patra-ingest-domain` — aggregates, value objects, ports, and domain events (pure Java)
- `patra-ingest-infra` — MyBatis-Plus mappers/repos, MQ publisher, RPC adapters, config
- `patra-ingest-boot` — Spring Boot application and baseline configs

## Key Flows
1) Plan Ingestion
   - Entrypoint jobs extend `AbstractProvenanceScheduleJob` (e.g., `PubmedHarvestJob`).
     - File: `patra-ingest/patra-ingest-adapter/src/main/java/com/patra/ingest/adapter/inbound/scheduler/job/AbstractProvenanceScheduleJob.java:1`
     - File: `patra-ingest/patra-ingest-adapter/src/main/java/com/patra/ingest/adapter/inbound/scheduler/job/PubmedHarvestJob.java:1`
   - Orchestrator `PlanIngestionOrchestrator` performs: schedule persist → config snapshot → window resolve → expression build → validations → assemble → persist → outbox publish.
     - File: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/plan/PlanIngestionOrchestrator.java:1`
   - Outbox publishes TaskReady messages with partitioning for ordered execution.
2) Execute Task
   - Consumer `ingestTaskReadyConsumer` parses payload to `TaskReadyCommand` and invokes `TaskExecutionUseCase`.
     - File: `patra-ingest/patra-ingest-adapter/src/main/java/com/patra/ingest/adapter/inbound/stream/IngestStreamConsumers.java:1`
   - Orchestrator `TaskExecutionUseCaseImpl` runs Prepare → Execute → Complete with leases, heartbeats, and checkpointing.
     - File: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/execution/TaskExecutionUseCaseImpl.java:1`
3) Outbox Relay
   - Orchestrator `OutboxRelayOrchestrator` builds a relay plan, executes batch publish, and emits audit events.
     - File: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/relay/OutboxRelayOrchestrator.java:1`
   - Publisher `RocketMqOutboxPublisher` uses Spring Cloud Stream `StreamBridge` to send with KEYS/TAGS and `partitionKey` headers.
     - File: `patra-ingest/patra-ingest-infra/src/main/java/com/patra/ingest/infra/messaging/RocketMqOutboxPublisher.java:1`

## Domain Model
- Aggregates: `PlanAggregate`, `PlanSliceAggregate`, `TaskAggregate`, `ScheduleInstanceAggregate`.
- Key VOs: `WindowSpec`, `SliceSpec`, `PlannerWindow`, `IdempotentKey`, `ExecutionTimeline`, `TaskReadyMessage`, `RelayPlan`.
- Ports (selected): `PatraRegistryPort`, `ExpressionCompilerPort`, `TaskRepository`, `PlanRepository`, `PlanSliceRepository`, `OutboxPublisherPort`, `OutboxRelayStore`.

## Database Schema (Flyway)
- Migration: `patra-ingest/patra-ingest-infra/src/main/resources/db/migration/V0.1.0__init_ingest_schema.sql:1`
- Tables (excerpt):
  - `ing_schedule_instance` — one external trigger; stores scheduler identifiers and params.
  - `ing_plan` — plan blueprint; includes window/time indexes, expression snapshot/hash, status.
  - `ing_plan_slice` — slice inventory with `slice_signature_hash` and expr hash; unique per plan.
  - `ing_task` — tasks by slice with idempotency key, lease/heartbeat/retry/status fields.
  - `ing_task_run` — attempt records per task (retries and replays are new attempts).
  - `ing_task_run_batch` — fine-grained page/token batches with separate idempotency key.
  - `ing_outbox_message` — generic outbox with channel/opType/dedupKey, partitioning/lease indexes.
  - `ing_cursor_event` — cursor change events for watermarks and lineage.

## Schedulers (XXL-Job)
- Base: `AbstractProvenanceScheduleJob` handles param parsing, orchestration, and result reporting.
- Example: `PubmedHarvestJob` binds `ProvenanceCode.PUBMED` + `OperationCode.HARVEST` and delegates to the base execution.
- Param JSON fields: `windowFrom`, `windowTo`, `priority`, `step`, `schedulerLogId`, `triggeredAt`, plus arbitrary pass-through fields → `triggerParams`.

## Planning Use Case
- `PlanIngestionOrchestrator#ingestPlan` phases:
  1. Persist schedule instance; load `ProvenanceConfigSnapshot` via `PatraRegistryPort`.
  2. Resolve planner window from cursor watermark + config + trigger.
  3. Build plan-level expression descriptor (hash and JSON snapshot only).
  4. Validate window sanity, queue pressure, and capacity.
  5. Assemble plan/slices/tasks blueprints.
  6. Persist aggregates and publish `TaskQueuedEvent`s via `TaskOutboxPublisher`.
- Idempotency: de-dup by `planKey`; reuse and retry failed tasks when a plan already exists.

## Execution Use Case
- `TaskExecutionUseCaseImpl#execute` orchestrates Prepare → Execute → Complete with structured logging and cleanup.
- Prepare: lease acquisition, idempotent skip if already succeeded.
- Execute: batched processing with `BatchExecutor` (paged/tokenized), stats, and per-batch idempotency.
- Complete: cursor advance and result persistence.

## Outbox Relay
- Toggle: `patra.ingest.outbox-relay.enabled` (see app properties).
- `OutboxRelayOrchestrator#relay` builds plan (channel filter, batch size, lease owner) and updates message states atomically.
- Publisher: `RocketMqOutboxPublisher` enforces channel whitelist; publishes with headers: `KEYS`, `TAGS`, and `partitionKey`.

## Streaming Contracts
- Consumer: `ingestTaskReadyConsumer` listens to `INGEST_TASK_READY` with concurrency/backoff configured in `patra-ingest/patra-ingest-boot/src/main/resources/ingest-mq-config.yaml:1`.
- Payload DTO: `TaskReadyPayload` contains `taskId` and `idempotentKey`; headers include RocketMQ KEYS/TAGS/topic/messageId and `partitionKey`.
- See event contract: `docs/contracts/events/task-ready.md:1`.

## Configuration
- Boot `application.yaml`: datasource + Flyway, Redis, Nacos imports, execution defaults.
  - File: `patra-ingest/patra-ingest-boot/src/main/resources/application.yaml:1`
- MQ binding config: destination `INGEST_TASK_READY`, group, concurrency, backoff.
  - File: `patra-ingest/patra-ingest-boot/src/main/resources/ingest-mq-config.yaml:1`
- Outbox publisher: `papertrace.ingest.outbox.*` and `allowed-channels` whitelist.
  - File: `patra-ingest/patra-ingest-boot/src/main/resources/ingest-mq-config.yaml:1`

## Observability
- Logs include trace/correlation IDs and keys: planId, sliceId, taskId, idempotentKey, sliceSignatureHash.
- Metrics (recommend): plan creation counts, slice/task counts, outbox publish/relay timers, execution success/failure counters, MQ consumer lag.

## Testing Strategy
- Domain: invariants for plan/slice/task, idempotency keys, window calculations.
- App: window resolution, expression hashing, idempotent plan reuse, relay execution planning.
- Adapter: XXL-Job param parsing, MQ consumer header handling and error path (retries).
- Infra: MyBatis mappers, RocketMQ publishing, and repository behaviors.

## Run Locally
```bash
./mvnw -pl patra-ingest/patra-ingest-boot -am spring-boot:run
```

## Related Docs
- Service catalog: `docs/services/index.md:1`
- Event contract: `docs/contracts/events/task-ready.md:1`
- Runbook: `docs/operations/Ingest-Runbook.md:1`
- ADR (Outbox): `docs/adr/0002-messaging-rocketmq-outbox.md:1`
- Sequences: `docs/architecture/Sequences.md:1`

## Open TODOs
- Add failure-queue metrics and dead-letter handling; expand slicers and backpressure; refine cursor lineage audits.
