# Sanitization Aspect Contract (AUTO Mode)

Version: 1.0.0

Purpose
- Provide an optional, opt‑in mechanism to sanitize log content automatically without requiring explicit `LogSanitizer` calls.

Scope & Layering
- Layer: Cross‑cutting concern delivered by the Spring Boot logging starter.
- Mechanism: AspectJ/Proxy‑based interception around application join points where logs are emitted or where sensitive parameters are common.

Modes & Properties
- Enabled via `papertrace.logging.sanitization.mode = AUTO`.
- Additional controls:
  - `papertrace.logging.sanitization.auto.base-packages` (list): limit weaving to specific packages.
  - `papertrace.logging.sanitization.auto.methods` (list): optional method name filters.
  - `papertrace.logging.sanitization.max-message-length`: truncate long strings before sanitizing.

Recommended Join Points (Conservative)
- Public methods in adapter/app layers (`..adapter..`, `..app..`) where inputs originate from users or external systems.
- Interceptor/logger wrappers for outbound HTTP/database logging, not the SLF4J `Logger` methods directly (intercepting `Logger` calls is brittle).

Behavior
- Before logging, apply `LogSanitizer` to:
  - String parameters annotated with `@Sanitize` (if provided), otherwise to selected parameters based on best‑effort heuristics (e.g., names: `password`, `token`, `body`).
  - Request/response payloads captured by client/server interceptors.
- Ensure idempotence: sanitizing twice must be safe.

MDC & Trace
- Aspect must not add or clear MDC; it operates only on message content.
- Works in tandem with `TraceContextFilter` and `Feign` interceptor contracts.

Performance Considerations
- Expect small overhead per call; keep AUTO mode disabled in production unless needed.
- Provide package/method filters and truncation to bound cost.

Non‑Goals
- Does not guarantee 100% PII removal in all custom formats; explicit `LogSanitizer` calls remain recommended for critical paths.

Related
- `contracts/utility-api.md` (`LogSanitizer`)
- `contracts/spring-boot-properties.md` (sanitization properties)
