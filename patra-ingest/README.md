# patra-ingest

The ingest service orchestrates scheduled literature collection for Papertrace. It plans ingestion windows, slices work into tasks, persists results, and publishes outbox events for downstream processing.

## Recent Highlights (2025-10-02)

### 🔧 Code Improvements
- **Unified Outbox retrieval** – Consolidated `fetchPending` and `fetchPendingAllChannels` into a single API that accepts an optional channel filter.<br>
  - Application port: `OutboxRelayStore.fetchPending(String channel, Instant availableTime, int limit)`<br>
  - MyBatis implementation: uses dynamic SQL (`<if>` / `<choose>`) to compose predicates.<br>
  - Benefit: eliminates duplicate logic while preserving SQL performance characteristics.

### 🚀 Feature Delivery
- **RocketMQ publisher reinstated**<br>
  - Integrated `spring-cloud-starter-stream-rocketmq` with `RocketMqOutboxPublisher` using `StreamBridge` for dynamic destinations.<br>
  - Added `papertrace.ingest.outbox` whitelist configuration, supporting fail-fast enforcement and runtime validation.<br>
  - Adapter module now includes `ingestTaskReadyConsumer` for end-to-end verification with structured logging of KEYS/TAGS/partition keys.

## 1. Module Scope
- **Responsibilities** – Generate ingestion plans per provenance and operation, ensuring idempotence, replayability, and observability across the pipeline.
- **Primary consumers** – Job scheduler (XXL-Job), downstream parsing/cleansing services, and asynchronous delivery channels.
- **Architecture boundary** – Hexagonal layering:
  - `adapter`: inbound scheduler/listener entry points
  - `app`: orchestration for planning and relay use cases
  - `domain`: aggregates, value objects, and domain ports
  - `infra`: persistence, messaging, and RPC adapters
  - `boot`: Spring Boot wiring

## 2. Core Capabilities
- **Window strategies** – HARVEST / BACKFILL / UPDATE with guard rails for lag and overlap.
- **Slice strategies** – TIME and SINGLE (extensible via `SlicePlannerRegistry`).
- **Plan assembly** – Produces Plan → PlanSlice → Task hierarchies with partial failure handling.
- **Idempotence** – Canonical hashing for expressions, configuration, and slices; task idempotency key format `provenance:operation:sliceHash:exprHash`.
- **Outbox publishing** – Lease-based retrieval with exponential backoff and ordered partitioning for reliable delivery.

> Deep design notes and schema details: `docs/modules/ingest/deep-dive.md`.

## 3. Module Breakdown

| Submodule | Responsibility |
|-----------|----------------|
| `patra-ingest-api` | Public DTOs and error codes. |
| `patra-ingest-adapter` | XXL-Job inbound jobs and Outbox relay schedulers (inbound only). |
| `patra-ingest-app` | Use cases (`usecase/plan` for planning, `usecase/relay` for outbox relay). |
| `patra-ingest-domain` | Plan/PlanSlice/Task/Schedule aggregates and domain ports.<br>Structure v0.1.0:<br>- `domain/event/` domain events<br>- `domain/model/aggregate/` aggregates<br>- `domain/model/vo/` value objects (incl. PlanTriggerNorm) |
| `patra-ingest-infra` | MyBatis-Plus DOs, mappers, repository implementations, and outbound RPC (`infra.rpc.registry.*`). |
| `patra-ingest-boot` | Spring Boot launcher, error catalog mapping. |

