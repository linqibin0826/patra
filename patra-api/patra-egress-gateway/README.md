# patra-egress-gateway

## Overview
- Provides a unified gateway for outbound HTTP calls. Applies standardized envelope, logging, and future resilience (retry, circuit breaker, rate limit) while keeping callers simple.

## Module Layout
- `patra-egress-gateway-adapter` — inbound REST (internal RPC endpoint)
- `patra-egress-gateway-app` — orchestration of external calls and envelope building
- `patra-egress-gateway-domain` — VOs and aggregate for resilience config
- `patra-egress-gateway-infra` — HTTP client adapter, properties, and masking
- `patra-egress-gateway-boot` — Spring Boot app and config

## Core Flow
1) Adapter receives RPC request and validates DTOs.
2) Orchestrator loads system config via `ConfigPort`, merges with caller config, executes `HttpClientPort`.
3) Builds `ResponseEnvelope` with whitelisted headers and retry advice; returns `ExternalCallResponseDTO` including traceId and duration.

Code Anchors
- REST endpoint: `patra-egress-gateway/patra-egress-gateway-adapter/src/main/java/com/patra/egress/adapter/rest/ExternalCallController.java:1`
- Use case orchestrator: `patra-egress-gateway/patra-egress-gateway-app/src/main/java/com/patra/egress/app/usecase/externalcall/ExternalCallOrchestrator.java:1`
- HTTP client adapter: `patra-egress-gateway/patra-egress-gateway-infra/src/main/java/com/patra/egress/infra/http/HttpClientAdapter.java:1`
- Boot config: `patra-egress-gateway/patra-egress-gateway-boot/src/main/resources/application.yaml:1`

## APIs and Contracts
- Endpoint: `POST /api/egress/call` via internal RPC interface `EgressEndpoint` and Feign client `EgressGatewayClient`.
- DTOs: `ExternalCallRequestDTO` (url, method, headers, body, optional `ResilienceConfigDTO`) and `ExternalCallResponseDTO` (envelope, durationMs, retryCount, traceId).
- Contract: see `docs/contracts/api/egress-gateway.md:1` (avoid duplication here).

## Example Request
```http
POST /api/egress/call
Content-Type: application/json

{
  "url": "https://api.example.com/data",
  "method": "GET",
  "headers": {"Accept": "application/json"},
  "body": null,
  "config": null
}
```

## Example Response (happy path)
```json
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

## Configuration
- Properties root: `patra.egress.*`
  - `patra.egress.global.rate-limit` → global RPS cap.
  - `patra.egress.resilience.default.*` → default timeout, retries, retryBackoff, rateLimit, circuitBreaker*, whitelist headers.
  - `patra.egress.resilience.max.*` → caps for the same fields.
- Beans
  - Rest client: `HttpClientConfig#restClient` with base timeouts (overridden per request).
  - Properties: `EgressProperties` encapsulates `GlobalProperties` + `ResilienceProperties` with nested `ResilienceConfigProperties`.

## Error Handling
- Validation: `ExternalCallRequestDTO` requires non-blank `url` and `method`; invalid `ResilienceConfigDTO` yields 400.
- Remote errors are returned as non-success envelopes (e.g., statusCode 500) with retry advice; ProblemDetail integration can be added via starters.

## Observability
- Logs: traceId, URL, method, statusCode, durationMs. Sensitive headers are masked by `SensitiveHeaderMasker`.
- Metrics (recommend): timer for end-to-end duration; counters for statusCode classes and retry attempts.

## Testing
- Adapter: controller tests for validation and response shape.
- Infra: HTTP adapter and header masking tests.
- Domain: value object tests for envelope/rate-limit/retry advice.

## Run Locally
```bash
./mvnw -pl patra-egress-gateway/patra-egress-gateway-boot -am spring-boot:run
```

## Related Docs
- API contract: `docs/contracts/api/egress-gateway.md:1`
- Service catalog: `docs/services/index.md:1`
- Runbook: `docs/operations/Egress-Runbook.md:1`

## Open TODOs
- Implement circuit breaker, retries with backoff, and rate limiting; propagate upstream-provided rate-limit hints into envelope.
