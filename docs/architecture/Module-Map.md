# Module Map

## Top-Level Modules
- `patra-ingest` — plans, slices, task execution, outbox relay
- `patra-registry` — provenance metadata, configuration, expression snapshots
- `patra-egress-gateway` — outbound HTTP with resilience envelope
- `patra-gateway-boot` — edge gateway entrypoint
- Shared: `patra-common`, `patra-expr-kernel`, `patra-spring-boot-starter-*`, `patra-spring-cloud-starter-feign`

## Service Layout (Hexagonal)
- `*-adapter` — controllers/jobs/listeners (input), validation, error mapping
- `*-app` — orchestrators/use cases, transactions, cross-aggregate coordination
- `*-domain` — aggregates, VOs, domain events, ports (pure Java)
- `*-infra` — repositories, mappers, external RPCs, messaging adapters
- `*-boot` — Spring Boot application, wiring, config

## Dependency Direction (must follow)
- adapter → app + api
- app → domain
- infra → domain
- domain → no framework dependencies
