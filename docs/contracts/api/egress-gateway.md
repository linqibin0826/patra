# Egress Gateway API Contract

## Endpoint
- `POST /api/egress/call`

## Method
- HTTP POST (internal RPC via gateway or direct service-to-service)

## Request Schema and Validation
```json
{
  "url": "https://host/path",        // required, non-blank
  "method": "GET|POST|...",          // required, non-blank
  "headers": {"Accept": "..."},     // optional, sanitized
  "body": "...",                     // optional
  "config": {                          // optional resilience overrides (seconds-based)
    "timeoutSeconds": 30,
    "maxRetries": 0,
    "retryBackoffSeconds": 0,
    "rateLimit": 10,
    "circuitBreakerThreshold": 10,
    "circuitBreakerWindowSeconds": 60,
    "responseHeaderWhitelist": ["Content-Type", "Retry-After"]
  }
}
```

## Response Schema
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

## Error Mapping
- 400: invalid `url`/`method` or malformed overrides
- 5xx in envelope: remote error captured; map to `success=false` with `statusCode` and optional retry advice

## Idempotency/Timeout/Retry/Rate Limits
- Idempotency is not enforced at this layer; callers must ensure safe semantics if required.
- Timeouts default from `patra.egress.resilience.default.timeout-seconds` with optional per-call override; capped by `patra.egress.resilience.max.timeout-seconds`.
- Retries default from `patra.egress.resilience.default.max-retries`; capped by `patra.egress.resilience.max.max-retries`.
- Backoff defaults from `patra.egress.resilience.default.retry-backoff-seconds`; capped by `patra.egress.resilience.max.retry-backoff-seconds`.
- Circuit breaker thresholds/windows default from corresponding `default.*` keys; capped by `max.*` keys.
- Rate limits apply globally per configuration; per-caller limits are planned (`patra.egress.global.rate-limit`).

## Notes
- See `patra-egress-gateway/README.md` for orchestration and DTO types.
