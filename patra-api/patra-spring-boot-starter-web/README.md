# patra-spring-boot-starter-web

Provides Spring MVC integrations for unified error responses and type conversion, exposing the core starter's `ErrorResolution` output as RFC 7807 `ProblemDetail` documents.

## 1. Module Scope
- **Responsibilities**: Global Web exception handling, validation error aggregation, `Forwarded` header parsing, and standard field contribution.
- **Primary Consumers**: All Servlet/MVC-based microservices (for example registry and ingest-adapter services).
- **Architectural Boundary**: Handles Web cross-cutting concerns only; business controllers remain responsible for domain validation and response composition.

## 2. Key Capabilities
- **ProblemDetail adaptation**: Maps `ErrorResolution` to the RFC 7807 structure (code, path, timestamp, traceId).
- **JSR-380 validation formatting**: Collects field errors and masks sensitive values.
- **Extension SPI**: `ProblemFieldContributor` adds custom response properties.
- **Type conversion**: Supplies global converters such as `String -> ProvenanceCode`.
- **Proxy metadata extraction**: Parses `Forwarded`/`X-Forwarded-*` headers to recover the client address.

This README serves as the authoritative documentation for the module. For comparisons with other starters consult their respective READMEs (`patra-spring-boot-starter-*`, `patra-spring-cloud-starter-feign`, etc.).

## 3. Package Layout and Dependencies
- Primary packages: `problem` (ProblemDetail assembly), `advice` (exception handling), `converter` (type conversion), `forwarded`.
- Dependencies: `patra-spring-boot-starter-core`, Spring Boot Web, Jakarta Validation.

## 4. Usage and Configuration
- Maven dependency:
  ```xml
  <dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-web</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
  ```
- Sample configuration:
  ```yaml
  patra:
    error.context-prefix: REG
    web:
      problem:
        enabled: true
        type-base-url: https://errors.papertrace.local/
        include-stack: false
  ```
- The `GlobalExceptionHandler` bean is enabled by default; customise behaviour by implementing `ProblemResponseCustomizer` if required.

## 5. Observability and Operations
- The `ProblemDetail` payload includes `code`, `path`, `traceId`, and `timestamp` by default. Ensure structured logs carry the same trace identifier.
- Combine metrics from the core starter to analyse Web-layer error distribution.
- Configure reverse-proxy trust lists carefully so that forwarded headers cannot be spoofed.

## 6. Testing Strategy
- Use `@WebMvcTest` to verify exception mapping, validation error formatting, and custom extension fields.
- Mock `Forwarded` headers to validate client address resolution.
- Assert that the `ProblemDetail` output stays aligned with frontend contracts (field naming and ordering).

## 7. Roadmap and Risks
| Item | Status | Risk / Notes |
|------|--------|--------------|
| ProblemDetail field templating | In progress | Standardise extension fields across services to avoid duplication. |
| Localised error messages | Planned | Requires a `MessageSource`; consider caching and performance impact. |
| Configurable serialization fields | Planned | Allow services to opt into stack/detail output selectively. |

**Risks**: Inconsistent error fields between upstream/downstream services, misconfigured proxy trust chains for `Forwarded` headers, duplicate exception handling in business layers.

## 8. References
- Other starters: `patra-spring-boot-starter-core/README.md`, `patra-spring-cloud-starter-feign/README.md`, `patra-spring-boot-starter-mybatis/README.md`, `patra-spring-boot-starter-expr/README.md`
- Core error resolution: `patra-spring-boot-starter-core/README.md`
- Error handling standard: `docs/standards/platform-error-handling.md`
