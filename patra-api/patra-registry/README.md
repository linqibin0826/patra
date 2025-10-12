# patra-registry

## Purpose and Responsibilities
Single source of truth for provenance metadata and time-aware configuration (HTTP, pagination, retry, rate limits, window offsets) plus expression snapshots used by ingest and the expression compiler.

## Module Layout (Hexagonal)
- Adapter: Feign-style REST controllers exposing internal RPC endpoints
- App: read-model services to assemble snapshots/configs
- Domain: read models (Query DTOs), VOs, ports, validation exceptions
- Infra: MyBatis-Plus repositories/mappers and DO↔Domain converters; Flyway migrations
- Boot: Spring Boot app, error mapping defaults, Nacos import

## Internal APIs (RPC)
Consumer contracts (endpoints, DTOs, Feign clients) are documented in the API module:
- patra-registry/patra-registry-api/README.md
- docs/contracts/api/registry-internal.md

Type-safe Feign clients are provided by the API module: `ProvenanceClient`, `ExprClient`.

## Domain Model (Read Side)
- Aggregate: `ProvenanceConfiguration` composes `HttpConfig`, `PaginationConfig`, `RetryConfig`, `RateLimitConfig`, `WindowOffsetConfig`.
- VOs: `Provenance`, `ExprSnapshot`, `ExprCapability`, `ExprField`, `ExprRenderRule`, `ApiParamMapping`.
- Ports: `ProvenanceConfigRepository`, `ExprRepository` (infra implements with MyBatis-Plus).

## Application Services
- `ProvenanceConfigAppService`
  - `listProvenances()` → metadata
  - `findProvenance(code)` → single metadata
  - `loadConfiguration(code, operationType, at)` → effective config at time `at`
- `ExprQueryAppService`
  - `loadSnapshot(provenanceCode, operationType, endpointName, at)` → expression snapshot for compilers

## Infrastructure
- MySQL with Flyway migrations under:
  - `patra-registry-infra/src/main/resources/db/migration`
- Mappers and entities:
  - Provenance: `RegProvenanceMapper`, `RegProv*CfgMapper`, DOs under `infra/persistence/entity/provenance`
  - Expression: `RegProvExpr*Mapper`, `RegExprFieldDictMapper`, DOs under `infra/persistence/entity/expr`
- Boot config: `patra-registry-boot/src/main/resources/application.yaml` imports `registry-error-config.yaml` and Nacos config.

## Configuration (excerpt)
```yaml
spring:
  application.name: patra-registry
  datasource:
    url: jdbc:mysql://127.0.0.1:13306/patra_registry?...  # see file for details
    username: root
    password: 123456
  flyway:
    enabled: true
    locations: classpath:db/migration
patra:
  error.context-prefix: REG
  web.problem.enabled: true
  feign.problem.enabled: true
```

## Error Handling
- Core/Web starters provide RFC7807 responses.
- Registry error defaults in `patra-registry-boot/src/main/resources/registry-error-config.yaml` set the context prefix (`REG`) and observation/circuit settings.

## Observability
- Logging: include `provenanceCode`, `operationType`, and query params; propagate trace IDs.
- Metrics (recommend):
  - Snapshot build time
  - Query latency by endpoint
  - Result size/shape (e.g., fields/capabilities count)

## How to Run
- Tests: `./mvnw -pl patra-registry -am test`
- Service: `./mvnw -pl patra-registry/patra-registry-boot -am spring-boot:run`

## Open TODOs
- Add a caching layer (e.g., Caffeine) with versioned invalidation and outbox-driven refresh.
- Validate JSON structures for param mappings using JSON Schema where appropriate.
