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

## Naming Conventions

| Element | Convention | Example | Notes |
|---------|-----------|---------|-------|
| **Package** | lowercase, no separator | `com.papertrace.ingest.domain` | - |
| **Class** | UpperCamelCase, noun | `BatchPlan`, `Provenance` | - |
| **Interface** | UpperCamelCase, noun/adjective | `ProvenancePort`, `Serializable` | No `I` prefix |
| **Method** | lowerCamelCase, verb | `createPlan()`, `isEnabled()` | Boolean: `is/has/can` |
| **Variable** | lowerCamelCase, noun | `provenanceId`, `batchSize` | - |
| **Constant** | UPPER_SNAKE_CASE | `MAX_BATCH_SIZE`, `DEFAULT_TIMEOUT` | `static final` immutable |
| **Enum Type** | UpperCamelCase | `BatchStatus` | - |
| **Enum Value** | UPPER_SNAKE_CASE | `PENDING`, `RUNNING`, `COMPLETED` | - |

**DDD/Hexagonal Naming Patterns:**

| Layer | Pattern | Example |
|-------|---------|---------|
| Domain | No suffix | `BatchPlan`, `Provenance`, `LiteratureId` |
| Domain - Port | `Port` | `ProvenancePort`, `LiteraturePort` |
| Application | `Orchestrator` | `BatchPlanOrchestrator` |
| Infrastructure | `RepositoryImpl` | `ProvenanceRepositoryImpl` |
| Adapter - Controller | `Controller` | `ProvenanceController` |
| Adapter - Job | `Job` | `IngestScheduledJob` |
| DTO | `DTO/Command/Request/Response` | `CreateProvenanceCommand` |
| DO | `DO` | `ProvenanceDO` |

---

## Logging Standards

**Framework:** SLF4J with parameterized messages, English only, NO sensitive data (passwords, tokens, PII)

**Log Level Usage:**

| Level | When to Use | Production | Example |
|-------|-------------|-----------|---------|
| **ERROR** | Business failures, exceptions, data inconsistency | ✅ ON | External API fails after retries |
| **WARN** | Recoverable issues, degradation, deprecated usage | ✅ ON | Circuit breaker opens, retry triggered |
| **INFO** | Key business events, app lifecycle | ✅ ON | Batch completed, user registered |
| **DEBUG** | Method flow, decisions, cache hits | ⚠️ On-demand | Creating plan with params X, Y |
| **TRACE** | Detailed execution path, loop internals | ❌ OFF | Processing element [i], state=X |

**Must Always Log:**
- Application startup/shutdown
- All exceptions with **full stack trace** (ERROR)
- External API calls (DEBUG: request/response, ERROR: failures)
- Database operation failures (ERROR)
- Authentication/authorization events (INFO: success, WARN: failure)
- Batch/job results with counts and duration (INFO)

**Examples:**

```java
// ✅ Good: Descriptive messages with business context
log.info("Completed batch processing of {} literature records in {}ms using batch [{}]",
    recordCount, duration, batchId);

log.error("Failed to retrieve literature from PubMed for PMID [{}] after attempt {}/3: API timeout",
    pmid, attemptCount, exception);

log.info("Starting daily literature ingestion from {} sources: PubMed, EPMC, CrossRef",
    sourceCount);

// ❌ Bad: Simple field listing without context
log.info("Batch processing completed: batchId={}, records={}, duration={}ms",
    batchId, recordCount, duration);

// ❌ Bad: String concatenation, missing context
log.info("Batch completed");  // Which batch? How many records? What was the outcome?
log.error("Error: " + e.getMessage());  // No stack trace! No business context!
```

**Distributed Tracing:** Always include `traceId` and `spanId` in log pattern:
```yaml
logging:
  pattern:
    level: '%5p [${spring.application.name},%X{traceId},%X{spanId}]'
```

---

## JavaDoc & Comments

**When JavaDoc is Required:**
- ✅ All `public` classes
- ✅ All `public`/`protected` methods
- ⚠️ Simple getters/setters can omit
- ⚠️ Override methods can omit if no new behavior

**Format (tag order matters):**

```java
/**
 * Brief summary in one sentence ending with period.
 *
 * Optional detailed description in multiple paragraphs.
 * Explain WHY, not WHAT.
 *
 * @param paramName parameter description
 * @return return value description
 * @throws ExceptionType when this exception is thrown
 * @since 1.0
 */
public BatchPlan createPlan(String provenanceId) {
    // Implementation
}
```

**Inline Comments (explain WHY, not WHAT):**

```java
// ✅ Good: Explains reasoning
// Retry 3 times to handle transient network failures
@Retry(maxAttempts = 3)

// Use LinkedHashMap to preserve insertion order for display
Map<String, String> metadata = new LinkedHashMap<>();

// ❌ Bad: States the obvious
// Set name to "PubMed"
String name = "PubMed";

// Loop through list
for (Literature lit : literatureList) { }
```

**TODO Format:**

```java
// TODO: https://github.com/papertrace/api/issues/123 - Add pagination support
// TODO: JIRA-456 - Replace with async processing after Q2 2025
```

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
