# Services Catalog

- [patra-ingest/README.md](../../patra-ingest/README.md) — orchestrates ingestion plans, time/windows slicing, task execution, and outbox relays.
  - [patra-ingest-api/README.md](../../patra-ingest/patra-ingest-api/README.md) — consumer-facing error codes.
- [patra-registry/README.md](../../patra-registry/README.md) — provides provenance metadata, configuration, and expression snapshots via RPC.
  - [patra-registry-api/README.md](../../patra-registry/patra-registry-api/README.md) — consumer-facing RPC interfaces and DTOs.
- [patra-egress-gateway/README.md](../../patra-egress-gateway/README.md) — performs outbound HTTP calls with unified resilience envelope and response.
  - [patra-egress-gateway-api/README.md](../../patra-egress-gateway/patra-egress-gateway-api/README.md) — consumer-facing RPC interface and DTOs.
- [patra-gateway-boot/README.md](../../patra-gateway-boot/README.md) — edge gateway application entrypoint.

Each service includes a README under its root directory with responsibilities, ports, APIs, events, and operational notes.
