# patra-spring-boot-starter-core

Platform-level starter that delivers unified error resolution, tracing, and circuit-breaker capabilities for every microservice.

## 1. Module Scope
- **Responsibilities**: Resolve exceptions into structured `ErrorResolution` objects and enrich them with tracing, metrics, and circuit-breaker interceptors.
- **Primary Consumers**: All Spring Boot services (adapter layer) and extension starters (web/feign/mybatis).
- **Boundaries**: Focus on cross-cutting concerns. Business-specific logic should live in SPI extensions, not inside the starter.

## 2. Core Capabilities
- **Error Resolution Engine**: `ApplicationException` → `ErrorMappingContributor` → trait matching → naming heuristics → fallback.
- **Resolution Pipeline**: Tracing, circuit-breaker, and metrics interceptors with `@Order`-based extensibility.
- **SPI Extension Points**: `ErrorMappingContributor`, `ProblemFieldContributor`, `TraceProvider`, `ResolutionInterceptor`.
- **Observability**: Micrometer metrics for count, duration, slow resolutions, and circuit breakers.
- **ObjectMapper Bridge**: Synchronizes `JsonMapperHolder` with the Spring-managed `ObjectMapper`.

> See configuration tables, examples, and FAQ below. For comparisons with other starters, consult their READMEs (`patra-spring-boot-starter-*`, `patra-spring-cloud-starter-feign`).

## 3. Packages & Dependencies
- Packages: `error` (engine), `pipeline` (interceptors), `spi` (extension interfaces), `tracing`, `observation`.
- Dependencies: `patra-common`, Spring Boot, Micrometer, Resilience4j, Jackson.
- Avoid: Hardcoding business error codes or downstream service behavior inside the starter.

## 4. Usage & Configuration
- Maven dependency:
  ```xml
  <dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
  ```
- Required configuration:
  ```yaml
  patra:
    error:
      context-prefix: REG   # Required: service-level error prefix (REG/ING/GATE/etc.)
    tracing:
      header-names: [traceId, X-B3-TraceId, traceparent]
  ```
- Typical usage: inject `ErrorResolutionPipeline` in `@RestControllerAdvice` and render a `ProblemDetail` response.

## 5. Observability & Operations
- Metrics: `papertrace.error.resolution.duration/count/slow/circuit_breaker`.
- Slow-resolution monitoring: adjust `patra.error.observation.slow-threshold-ms` and optionally enable WARN logging.
- Circuit breaker: prevent error storms; monitor `circuit_breaker` metrics and tune `failure-rate-threshold`.

## 6. Testing Strategy
- Spring Boot tests injecting `ErrorResolutionPipeline` to verify ApplicationException/trait/heuristic flows.
- Simulate contributors: custom exception mappings, `TraceProvider`, interceptors.
- Performance baseline: benchmark high-frequency exceptions; add caching if necessary.

## 7. Roadmap & Risks
| Initiative | Status | Notes |
|------------|--------|-------|
| Web `ProblemDetail` integration | In progress | Keep response fields aligned with frontend contracts. |
| ErrorResolution caching | Planned | Watch memory usage and eviction policy. |
| Trait → status dynamic configuration | Planned | Requires config center support and gradual rollout. |
| TraceProvider extensions | Planned | Support SkyWalking/OpenTelemetry context extraction. |

Key risks: missing `context-prefix` yields UNKNOWN codes, aggressive circuit breaking impacts resolution, misordered SPI registrations alter priority.

## 8. References
- Other starters: `patra-spring-boot-starter-web/README.md`, `patra-spring-cloud-starter-feign/README.md`, `patra-spring-boot-starter-mybatis/README.md`, `patra-spring-boot-starter-expr/README.md`
- Web integration: `patra-spring-boot-starter-web/README.md`
- Feign error handling: `patra-spring-cloud-starter-feign/README.md`
- Error-handling guidelines: `docs/standards/platform-error-handling.md`
