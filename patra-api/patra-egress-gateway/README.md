Purpose and Responsibilities
- Provide a unified gateway for outbound HTTP calls. Applies standardized envelope, logging, and future resilience (retry, circuit breaker, rate limit) while keeping callers simple.

Package Layout
- Adapter (inbound): `patra-egress-gateway-adapter`
- Application: `patra-egress-gateway-app`
- Domain: `patra-egress-gateway-domain`
- Infrastructure: `patra-egress-gateway-infra`
- Boot: `patra-egress-gateway-boot`

Domain Model
- Aggregate: `ResilienceConfigAggregate` merges caller overrides with system defaults/max (domain aggregate for guardrails).
- VOs: `HttpRequest`, `HttpResponse`, `ResilienceConfig`, `RateLimitStatus`, `RetryAdvice`, `ResponseEnvelope`.

Application Flow
1) Adapter receives RPC request and validates DTOs.
2) Orchestrator loads system config via `ConfigPort`, merges with caller config, executes `HttpClientPort`.
3) Builds `ResponseEnvelope` with whitelisted headers and retry advice; returns `ExternalCallResponseDTO` including traceId and duration.

APIs and Contracts
- Interface: `EgressEndpoint` exposes `POST /api/egress/call` for internal RPC.
- Feign client: `EgressGatewayClient` for in-repo consumers.
- DTOs: `ExternalCallRequestDTO` (url, method, headers, body, optional `ResilienceConfigDTO`) and `ExternalCallResponseDTO` (envelope, durationMs, retryCount, traceId).

Example Request
```
POST /api/egress/call
{
  "url": "https://api.example.com/data",
  "method": "GET",
  "headers": {"Accept": "application/json"},
  "body": null,
  "config": null
}
```

Example Response (happy path)
```
200 OK
{
  "envelope": {
    "success": true,
    "statusCode": 200,
    "headers": {"content-type": "application/json"},
    "body": "{\"result\":\"success\"}",
    "hash": "abc123",
    "rateLimitStatus": {"limit": 100, "remaining": 99, "resetAfterSeconds": 60},
    "retryAdvice": {"retryable": false, "suggestedDelaySeconds": 0}
  },
  "durationMs": 150,
  "retryCount": 0,
  "traceId": "trace-123"
}
```

Configuration
- Properties root: `patra.egress.*`
  - `patra.egress.global.rateLimit` → global RPS cap.
  - `patra.egress.resilience.defaultConfig.*` → default timeout, retries, retryBackoff, rateLimit, circuitBreaker*, whitelist headers.
  - `patra.egress.resilience.max.*` → caps for the same fields.
- Beans:
  - Rest client: `HttpClientConfig#restClient` with base timeouts (overridden per request).
  - Properties: `EgressProperties` encapsulates `GlobalProperties` + `ResilienceProperties` with nested `ResilienceConfigProperties`.

Error Handling
- Validation: `ExternalCallRequestDTO` requires non-blank `url` and `method`; invalid `ResilienceConfigDTO` yields 400 (adapter-level validation).
- Remote errors are returned as non-success envelopes (e.g., statusCode 500) with retry advice; ProblemDetail may be added via starters in future.

Observability
- Logs: traceId, URL, method, statusCode, durationMs. Sensitive headers are masked by `SensitiveHeaderMasker`.
- Metrics (recommend): 
  - Timer for end-to-end call duration.
  - Counters for statusCode classes and retry attempts.

Testing
- Adapter: `ExternalCallControllerTest` covers validation and response shape.
- Infra: `HttpClientAdapterTest`, `SensitiveHeaderMaskerTest` validate HTTP adapter and masking.
- Domain: value object tests for envelope/rate-limit/retry advice.

Run Locally
- `./mvnw -pl patra-egress-gateway/patra-egress-gateway-boot -am spring-boot:run`

Open TODOs
- Implement circuit breaker, retries with backoff, and rate limiting; propagate upstream-provided rate-limit hints into envelope.