### Application Layer Layout
```
app/
├── config/                         # Application-level configuration
└── usecase/
    ├── plan/                       # Planning use cases
    │   ├── PlanIngestionUseCase.java        # Use-case contract
    │   ├── PlanIngestionOrchestrator.java   # Orchestrator
    │   ├── command/                 # Command models
    │   ├── dto/                     # Result DTOs
    │   ├── assembler/               # Plan assembly helpers
    │   ├── slicer/                  # Slice planners
    │   ├── window/                  # Window resolvers
    │   ├── expression/              # Expression builders
    │   ├── validator/               # Preflight validators
    │   └── publisher/               # Outbox publishers
    │
    └── relay/                       # Outbox relay use cases
        ├── OutboxRelayUseCase.java          # Use-case contract
        ├── OutboxRelayOrchestrator.java     # Orchestrator
        ├── command/                 # Command models
        ├── dto/                     # Result DTOs
        ├── executor/                # Relay executors
        ├── planner/                 # Relay plan builders
        ├── policy/                  # Error classification policies
        ├── publisher/               # Event publishers
        ├── config/                  # Relay configuration objects
        └── support/                 # Supporting utilities
```

Key dependencies: `patra-common`, `patra-expr-kernel`, MyBatis-Plus, XXL-Job, Nacos. Domain layer must remain framework-free; adapters may not embed business rules.

## 4. Operation & Configuration
- **Scheduler entry point** – Extend `AbstractProvenanceScheduleJob` for XXL-Job integration. Parameters include `provenanceCode`, `operationCode`, and window descriptors.
- **Configuration source** – Fetch provenance/expression snapshots from `patra-registry`; local overrides via Nacos.
- **Outbox settings** (excerpt):

| Property | Default | Description |
|----------|---------|-------------|
| `patra.ingest.outbox-relay.enabled` | `true` | Toggle relay pipeline. |
| `patra.ingest.outbox-relay.batch-size` | `200` | Scan batch size per lease. |
| `patra.ingest.outbox-relay.max-retry` | `8` | Maximum retry attempts. |
| `patra.ingest.planner.queue-threshold` | `10000` | Queue pressure threshold for throttling. |

## 5. Observability & Operations
- Recommended metrics: `ingest.plan.created`, `ingest.plan.slice.count`, `ingest.outbox.publish.duration`.
- Log key fields: `planKey`, `sliceSignatureHash`, `taskIdempotentKey`, `traceId`.
- Troubleshooting quick reference:

| Symptom | Diagnostic Path |
|---------|-----------------|
| No tasks generated | Inspect `ing_plan` table, check ING-12xx logs for validation failures. |
| Outbox backlog | Query `ing_outbox_message` status, review retry/dead-letter counts. |
| Publish jitter | Inspect `retry_count` and channel health; adjust backoff if needed. |

## 6. Testing Strategy
- **Domain** – Validate Plan/PlanSlice/Task invariants and idempotency key derivation.
- **App** – Cover window resolution, slice partitioning, and partial-failure recovery paths.
- **Adapter** – Verify scheduler parameter parsing and relay scheduling flow.
- **Infra** – Exercise repositories, batch inserts, and indexing strategies.
- **Integration** – Simulate MQ outages, missing configuration, and window boundary violations.

## 7. Roadmap & Risks

| Initiative | Priority | Notes / Risks |
|------------|----------|---------------|
| CURSOR / ID_RANGE slicing strategies | High | Requires new idempotent keys and slice decomposition logic. |
| Metrics & health probes | High | Without them, backlog and lease anomalies are hard to detect. |
| Batch insert optimisation | Medium | Must evaluate database locking and write amplification. |
| Task execution contract | Medium | Downstream execution path still evolving; need clear message format. |
| Relay circuit breaking / throttling | Low | Prevent retry storms once monitoring thresholds are defined. |

Primary risks: configuration drift, window boundary errors, outbox retry storms, and channel outages. Use `docs/process/ingest-dataflow.md` for end-to-end diagnosis.

## 8. References
- Ingest deep dive: `docs/modules/ingest/deep-dive.md`
- End-to-end flow: `docs/process/ingest-dataflow.md`
- Registry configuration: `docs/modules/registry/deep-dive.md`
- Error handling standard: `docs/standards/platform-error-handling.md`
