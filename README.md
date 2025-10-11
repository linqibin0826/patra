# Papertrace Literature Data Platform

Papertrace focuses on unified ingestion, normalization, and intelligent analysis of biomedical research literature. It
provides a high‑quality data foundation for search, profiles, and insights. The team is currently a solo effort covering
architecture, development, operations, and documentation. A large portion of the codebase is AI‑assisted; systematic
documentation is required to maintain long‑term clarity and operability.

## System Overview

- **Business goals**: integrate 10+ literature sources; build a Single Source of Truth (SSOT); ensure traceability
  across the ingest → parse → persist pipeline
- **Technical traits**: Hexagonal Architecture + Domain‑Driven Design (DDD); event‑driven with an Outbox design (
  MQ‑ready at any time); unified error model via `ProblemDetail`
- **Current focus**: stabilize the data landing pipeline; strengthen Registry configuration governance; standardize
  error handling and observability
- **Core stack**: Java 21, Spring Boot 3.2.4, Spring Cloud 2023.0.1, Spring Cloud Alibaba 2023.0.1.0, MyBatis‑Plus
  3.5.12, MySQL 8.0, Redis 7.0, Elasticsearch 8.14, Nacos, SkyWalking, XXL‑Job

> For architectural details, see `docs/overview/architecture.md`.

## Quick Start

1. **Environment**: install JDK 21, Docker, and Docker Compose; prefer the repository‑bundled `./mvnw`.
2. **Bring up infra**: in `docker/compose` run `docker compose up -d` to start MySQL, Redis, Elasticsearch, Nacos,
   SkyWalking, XXL‑Job, and other services.
3. **Build all modules**: at the repo root, run `./mvnw -q -DskipTests compile` to verify dependency integrity (the
   first build may take a while to download artifacts).
4. **Develop a single module**: e.g., change into `patra-registry` and run `./mvnw -q clean test`.
5. **Run services**: each `*-boot` module provides a Spring Boot entrypoint. Configure `application-local.yaml` and
   Nacos settings as needed.

> If startup fails, first check Docker resources, port conflicts, and Nacos configuration synchronization.

## Module Quick Reference

| Module               | Responsibility                                                        | Deep Dive                                                                                                                                               |
|----------------------|-----------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `patra-ingest`       | Ingestion plan assembly, window slicing, Outbox publishing            | [README](patra-ingest/README.md) · [Ingestion Dataflow](docs/process/ingest-dataflow.md)                                                                |
| `patra-registry`     | SSOT for configs/dictionaries/expressions; provides snapshot services | [README](patra-registry/README.md) · [Deep Dive](docs/modules/registry/deep-dive.md)                                                                    |
| `patra-gateway-boot` | API gateway, routing, auth, and error alignment                       | [README](patra-gateway-boot/README.md)                                                                                                                  |
| `patra-common`       | Cross‑service base types, error model, JSON normalization             | [README](patra-common/README.md)                                                                                                                        |
| `patra-expr-kernel`  | Expression AST and normalization engine                               | [README](patra-expr-kernel/README.md)                                                                                                                   |
| `patra-parent`       | Maven parent POM for unified dependencies and plugins                 | [README](patra-parent/README.md)                                                                                                                        |
| Starters series      | Auto‑config for Core/Web/Feign/MyBatis                                | [core](patra-spring-boot-starter-core/README.md) · [web](patra-spring-boot-starter-web/README.md) · [feign](patra-spring-cloud-starter-feign/README.md) |

## Business & Process Entry Points

- **Ingest → Plan → Outbox**: see `docs/process/ingest-dataflow.md`.
- **Error standards & cross‑service handling**: see `docs/standards/platform-error-handling.md` and
  `docs/standards/cross-service-error-best-practices.md`.

More process documentation will be added under `docs/process/`.

## Development Guidelines at a Glance

- Enforce dependency direction: `adapter → app → domain ← infra`; the domain layer must not pull framework dependencies.
- Use aggregates, value objects, and domain events in the domain model; the application layer owns auth, transactions,
  and idempotency.
- Prefer reusing `patra-common` and custom Starters for common capabilities—avoid reinventing wheels.
- Use `ProblemDetail` for error output; manage error code ranges via `patra.error.context-prefix`.
- Manage configuration and secrets via Nacos/environment variables; never commit them to the repository.
- See `docs/standards/platform-error-handling.md` and module‑specific deep dives for more conventions.

## Observability & Operations

- **Metrics**: publish error counts, latency, slow calls, and circuit‑breaker metrics via Micrometer; plan to centralize
  in SkyWalking/Prometheus.
- **Logging**: propagate a `traceId` end‑to‑end; use `@Slf4j` with parameterized logging in key classes; never log
  sensitive data.
- **Scheduling**: XXL‑Job triggers plans; scheduling parameters and execution states must be recorded in the
  `schedule_instance` table.
- Capture incident case studies under `docs/operations/troubleshooting.md`.

## Documentation Index

- Unified index: `docs/README.md`
- Architecture: `docs/overview/architecture.md`
- Processes: `docs/process/`
- Module deep dives: `docs/modules/`
- Operations handbook: `docs/operations/`
- Standards & templates: `docs/standards/`, `docs/templates/`
