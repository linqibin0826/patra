# Conventions & Standards

- Language/Format: Java 21, UTF-8, 4-space indent, 120-char line width.
- Packages: com.patra.*; Maven groupId com.papertrace.
- Hexagonal/DDD: adapter → app → domain ← infra; domain is pure Java.
- Immutability: Prefer records for VOs; Lombok for POJOs; MapStruct for mapping.
- Logging: @Slf4j; parameterized; never log secrets; include trace IDs.
- Naming: *Orchestrator, *Command, *Port, *Impl.
- Integration: timeouts/retries/circuit/rate-limits defined; event-driven first with outbox and idempotency.

See repository root README.md and AGENTS.md for full guidelines.

## Logging Standards
- Use parameterized format: `log.info("Processing plan {}", planId)`
- Include: `traceId`, and key IDs (planId, sliceId, taskId, batchId)
- Mask secrets and sensitive headers; centralize masking in adapters

## Error Mapping
- Use RFC7807 ProblemDetail at adapters; map domain/app exceptions to typed problems
- Do not leak internal stack traces or secrets; include correlation IDs

## Events & Contracts
- Event naming: `<Domain><Action>` with version suffix (e.g., `TaskReady v1`)
- Versioning: additive changes do not bump version; breaking changes → new versioned topic/schema
- JSON Schemas stored under `docs/contracts/events/schemas/`

## Commits & Docs
- Conventional Commits; keep diffs focused
- Definition of done includes ADR/service README/contracts updates
