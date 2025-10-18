# Trace Context Filter Contract

Version: 1.0.0

Purpose
- Establish the HTTP/system boundary where distributed tracing context is extracted/created and placed into MDC for downstream logs.
- Enforce consistent propagation headers and clearing semantics.

Scope & Layering
- Layer: Adapter (web) layer only. Implemented as Servlet `OncePerRequestFilter` (Spring MVC) and optionally `WebFilter` (WebFlux).
- Depends on: `TraceContextHolder` (contracts/utility-api.md) and `mdc-fields-reference.md` for key names.
- Does not depend on domain code.

Headers & MDC Mapping
- Inbound headers (configurable via `papertrace.logging.trace.*`):
  - `X-Trace-ID` → MDC `traceId`
  - `X-Correlation-ID` → MDC `correlationId`
- Trace IDs/spans:
  - If tracing is active (e.g., SkyWalking), obtain current span/parent span IDs.
  - Populate MDC keys: `traceId`, `correlationId` (if present or generated), `spanId` (if available), `parentSpanId` (if available).
- Outbound response (optional): set `X-Trace-ID` header for client visibility.

Lifecycle
1) Pre-handle:
   - Read headers per `spring-boot-properties.md`.
   - Resolve `DistributedTraceContext` via `TraceContextHolder.current()`; if absent and `auto-generate=true`, call `currentOrGenerate()`.
   - If `warn-on-missing=true` and context was created, log a WARN once per request.
   - Call `TraceContextHolder.populateMDC(context)`.
2) Filter chain proceeds.
3) Finally:
   - Always call `TraceContextHolder.clearMDC()`.

Ordering
- Must run at highest precedence before any application logging occurs (e.g., order = `Ordered.HIGHEST_PRECEDENCE + 10`).

Configuration
- Honors `papertrace.logging.trace.*` properties (enabled, auto-generate, header names, warn-on-missing).
- Honors MDC key remapping described in `spring-boot-properties.md#mdc-key-remapping`.

Error Handling
- Never throw to caller; log and continue.
- On invalid header values, ignore and generate a new context if configured.

Non‑Goals
- Does not create/close tracing spans; that is the responsibility of the tracing library.
- Does not manipulate business MDC fields.

Security Notes
- Do not trust inbound correlation IDs from untrusted sources; systems may choose to overwrite with server-generated values for external traffic.

Related
- `contracts/utility-api.md` (`TraceContextHolder`, `DistributedTraceContext`)
- `contracts/mdc-fields-reference.md`
- `contracts/spring-boot-properties.md`
