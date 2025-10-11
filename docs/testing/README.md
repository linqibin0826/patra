Testing Strategy

- Unit (JUnit5 + AssertJ + Mockito): fast, isolated, pure domain in domain modules; mock only ports in app layer.
- Adapter: @WebMvcTest + MockMvc for validation and error mapping.
- Infra: mock MyBatis mappers; test mappers/converters.
- Integration: Spring Boot Test + Testcontainers (MySQL, Redis, RocketMQ); verify vertical slices and outbox/idempotency.
- Naming: shouldXWhenY; use @DisplayName; Given-When-Then; prefer AssertJ.

How to run
- All tests: ./mvnw test
- Single test: ./mvnw -Dtest=ClassNameTest test

