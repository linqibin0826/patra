Conventions & Standards

- Language/Format: Java 21, UTF-8, 4-space indent, 120-char line width.
- Packages: com.patra.*; Maven groupId com.papertrace.
- Hexagonal/DDD: adapter → app → domain ← infra; domain is pure Java.
- Immutability: Prefer records for VOs; Lombok for POJOs; MapStruct for mapping.
- Logging: @Slf4j; parameterized; never log secrets; include trace IDs.
- Naming: *Orchestrator, *Command, *Port, *Impl.
- Integration: timeouts/retries/circuit/rate-limits defined; event-driven first with outbox and idempotency.

See repository root README.md and AGENTS.md for full guidelines.

