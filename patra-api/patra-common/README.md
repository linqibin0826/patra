# patra-common

Provides reusable domain abstractions, error contracts, and JSON normalization utilities that sit at the innermost layer of our hexagonal architecture.

## 1. Module Scope
- **Responsibilities**: Hosts aggregate root base classes, domain events, a unified error-code model, JSON normalization, and core enums/utilities.
- **Primary Consumers**: Every `patra-*` domain module and in-house starters (core/web/feign).
- **Architectural Boundaries**: Depends only on the JDK, Jackson, and Hutool. Do not add Spring/MyBatis or embed business-specific semantics here.

## 2. Core Capabilities
- **Domain Modeling Abstractions**: `AggregateRoot`, `ReadOnlyAggregate`, `DomainEvent`, and related helpers keep aggregates lean and testable.
- **Error Model**: `ErrorCodeLike`, `HttpStdErrors`, `ApplicationException`, `DomainException`, and `ErrorTrait` form the foundation of our error resolution pipeline.
- **JSON Normalization**: `JsonMapperHolder` standardizes access to a shared `ObjectMapper`, while `JsonNormalizer` produces canonical JSON and hashing inputs.
- **Shared Enums**: Cross-service enumerations for source, priority, and configuration scope with consistent JSON serialization rules.
- **Utilities**: `HashUtils` wraps SHA-256 to power idempotency keys and signature generation.

> For deeper guidance—including tables and examples—see `docs/modules/common/deep-dive.md`.

## 3. Package Layout & Dependencies
```
patra-common/
  └─ src/main/java/com/patra/common/
       domain/    error/    json/    enums/    util/
```
- **Dependencies**: JDK 21, Jackson, and Hutool (managed by the parent POM).
- **Forbidden**: Adding framework dependencies, storing business aggregates/DTOs, or shipping runtime configuration in this module.

## 4. Usage & Configuration
- **How to Include**: Child modules inherit `patra-parent` and receive this dependency automatically. For standalone usage, declare it explicitly:
  ```xml
  <dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-common</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
  ```
- **Required Configuration**: None. When combined with `patra-spring-boot-starter-core`, `JsonMapperHolder` is automatically wired to the Spring-managed `ObjectMapper`.
- **Startup Steps**: Library only—no standalone runtime.

## 5. Observability & Operations
- This module ships no runtime process, yet its error codes, `JsonNormalizer`, and `HashUtils` impact service observability.
- When integrating the `ErrorResolutionEngine` and Micrometer metrics, verify the `HttpStdErrors` prefix configuration within each service.

## 6. Testing Strategy
- Aggregates & Events: Validate event collection, immutability, and guard assertions.
- JsonNormalizer: Cover null handling, array deduplication, lenient boolean/time parsing, and exceptional branches.
- Error Codes: Assert that `HttpStdErrors` produces the expected HTTP status and domain-specific code segments.
- Core Enums: Test alias parsing and fallbacks for invalid input.

## 8. Further Reading
- Deep dive: `docs/modules/common/deep-dive.md`
- Error-handling standard: `docs/standards/platform-error-handling.md`
- Cross-service error best practices: `docs/standards/cross-service-error-best-practices.md`
- Data ingestion flow highlighting hash/JSON usage: `docs/process/ingest-dataflow.md`
