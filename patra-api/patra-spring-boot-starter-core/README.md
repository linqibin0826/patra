Purpose and Responsibilities
- Core auto-configurations for error resolution pipeline, tracing, metrics, and JSON utilities.

Key Components
- ErrorResolutionEngine and DefaultErrorResolutionEngine with interceptor pipeline (Tracing, Metrics, CircuitBreaker hooks).
- ProblemFieldContributor and ErrorMappingContributor SPIs for services to add mappings.
- Tracing: HeaderBasedTraceProvider, TracingProperties.
- JSON: JacksonAutoConfiguration, ObjectMapperProvider, JsonNodeSupport.

Auto-Configuration
- Meta-inf imports register CoreErrorAutoConfiguration and Jackson auto-config.

Usage
- Include starter to enable unified ProblemDetail mapping and tracing headers across services.

Configuration Properties
- `patra.error.enabled` (default true)
- `patra.error.context-prefix` (e.g., REG, INGEST)
- `patra.error.engine.max-cause-depth` (default 10)
- `patra.error.engine.enable-trait-mapping` (default true)
- `patra.error.engine.enable-naming-heuristic` (default true)
- `patra.error.observation.enabled` (default true)
- `patra.error.observation.slow-threshold-ms` (default 200)
- `patra.error.observation.log-slow-resolution` (default true)
- `patra.error.circuit-breaker.*` (failureRateThreshold, minimumNumberOfCalls, slidingWindowSize, permittedCallsInHalfOpenState, waitDurationInOpenState)
- `patra.tracing.header-names` (default [traceId, X-B3-TraceId, traceparent])
