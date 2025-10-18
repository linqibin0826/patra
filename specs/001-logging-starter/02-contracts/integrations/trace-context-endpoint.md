# Trace-Context Actuator Endpoint Contract (Optional)

Version: 1.0.0

Purpose
- Provide a simple diagnostics endpoint to verify trace context propagation, header names, and MDC key remapping at runtime.
- Intended for operations/support to validate configuration during incidents and for smoke checks in lower environments.

Status & Exposure
- Optional: enabled only when the logging starter exposes it and when `management.endpoints.web.exposure.include` contains `loggers,trace-context`.
- Default path: `GET /actuator/loggers/trace-context` (under the “loggers” group for operational affinity).

Security
- Same auth model as Spring Boot Actuator endpoints.
- Recommended: restrict in production (IP allowlist or role-based access) and never expose in internet-facing gateways.

Resource
- Method: `GET`
- Path: `/actuator/loggers/trace-context`
- Query parameters:
  - `include=mdc` (optional): if present, include a snapshot of the current request thread’s MDC map (subject to remapping). See notes.

Response (application/json)
```
{
  "enabled": true,
  "tracingVendor": "SkyWalking",          // free-text descriptor
  "headers": {
    "trace": "X-Trace-ID",
    "correlation": "X-Correlation-ID"
  },
  "mdcKeys": {
    "traceId": "traceId",                 // effective MDC key name in use
    "correlationId": "correlationId",
    "spanId": "spanId",
    "parentSpanId": "parentSpanId",
    "service": "service",
    "environment": "environment",
    "hostname": "hostname",
    "clientIp": "clientIp",
    "requestUri": "requestUri",
    "httpMethod": "httpMethod"
  },
  "status": {
    "traceAvailable": true,                // vendor API returns a non-empty trace id
    "autoGenerate": true,                  // mirrors papertrace.logging.trace.auto-generate
    "warnOnMissing": true                  // mirrors papertrace.logging.trace.warn-on-missing
  },
  "current": {
    "traceId": "abc123-def456",          // present only if include=mdc and available
    "correlationId": "batch-20251015-001",
    "spanId": "0.1.2",
    "parentSpanId": "0.1"
  }
}
```

Semantics
- `mdcKeys`: the effective MDC key remapping resolved from `papertrace.logging.mdc.field-names.*`.
- `status.traceAvailable`: true if the tracing vendor API reports a usable trace id at call time.
- `current`: returned only when `include=mdc` and values are available within this endpoint’s request thread.

Notes & Caveats
- The `current` block reflects MDC on the endpoint handling thread only; it does not inspect other worker threads. It is useful as a quick smoke test that remapping and inbound filters work end-to-end.
- The endpoint must not include sensitive MDC fields; sanitize or omit any non-standard keys.

Error Cases
- 404 Not Found: endpoint not exposed or disabled.
- 200 OK with `enabled=false`: starter present but endpoint disabled by configuration.

Related
- `contracts/spring-boot-properties.md` — trace properties and MDC remapping
- `contracts/mdc-fields-reference.md` — standardized MDC keys
- `contracts/integrations/trace-context-filter.md` — HTTP boundary propagation
