Purpose and Scope
- Reusable domain abstractions, error contracts, and JSON utilities at the core of the hexagonal architecture.

Key Capabilities
- Domain: `AggregateRoot`, `ReadOnlyAggregate`, `DomainEvent`.
- Error model: `ErrorCodeLike`, `HttpStdErrors`, `ApplicationException`, `DomainException`, `ErrorTrait`.
- JSON: `JsonMapperHolder`, `JsonNormalizer`, `JsonNodeMappings` for canonical JSON and hashing inputs.
- Enums/Utils: cross-service enums and `HashUtils`.

Boundaries
- Pure Java module: avoid Spring/MyBatis; no business-specific aggregates/DTOs.

Layout
- src/main/java/com/patra/common/{domain,error,json,enums,util}

Usage
- Add dependency (if not inherited from parent POM):
  <dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-common</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
- With `patra-spring-boot-starter-core`, `JsonMapperHolder` binds to Spring’s `ObjectMapper` automatically.

Testing
- Validate aggregate/event semantics, JSON normalization edge cases, error-code mapping, and enum parsing.
