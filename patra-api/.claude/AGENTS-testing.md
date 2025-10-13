# Testing Strategy

This file contains testing guidelines, organization structure, and best practices for the Papertrace project.

---

## Unit Tests (Domain & Application)

### Domain Layer (`patra-{service}-domain`)
- Test in isolation, pure Java, NO mocks needed
- Focus: Aggregate behavior, business rules, value object validation
- Example: `ProvenanceTest`, `BatchPlanTest`

### Application Layer (`patra-{service}-app`)
- Test orchestration logic
- Mock domain and infrastructure ports
- Verify transaction boundaries and error translation

---

## Integration Tests (Boot Module ONLY)

### Integration Tests (`patra-{service}-boot`)
- **Location**: Only in boot module (has main application class and full context)
- **Infrastructure tests**: Use TestContainers for DB/Redis/MQ
- **API tests**: Use MockMvc or RestAssured
- Test repository implementations, DO ↔ Domain mapping
- Verify `@Valid` constraints and error mapping

**Why boot module?**: Main application class in boot assembles full Spring context needed for integration tests

---

## Architecture Validation

### ArchUnit (recommend in boot module)
- Enforce layer dependency rules (Adapter → App → Domain ← Infra)
- Verify no framework dependencies in domain layer
- Check naming conventions (`*Orchestrator`, `*Port`, `*RepositoryImpl`)

---

## Test Organization

```
patra-{service}/
├─ patra-{service}-domain/
│  └─ src/test/java/          # Unit tests (pure Java)
├─ patra-{service}-app/
│  └─ src/test/java/          # Unit tests (mock ports)
├─ patra-{service}-infra/
│  └─ src/test/java/          # Optional: mapper tests
└─ patra-{service}-boot/
   └─ src/test/java/          # Integration tests + ArchUnit
```
