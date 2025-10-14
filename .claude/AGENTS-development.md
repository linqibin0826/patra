# Development Guidelines

This file contains coding standards, development workflow, common libraries, and build commands for the Papertrace project.

---

## Coding Standards

- **Code Style**: All code MUST follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- **POJOs**: `record` for immutables, Lombok for mutables
- **Error Handling**: Domain exceptions → App exceptions → HTTP (ProblemDetail)
- **Consistency**: Outbox pattern, idempotency keys, optimistic locking
- **Performance**: Avoid N+1 queries, batch operations, proper indexing

---

## Logging Standards

- **Framework**: SLF4J parameterized English logs, no sensitive data (passwords, tokens, PII)
- **Log Levels**: ERROR (failures), WARN (recoverable issues), INFO (key events), DEBUG (detailed flow), TRACE (diagnostics)
- **Always Log**:
  - Application startup/shutdown events
  - All exceptions with full context (ERROR level)
  - External API calls (request/response at DEBUG, errors at ERROR)
  - Database operations failures
  - Authentication/authorization events (success at INFO, failures at WARN)
  - Key business operations (batch processing, data ingestion, parsing results)
- **Troubleshooting**: Include sufficient DEBUG/TRACE logs for method entry/exit, decision branches, and variable states

---

## Development Workflow

**⚠️ IMPORTANT**: Read target module's README.md FIRST before any code reading/modification!

1. **Confirm**: Module, contracts, ports, DTOs, signatures
2. **Domain**: Pure Java entities, aggregates, VOs, ports
3. **App**: Orchestrators with transactions (no business rules)
4. **Infra**: MyBatis-Plus repos, MapStruct mappers, DOs
5. **Adapter**: Controllers/Jobs with `@Valid`, error mapping
6. **Self-check**: `mvn -q compile && mvn test`
7. **Handoff**: Minimal diff with key decisions documented

---

## Common Libraries & Starters

**Reuse first**: `patra-common` (base classes, utilities), `patra-spring-boot-starter-*` (core/web/mybatis configs), Hutool (cn.hutool)
**When adding deps**: Check `patra-common`/Hutool first, avoid deps in `domain` layer, coordinate for major deps

---

## Build & Test Commands

```bash
mvn -q -DskipTests compile                    # Compile check (fast)
cd patra-{service}/patra-{service}-boot && mvn spring-boot:run  # Run service
mvn test                                       # All tests
mvn test -pl patra-registry                    # Module tests
mvn clean install [-DskipTests]                # Full build
```
