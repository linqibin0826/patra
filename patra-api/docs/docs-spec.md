# Documentation Spec

## Guiding Principles

- short, focused docs that evolve quickly.
- Docs-as-code: Markdown in docs/ + per-service READMEs.
- Architecture-first: capture decisions (ADRs), C4 context/container, contracts.
- Authoritative sources: API/event contracts are the truth; everything else references them.
- Living documents: small, frequent updates; avoid big rewrites.

## Top-Level Layout

- docs/README.md — index and table of contents.
- docs/adr/ — decision records using numbered files 0001-<slug>.md.
- docs/architecture/ — C4 Context/Container, deployment, modules map.
- docs/contracts/api/ — OpenAPI/HTTP contracts and generation instructions.
- docs/contracts/events/ — event contracts, topics, schemas, and conventions.
- docs/services/ — service catalog overview linking to per-service READMEs.
- docs/nfr/ — NFR matrix with targets and measurement approach.
- docs/observability/ — logs, metrics, traces, and basic alerting plan.
- docs/operations/ — local dev, runbooks, release/versioning, environment notes.
- docs/testing/ — test strategy by layer, naming, how to run.
- docs/conventions/ — coding, naming, logging, error taxonomy, links to repo guidelines.
- docs/changelog/CHANGELOG.md — pre-release running log of noteworthy changes.

## Per-Service Docs Placement

- patra-<service>/README.md — a single service doc covering *-api, *-domain, *-app, *-infra, *-adapter, *-boot.
- docs/services/index.md — brief catalog describing each service and linking to its README.
 - Optional: patra-<service>-api/README.md, consumer-focused doc about contracts and Feign clients; avoid duplicating the service README.

## Required Documents (MVP)

- docs/adr/ — capture real decisions; target 10–20 minutes per ADR.
- docs/architecture/C4-Context.md — what the system is and integrates with.
- docs/architecture/C4-Container.md — the main runtime containers/services.
- docs/contracts/api/openapi.yaml — generated contracts or pointers to them.
- docs/contracts/events/ — producer/consumer event specs and schemas.
- patra-<service>/README.md — responsibilities, boundaries, interfaces for each service.
 - Optional: patra-<service>-api/README.md — quickstart for consumers (endpoints, DTOs, Feign usage), links to contracts.

## Lightweight Templates (Fields Only)

- ADR (docs/adr/ADR-Template.md)
    - Title
    - Status (Proposed, Accepted, Superseded)
    - Date
    - Context
    - Decision
    - Alternatives
    - Consequences
    - References
- C4 Context (docs/architecture/C4-Context.md)
    - Purpose
    - Primary Users and External Systems
    - System Responsibilities
    - High-Level Interactions
    - Key Quality Attributes
- C4 Container (docs/architecture/C4-Container.md)
    - Containers/Services
    - Responsibilities per Container
    - Data Stores
    - Communication (sync/async)
    - Cross-Cutting Concerns (config, security, observability)
- Service README (patra-<service>/README.md)
    - Purpose and Responsibilities
    - Domain Ownership and Aggregates
    - Ports and Adapters (in/out)
    - APIs (endpoints) and Contracts
    - Events (produced/consumed) and Idempotency
    - Transactions and Consistency Boundaries
    - Infra Dependencies and Config
    - Observability (traces/metrics/logs)
    - Open TODOs
- API Contract (docs/contracts/api/<name>.md if not generated)
    - Endpoint
    - Method
    - Request Schema and Validation
    - Response Schema
    - Error Mapping
    - Idempotency/Timeout/Retry/Rate Limits
- Event Contract (docs/contracts/events/<name>.md)
    - Event Name and Version
    - Topic/Channel
    - Producer and Consumers
    - Schema (link or inline)
    - Semantics and Ordering
    - Idempotency Key and Retry Policy
- NFR Matrix (docs/nfr/NFR-Matrix.md)
    - Attribute (availability, latency, throughput, security, privacy, cost)
    - Target
    - How Measured
    - Notes
- Runbook (docs/operations/<area>-Runbook.md)
    - Purpose
    - How to Run Locally
    - Health and Diagnostics
    - Common Failures
    - Rollback/Recovery
- Release Notes (docs/changelog/CHANGELOG.md)
    - Version or Milestone
    - Highlights
    - Breaking Changes
    - Upgrade Notes

## Contracts and Generation

- API: generate OpenAPI into docs/contracts/api/openapi.yaml via a Maven plugin or manual export; add a README
  explaining the command to regenerate.
- Events: store JSON Schemas in docs/contracts/events/schemas/ and keep producer/consumer docs in
  docs/contracts/events/.

## Diagrams

- Use Mermaid in Markdown for C4 and sequences to avoid extra tooling.
- Store any binary diagrams under docs/architecture/diagrams/ only if necessary.

## Change Workflow (Fast and Consistent)

- New decision or meaningful change: add/update an ADR and link from docs/adr/README.md.
- New or changed API/Event: update docs/contracts/ and link from affected service README.
- Service behavior or boundaries change: update that service’s README.md and docs/services/index.md.
- Significant architecture change: update C4-Container.md and C4-Context.md.
- Pre-release logging: add a short entry to docs/changelog/CHANGELOG.md.

## Naming and Conventions

- ADR files: NNNN-short-title.md with zero-padded numbers.
- Service docs: patra-<service>/README.md to cover all modules within the service.
- Contracts: one file per endpoint/event when not generated; group related items logically.

## Maintenance Rules

- Keep docs small and current; prioritize ADRs and contracts.
- Define “done” to include docs updates for ADR, service README, and contracts.
- Timebox updates to stay lightweight and avoid drift.
