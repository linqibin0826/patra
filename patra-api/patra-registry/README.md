Purpose and Responsibilities
- Single source of truth for provenance metadata and time-aware configuration (HTTP, pagination, retry, limits, offsets) plus expression snapshots used by ingest/expression compiler.

Package Layout
- Adapter: Feign-style controllers implementing internal endpoints
- App: read-model services to assemble snapshots/configs
- Domain: read models (Query DTOs), VO types, ports, and validation exceptions
- Infra: MyBatis-Plus mappers/repos and converters; DB migrations
- Boot: Spring Boot app, error mapping, and runtime config

APIs and Contracts (internal)
- Provenance endpoints (`/_internal/provenances`):
  - `GET /_internal/provenances` → list of supported provenances.
  - `GET /_internal/provenances/{code}` → a single provenance by code.
  - `GET /_internal/provenances/{code}/config?operationType=&at=` → aggregated effective configuration.
- Expression endpoint (service layer): load expression snapshot (provenance + operation + endpoint + at) via `ExprQueryAppService` for compiler usage.
- Client interfaces: `ProvenanceClient`, `ExprClient` in `patra-registry-api` for type-safe Feign usage.

Domain Model (read side)
- Aggregate: `ProvenanceConfiguration` composes `HttpConfig`, `PaginationConfig`, `RetryConfig`, `RateLimitConfig`, `WindowOffsetConfig`.
- VOs: `Provenance`, `ExprSnapshot`, `ExprCapability`, `ExprField`, `ExprRenderRule`, `ApiParamMapping`.
- Ports: `ProvenanceConfigRepository`, `ExprRepository` abstract infra data access.

Application Services
- `ProvenanceConfigAppService#listProvenances` and `#findProvenance` → metadata.
- `#loadConfiguration(code, operationType, at)` → effective configuration for a provenance/operation/instant.
- `ExprQueryAppService#loadSnapshot(provenanceCode, operationType, endpointName, at)` → expression snapshot for compilers.

Infrastructure
- DB: MySQL; Flyway migrations under `patra-registry-infra/src/main/resources/db/migration`.
- Mappers: e.g., `RegProvPaginationCfgMapper` with XML under `resources/mapper/` for efficient reads.
- Boot config: `patra-registry-boot/src/main/resources/application.yaml` imports `registry-error-config.yaml` and Nacos config.

Error Mapping
- Error catalog and mapping live under `patra-registry-api` and boot error config; expose RFC7807 ProblemDetail via the web/core starters.

Observability
- Logs include provenance code/operationType and query context; trace IDs are propagated.
- Metrics (recommend): snapshot build time, query latency, size/shape of returned configs.

How to Run
- Tests: `./mvnw -pl patra-registry -am test`
- Local service: `./mvnw -pl patra-registry/patra-registry-boot -am spring-boot:run`

Open TODOs
- Add a caching layer (e.g., Caffeine) with versioned invalidation and outbox-driven refresh strategy.
- Validate JSON structures for param mappings using JSON Schema where appropriate.
