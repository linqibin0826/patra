# Feign Interceptor Contract

Version: 1.0.0

Purpose
- Propagate distributed trace and correlation IDs on outbound HTTP requests via Feign.
- Provide standardized, sanitized logging for external API calls (duration, status, optional bodies/headers).

Scope & Layering
- Layer: Infrastructure/adapters (HTTP clients).
- Component: `feign.RequestInterceptor` and (optionally) `feign.Logger`/`Client` wrapper for timing.
- Depends on: `TraceContextHolder`, `LogSanitizer`, and `ApiCallLogger` (when implemented).

Behavior
1) Propagation:
   - Read current `DistributedTraceContext` via `TraceContextHolder.currentOrGenerate()`.
   - Inject headers per `spring-boot-properties.md`:
     - `X-Trace-ID` = `traceId`
     - `X-Correlation-ID` = `correlationId` (if present)
2) Logging:
   - Measure call duration; log at:
     - INFO for success (2xx) with `service`, `url`, `method`, `status`, `durationMs`.
     - WARN for client errors (4xx) and retries.
     - ERROR for server errors (5xx) with error message.
   - Respect properties under `papertrace.logging.api.*` (enable/disable bodies, headers, max lengths).
   - Sanitize request/response bodies and headers via `LogSanitizer`.

MDC Interaction
- Do not mutate MDC keys other than reading current context.
- All logs emitted should include existing MDC populated by the HTTP boundary filter.

Configuration
- Honors:
  - `papertrace.logging.trace.header-name`
  - `papertrace.logging.trace.correlation-header`
  - `papertrace.logging.api.*` (request/response body logging, truncation, thresholds, exclusions)
- Honors MDC key remapping (see `spring-boot-properties.md#mdc-key-remapping`).

Error Handling
- On interceptor failure, avoid throwing; prefer logging and continuing the request unless propagation is mandatory.

Non‑Goals
- Does not implement retry logic or circuit breaking.
- Does not add business MDC fields.

Related
- `contracts/utility-api.md` (`TraceContextHolder`, `LogSanitizer`)
- `contracts/mdc-fields-reference.md`
- `contracts/spring-boot-properties.md`
