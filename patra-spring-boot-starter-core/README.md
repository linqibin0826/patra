# patra-spring-boot-starter-core

## Purpose
Core auto-configuration for unified error handling, tracing, metrics, and JSON utilities. Keeps domain/app code clean while enabling consistent cross-cutting behavior.

## Auto-Configuration
Registered via `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`:
- `com.patra.starter.core.json.autoconfig.JacksonAutoConfiguration`
- `com.patra.starter.core.error.config.CoreErrorAutoConfiguration`

## Beans and Conditions
- `ObjectMapperProvider` (Jackson bridge)
  - Exposed if `ObjectMapper` on classpath.
  - Bridges Spring-managed mapper into `JsonMapperHolder` (from `patra-common`).

- Error pipeline (enabled by default)
  - `ErrorResolutionEngine` → `DefaultErrorResolutionEngine`
  - `ErrorResolutionPipeline` with ordered interceptors:
    - `MetricsInterceptor` (when `patra.error.observation.enabled=true` and Micrometer present)
    - `TracingInterceptor` (always, uses `TraceProvider`)
    - `CircuitBreakerInterceptor` (when circuit breaker bean present)
  - `TraceProvider` → `HeaderBasedTraceProvider`
  - Optional `CircuitBreaker` bean named `errorResolutionCircuitBreaker` (when `patra.error.circuit-breaker.enabled=true`)
  - `HttpStdErrors.Group` (prefix from `patra.error.context-prefix`, default `UNKNOWN`)

## Tracing
- `TracingProperties.header-names` controls inbound header lookup order for `traceId`.

## Logging
- Bundled `logback-spring.xml` with SkyWalking layout and async appenders.

## Properties (key subset)
```yaml
patra:
  error:
    enabled: true
    context-prefix: INGEST   # e.g., REG, INGEST, EGRESS
    engine:
      max-cause-depth: 10
      enable-trait-mapping: true
      enable-naming-heuristic: true
    observation:
      enabled: true
      slow-threshold-ms: 200
      log-slow-resolution: true
    circuit-breaker:
      enabled: true
      failure-rate-threshold: 50.0
      minimum-number-of-calls: 20
      sliding-window-size: 50
      permitted-calls-in-half-open-state: 5
      wait-duration-in-open-state: 30s
  tracing:
    header-names: [traceId, X-B3-TraceId, traceparent]
```

## Usage Example
Throw application exceptions with unified codes; the Web starter will render RFC7807.
```java
var http = HttpStdErrors.of("ING");
throw new ApplicationException(http.UNPROCESSABLE(), "Invalid input");
```

## SPIs
- `ProblemFieldContributor`, `ErrorMappingContributor` for extending payload fields and mapping strategies.

## Notes
- The Web starter (`patra-spring-boot-starter-web`) provides the REST exception handler that converts resolutions to `ProblemDetail`.
