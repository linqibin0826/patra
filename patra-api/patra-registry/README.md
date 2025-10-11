# patra-registry

The registry service is the single source of truth for Papertrace configuration and metadata. It maintains time-aware snapshots consumed by ingestion, expression rendering, and downstream analytics teams.

## 1. Module Scope
- **Responsibilities** – Own configuration for Dictionary, Provenance, and Expression subdomains, producing scope-aware (SOURCE/TASK) snapshots.
- **Primary consumers** – `patra-ingest`, `patra-spring-boot-starter-expr`, and upcoming search/persona services.
- **Architecture guardrails** – Hexagonal design with CQRS. The domain layer stays framework-free while the read side may project cached snapshots.

## 2. Core Capabilities
- **Dictionary subdomain** – Normalizes dictionary types/items/aliases, enforces single defaults, and performs status filtering.
- **Provenance subdomain** – Composes multi-slice configuration (windowing, pagination, rate limits, credentials) per scope.
- **Expression subdomain** – Aggregates field capabilities, templates, and API parameter mappings into `ExprSnapshot` outputs.
- **Effective-time model** – Every rule supports `effective_from` / `effective_to`; merge priority is `SOURCE < TASK`.
- **Error taxonomy** – Uses `REG-1xxx` (business) and `REG-0xxx` (HTTP-aligned) codes, emitted as RFC 7807 ProblemDetail payloads through the core starter.

> Detailed design notes, schema diagrams, and extension guidance live in `docs/modules/registry/deep-dive.md`.

## 3. Submodules and Dependencies
- Modules: `api` (contracts and error codes), `adapter` (REST/MQ/Scheduler), `app` (use cases), `domain` (aggregates), `infra` (MyBatis-Plus implementations), `boot` (Spring Boot entry point).
- Dependencies: `patra-common`, `patra-spring-boot-starter-mybatis`, Nacos, MySQL, with Caffeine caching planned.
- Anti-patterns: the domain layer must not import frameworks; adapters must not depend on infra; avoid cross-layer shortcuts.

## 4. Running and Configuration
- **Maven inclusion**:
  ```xml
  <dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-registry-boot</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
  ```
- **Data onboarding** – Authoritative data resides in MySQL. Seed via admin tooling or the SQL bundles under `docs/modules/registry/sql`. Service configuration is sourced from Nacos.
- **Developer commands**:
  ```bash
  # Run module tests
  ./mvnw -pl patra-registry -am test
  # Launch the service locally
  ./mvnw -pl patra-registry/patra-registry-boot spring-boot:run
  ```

## 5. Observability and Operations
- Suggested metrics: `registry.validation.errors.count`, `registry.snapshot.build.duration`, and database latency/row-count dashboards.
- Caching roadmap: Introduce Caffeine with versioned invalidation via Outbox broadcasts; monitor current MySQL load closely.
- Health automation: Plan automated checks for scope overlap and default-item conflicts.
- Common incidents: overlapping effective windows, mis-ordered scope precedence, and stale cache snapshots. Document remediation steps in `docs/operations/troubleshooting.md`.

## 6. Testing Strategy
- **Domain** – Assert invariants (single defaults, non-overlapping windows, correct scope merge logic).
- **Infra** – Validate snapshot queries, MyBatis mappings, and pagination/rate-limit slice composition.
- **App** – Cover orchestration, transactional boundaries, and event publication.
- **Adapter** – Verify DTO serialization, error-code mapping, and REST/MQ contracts.
- **Performance** – Benchmark cold/warm snapshot construction and track latency regressions.

## 7. Roadmap and Risks
| Initiative | Priority | Notes / Risks |
|------------|----------|---------------|
| Caffeine cache with versioned invalidation | High | Guarantee snapshot consistency while broadcasting cache evictions. |
| Snapshot pre-compilation | High | Improve expression-rendering latency; ensure backward compatibility. |
| Outbox-driven cache refresh | Medium | Coordinate with ingest to avoid traffic amplification. |
| JSON Schema validation for `paramsJson` | Medium | Introduces schema management and validation cost. |
| Automated health inspections | Medium | Catch configuration conflicts before they reach production. |

Primary risks include overlapping time windows, incorrect scope precedence, and stale cache entries. Establish monitoring and automated audits accordingly.

## 8. References
- Deep dive: `docs/modules/registry/deep-dive.md`
- Seed SQL: `docs/modules/registry/sql/`
- Error handling standard: `docs/standards/platform-error-handling.md`
- Ingestion dataflow overview: `docs/process/ingest-dataflow.md`
