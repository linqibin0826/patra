# Repository Structure (High-Level)

- Root Maven aggregator (`pom.xml`) modules:
  - `patra-parent` — parent/BOM, dependency and plugin mgmt
  - `patra-common` — base types, error model, normalization helpers
  - `patra-ingest` — {api,domain,app,infra,adapter,boot}
  - `patra-registry` — {api,domain,app,infra,adapter,boot}
  - `patra-egress-gateway` — {api,domain,app,infra,adapter,boot}
  - `patra-gateway-boot` — Spring Cloud Gateway executable
  - `patra-expr-kernel` — expression AST/normalization engine
  - Starters: `patra-spring-boot-starter-{core,web,mybatis,provenance,expr}`
  - Spring Cloud Feign starter: `patra-spring-cloud-starter-feign`
- Docs under `docs/`:
  - `architecture/` (C4), `adr/`, `contracts/`, `services/`, `operations/`, `conventions/`, `process/`, `nfr/` etc.
- Local infra under `docker/` with `compose/docker-compose.dev.yaml`.

Hexagonal layering per service: `adapter → app → domain ← infra`; `*-boot` provides entrypoint.