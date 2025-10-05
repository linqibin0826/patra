---
inclusion: always
---

# Coding Conventions

## Dependency Rules

### Strict Layer Boundaries

- **Domain layer**: MUST NOT depend on any framework (Spring, MyBatis, etc.). Pure Java only.
- **Application layer**: May use Spring annotations for transaction/security, but business logic stays framework-agnostic.
- **Adapter layer**: Inbound only. No outbound adapters (those belong in infra).
- **Infrastructure layer**: Implements domain ports. Can use any framework needed.

### Dependency Direction

```
adapter → app → domain ← infra
```

Never reverse this flow. Infra depends on domain ports, not the other way around.

## Domain-Driven Design Patterns

### Aggregates
- Use `Aggregate` suffix for all aggregate roots
- Aggregates enforce business invariants
- Keep aggregates small and focused
- Access other aggregates only by ID reference

### Value Objects
- Immutable by design
- Located in `domain/model/vo/`
- Implement equality based on attributes, not identity

### Domain Events
- Located in `domain/event/`
- Immutable event objects
- Past tense naming (e.g., `TaskCreatedEvent`, `PlanCompletedEvent`)

### Domain Ports
- Interfaces defined in `domain/port/`
- Implemented in `infra` layer
- Examples: `*Repository`, `*Port`, `*Publisher`

## Object Mapping

### MapStruct Conventions

- All DO ↔ Domain mappings use MapStruct interfaces
- Converters named `*Converter` (e.g., `TaskConverter`, `PlanConverter`)
- Use `@Named` methods for complex transformations
- Avoid Spring utilities in MapStruct - keep it pure Java
- JSON conversions delegate to `JsonNodeMappings` from `patra-common`

### Common Patterns

```java
@Mapper(componentModel = "spring")
public interface TaskConverter {
    TaskAggregate toDomain(TaskDO taskDO);
    TaskDO toDO(TaskAggregate aggregate);
    
    @Named("statusCodeToEnum")
    static TaskStatus statusCodeToEnum(String code) {
        return TaskStatus.fromCode(code);
    }
}
```

## Logging Standards

### Log Prefix Format

All logs use two-segment prefix: `[MODULE][LAYER]`

- **MODULE**: Service name (INGEST, REGISTRY, GATEWAY, COMMON)
- **LAYER**: Architecture layer (ADAPTER, APP, DOMAIN, INFRA, API)

Example: `[INGEST][APP] Task publish started (taskId=123)`

### Log Levels

- **ERROR**: Unrecoverable errors requiring intervention (external service failures, data inconsistency)
- **WARN**: Recoverable issues or potential risks (retry exhaustion warnings, degraded mode)
- **INFO**: Important milestones (job completion, configuration refresh, batch closure)
- **DEBUG**: High-frequency internal state transitions (upsert branches, lease competition)
- **TRACE**: Not used (disabled by default)

### Best Practices

- Use parameterized logging: `log.info("Task {} completed in {}ms", taskId, duration)`
- Never log passwords, tokens, or PII
- Large objects (>2KB): log summary only (size, hash)
- Include `traceId` for distributed tracing
- Avoid logging in tight loops - aggregate and log summary

## Error Handling

### Error Code Structure

Format: `<PREFIX>-<HTTP_STATUS>` or `<PREFIX>-<BUSINESS_CODE>`

- **0xxx segment**: HTTP-aligned errors (e.g., `REG-0404`, `ING-0422`)
  - Use `HttpStdErrors.Group` injected by context prefix
  - Never hardcode 0xxx in error enums
- **1xxx+ segment**: Business errors (e.g., `ING-1201`, `REG-1407`)

### ProblemDetail Output

All errors output RFC 7807 ProblemDetail with:
- `code`: Error code (e.g., `ING-1201`)
- `status`: HTTP status
- `path`: Request path
- `timestamp`: ISO-8601 timestamp
- `traceId`: Distributed trace ID

### Exception Hierarchy

- Domain exceptions extend `ApplicationException`
- Never return `null` - throw appropriate domain exception
- Adapter layer catches and maps to ProblemDetail
- Use `ErrorMappingContributor` for custom mappings

## Configuration

### Property Management

- Sensitive data (passwords, keys): Nacos or environment variables ONLY
- Never commit secrets to repository
- Use `patra.error.context-prefix` for error code prefixes
- Local overrides: `application-local.yaml` (gitignored)

### Configuration Files

- Main config: `application.yaml`
- Error config: `{service}-error-config.yaml`
- Import pattern: `spring.config.import: optional:classpath:ingest-error-config.yaml`

## Testing

### Test Organization

- Domain tests: Verify invariants, idempotency, business rules
- App tests: Use case orchestration, transaction boundaries
- Adapter tests: Protocol conversion, error mapping
- Infra tests: Persistence, external integrations
- Integration tests: End-to-end scenarios

### Test Naming

- Test methods: `should{ExpectedBehavior}_when{Condition}`
- Example: `shouldThrowException_whenWindowExceedsLimit`

## Documentation

### Language Standards

- **All documentation MUST be written in Chinese (中文)**
- This includes:
  - README files
  - Code comments
  - Commit messages
  - Architecture documents
  - API documentation
  - Inline Javadoc
- Exception: Technical terms and code identifiers remain in English

### Documentation Structure

- Keep module README concise, refer to deep-dive docs for details
- Deep-dive documentation: `docs/modules/{module}/deep-dive.md`
- Update `docs/README.md` index when adding new documentation
- Include status indicators: 草案/待补充/已验证

## Code Quality

### General Rules

- Prefer composition over inheritance
- Keep methods small and focused (< 20 lines ideal)
- Use meaningful variable names (no single letters except loop counters)
- Document complex business logic with comments
- Use `@Slf4j` for logging (Lombok)
- Use `@RequiredArgsConstructor` for dependency injection

### Avoid

- God classes (> 500 lines)
- Deep nesting (> 3 levels)
- Magic numbers (use constants or enums)
- Mutable static state
- Catching generic `Exception` without re-throwing
