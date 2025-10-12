# Code Style & Conventions

- Language: Java 21; UTF-8; 4-space indent; ~120-char line width.
- Packages: `com.patra.*`; Maven `groupId` = `com.papertrace`.
- Architecture: Hexagonal + DDD. Dependency direction MUST hold: `adapter → app → domain ← infra`; domain is pure Java (no Spring).
- Modules: service modules follow `patra-<service>-{api,domain,app,infra,adapter,boot}`; executables are `*-boot`.
- Immutability: use Java `record` for value objects; Lombok for POJOs; MapStruct for mapping DO↔Domain.
- Naming: `*Orchestrator` (app), `*Command` (commands), `*Port` (interfaces), `*Impl` (implementations).
- Application layer: orchestrates use cases, defines transaction boundaries; no business rules here.
- Infrastructure: MyBatis-Plus repos, mappers; Feign/RPC; MapStruct mappers.
- Adapter: Controllers/Jobs/Listeners with `@Valid`, RFC7807 `ProblemDetail` error mapping, trace propagation.
- Event-driven first: outbox pattern, idempotency keys, retries with backoff, failure queues.
- Sync calls: define timeouts/retries/circuit-breakers/rate limits; avoid tight coupling.
- Logging: parameterized (`log.info("Processing plan {}", planId)`), include `traceId` and key IDs (planId, sliceId, taskId, batchId). Do not log secrets.
- Security/config: secrets via Nacos/env; never commit. CORS and boundary protection at adapters/gateway.
- Observability: SkyWalking traces, structured logs, Micrometer metrics with correlation IDs.
- Commits: Conventional Commits; small, focused diffs; update docs per DoD.

References: `docs/conventions/README.md`, repository `README.md`.