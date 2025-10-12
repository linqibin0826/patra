# Papertrace Documentation Index

## Overview
- Architecture-first, docs-as-code. Contracts and ADRs are authoritative; service docs link back to them.

## Architecture
- C4 Context: [architecture/C4-Context.md](architecture/C4-Context.md)
- C4 Container: [architecture/C4-Container.md](architecture/C4-Container.md)
- Deployment Topology: [architecture/Deployment-Topology.md](architecture/Deployment-Topology.md)
- Module Map: [architecture/Module-Map.md](architecture/Module-Map.md)
- Sequences: [architecture/Sequences.md](architecture/Sequences.md)

## ADRs
- Index and template: [adr/README.md](adr/README.md), [adr/ADR-Template.md](adr/ADR-Template.md)
- Decisions: [adr/0001-architecture-style-hexagonal-ddd.md](adr/0001-architecture-style-hexagonal-ddd.md), [adr/0002-messaging-rocketmq-outbox.md](adr/0002-messaging-rocketmq-outbox.md)

## Contracts
- HTTP/API: [contracts/api/README.md](contracts/api/README.md)
- Endpoint docs: [contracts/api/egress-gateway.md](contracts/api/egress-gateway.md)
- Endpoint docs: [contracts/api/registry-internal.md](contracts/api/registry-internal.md)
- Events: [contracts/events/README.md](contracts/events/README.md), [contracts/events/task-ready.md](contracts/events/task-ready.md)
- Event Schemas: [contracts/events/schemas/](contracts/events/schemas/)
- Note: OpenAPI generation is deferred; maintain per-endpoint docs.
 - API module READMEs (consumer-facing):
   - [patra-registry-api/README.md](../patra-registry/patra-registry-api/README.md)
   - [patra-ingest-api/README.md](../patra-ingest/patra-ingest-api/README.md)
   - [patra-egress-gateway-api/README.md](../patra-egress-gateway/patra-egress-gateway-api/README.md)

## Services
- Catalog: [services/index.md](services/index.md)

## Quality & Operations
- NFR Matrix: [nfr/NFR-Matrix.md](nfr/NFR-Matrix.md)
- Observability Plan: [observability/README.md](observability/README.md)
- Operations: [operations/Local-Dev-Runbook.md](operations/Local-Dev-Runbook.md), [operations/Release-Versioning.md](operations/Release-Versioning.md), [operations/Ingest-Runbook.md](operations/Ingest-Runbook.md), [operations/Registry-Runbook.md](operations/Registry-Runbook.md), [operations/Egress-Runbook.md](operations/Egress-Runbook.md)
- Metrics: [observability/Metrics-Glossary.md](observability/Metrics-Glossary.md), [observability/Dashboards.md](observability/Dashboards.md)
- Testing Strategy: [testing/README.md](testing/README.md)
- Conventions & Standards: [conventions/README.md](conventions/README.md)

## Changelog
- [changelog/CHANGELOG.md](changelog/CHANGELOG.md)

## Change Workflow
- New behavior or contracts: update ADRs and contracts first.
- Service changes: update the service README and [services/index.md](services/index.md).
- Architecture shifts: update C4 docs and Deployment Topology.
