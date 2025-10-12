Egress Gateway API Contract

Endpoint
- `POST /api/egress/call`

Method
- HTTP POST (internal RPC via gateway or direct service-to-service)

Request Schema and Validation
```json
{
  "url": "https://host/path",        // required, non-blank
  "method": "GET|POST|...",          // required, non-blank
  "headers": {"Accept": "..."},     // optional, sanitized
  "body": "...",                     // optional
  "config": {                          // optional resilience overrides
    "timeoutMs": 1000,
    "retries": 0,
    "retryBackoffMs": 0,
    "rateLimitRps": 10,
    "circuitBreaker": {"enabled": false}
  }
}
```

Response Schema
```json
{
  "envelope": {
    "success": true,
    "statusCode": 200,
    "headers": {"content-type": "application/json"},
    "body": "<string or base64>",
    "hash": "<body-hash>",
    "rateLimitStatus": {"limit": 100, "remaining": 99, "resetAfterSeconds": 60},
    "retryAdvice": {"retryable": false, "suggestedDelaySeconds": 0}
  },
  "durationMs": 150,
  "retryCount": 0,
  "traceId": "<trace-id>"
}
```

Error Mapping
- 400: invalid `url`/`method` or malformed overrides
- 5xx in envelope: remote error captured; map to `success=false` with `statusCode` and optional retry advice

Idempotency/Timeout/Retry/Rate Limits
- Idempotency is not enforced at this layer; callers must ensure safe semantics if required.
- Timeouts default from `patra.egress.resilience.defaultConfig.timeoutMs` with optional per-call override.
- Retries disabled by default; caps are enforced by `max.*` properties.
- Rate limits are applied globally per configuration; per-caller limits are planned.

Notes
- See `patra-egress-gateway/README.md` for orchestration and DTO types.

