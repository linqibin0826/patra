# Project Overview

- Name: Papertrace — Medical Literature Data Platform
- Purpose: Unified ingestion, normalization, and intelligent analysis of biomedical literature; produce a single source of truth for downstream search, profiles, and insights with provenance and traceability.
- Architecture: Microservices with Hexagonal Architecture and DDD; event-driven with Outbox; clear service boundaries.
- Core Services:
  - `patra-ingest` — plans/windows/slices, task execution, outbox relay (RocketMQ)
  - `patra-registry` — provenance metadata and time-aware configuration, expression snapshots
  - `patra-egress-gateway` — outbound HTTP with standardized envelope and resilience hooks
  - `patra-gateway-boot` — API Gateway entrypoint
- Shared Libraries: `patra-common`, `patra-expr-kernel`, starters (`core`, `web`, `mybatis`, `provenance`, `expr`, `feign`).
- Observability & Security: SkyWalking traces, parameterized logs, Micrometer metrics; secrets/config via Nacos/env; boundary protection at adapters/gateway.
- Docs System: Architecture-first, ADRs, C4 diagrams, contracts; see `docs/docs-spec.md`.