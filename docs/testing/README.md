Testing Strategy

- Unit (JUnit5 + AssertJ + Mockito): fast, isolated, pure domain in domain modules; mock only ports in app layer.
- Adapter: @WebMvcTest + MockMvc for validation and error mapping.
- Infra: mock MyBatis mappers; test mappers/converters.
- Integration: Spring Boot Test + Testcontainers (MySQL, Redis, RocketMQ); verify vertical slices and outbox/idempotency.
- Naming: shouldXWhenY; use @DisplayName; Given-When-Then; prefer AssertJ.

How to run
- All tests: ./mvnw test
- Single test: ./mvnw -Dtest=ClassNameTest test

Layer Guidance
- Domain: pure JUnit tests; no Spring; validate aggregates and VOs
- Application: mock ports/repositories only; verify orchestration and transactions
- Adapter: `@WebMvcTest` with validation and ProblemDetail mapping assertions
- Infra: mapper/converter tests, repository behavior with lightweight fixtures

Integration Tests
- Use Testcontainers for DB/Redis/MQ; reuse containers with static fields
- Cover: REST → App → Domain → Infra → Outbox; idempotency and retries

Performance & Isolation
- Unit tests < 100ms; no external dependencies
- Integration tests < 30s; parallelize where safe; isolate data per test
