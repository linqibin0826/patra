# patra-egress-gateway — Southbound API Gateway

> **Egress gateway** providing centralized resilience, observability, and standardized response format for all outbound external service calls.

---

## 📌 Purpose

Serves as the **single exit point** for all external API calls from Papertrace microservices:
- Centralized resilience patterns (retry, rate limiting, circuit breaker)
- Standardized response envelope with metadata
- Audit trail (bodyHash, traceId, duration tracking)
- Security (header whitelisting, sensitive data masking)
- Anti-Corruption Layer for external APIs

---

## 🏗️ Architecture

```
patra-ingest/patra-registry (internal services)
  ↓ (via EgressGatewayClient - Feign)
patra-egress-gateway (port 8083)
  ↓ (HttpClientAdapter - RestClient)
External APIs (PubMed, EPMC, etc.)
```

### Hexagonal Layers

```
patra-egress-gateway/
├─ patra-egress-gateway-api/        # External contracts (DTOs, EgressEndpoint, EgressGatewayClient)
├─ patra-egress-gateway-domain/     # Domain model (ResilienceConfigAggregate, VOs, Ports)
├─ patra-egress-gateway-app/        # Use case orchestration (ExternalCallOrchestrator)
├─ patra-egress-gateway-infra/      # HTTP client, config repository
├─ patra-egress-gateway-adapter/    # REST controller
└─ patra-egress-gateway-boot/       # Main application
```

---

## 🧩 Core Concepts

### ResilienceConfig

**7-parameter** value object for outbound call guardrails:

```java
public record ResilienceConfig(
    Duration timeout,                     // Request timeout
    int maxRetries,                       // Maximum retry attempts
    Duration retryBackoff,                // Backoff between retries
    int rateLimit,                        // Requests per second
    int circuitBreakerThreshold,          // Failures to trigger circuit breaker
    Duration circuitBreakerWindow,        // CB evaluation window
    List<String> responseHeaderWhitelist  // Headers allowed to flow back
);
```

**Two-tier configuration**:
- **System default**: Used when caller doesn't provide config
- **System max**: Upper bounds enforced on caller-provided config

**Merge logic**:
```java
// Caller config is capped by system max
ResilienceConfig merged = callerConfig.mergeWithMax(systemMaxConfig);
```

### ResponseEnvelope

**Standardized response wrapper** returned by egress gateway:

```java
public record ResponseEnvelope(
    boolean success,                  // Based on HTTP status code
    int statusCode,                   // HTTP status
    Map<String, String> headers,      // Whitelisted headers only
    String body,                      // Response body
    String bodyHash,                  // SHA-256 hash (for audit/deduplication)
    RateLimitStatus rateLimitStatus,  // Gateway + provider rate limits
    RetryAdvice retryAdvice,          // Should caller retry?
    String snapshotMode               // "metadata-only" or "full"
);
```

**Why ResponseEnvelope?**
- Uniform format across all external APIs
- Metadata for retry decisions, rate limit awareness
- Audit trail (bodyHash for deduplication)
- Security (only whitelisted headers)

### RetryAdvice

**Smart retry decision logic** based on HTTP response:

```java
public record RetryAdvice(
    boolean retryable,        // Should retry?
    Duration suggestedDelay,  // Backoff duration
    String reason             // Why retryable/not
);
```

**Retry rules**:
- **Retryable**: 429 (rate limited), 503 (unavailable), 5xx (server error), 408 (timeout)
- **Non-retryable**: 2xx, 3xx, 4xx (except 408)

**Intelligent backoff**:
```java
// Extract Retry-After header from provider response
Duration delay = extractRetryAfter(response.headers())
    .orElse(config.retryBackoff());  // Fall back to config
```

---

## 🔌 API Contract

### EgressEndpoint

```java
@PostMapping("/api/egress/call")
ExternalCallResponseDTO call(@Valid @RequestBody ExternalCallRequestDTO request);
```

**Request**:
```json
{
  "url": "https://api.pubmed.gov/search",
  "method": "GET",
  "headers": {
    "Accept": "application/json",
    "Authorization": "Bearer ..."
  },
  "body": null,
  "callerConfig": {
    "timeoutSeconds": 30,
    "maxRetries": 3,
    "retryBackoffSeconds": 1,
    "rateLimit": 100
  }
}
```

**Response**:
```json
{
  "envelope": {
    "success": true,
    "statusCode": 200,
    "headers": {
      "content-type": "application/json",
      "x-ratelimit-remaining": "95"
    },
    "body": "...",
    "bodyHash": "abc123...",
    "rateLimitStatus": {
      "limit": 100,
      "remaining": 99,
      "resetIn": "PT1S"
    },
    "retryAdvice": {
      "retryable": false,
      "suggestedDelay": "PT0S",
      "reason": "Not retryable"
    }
  },
  "duration": "PT0.523S",
  "retryCount": 0,
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

---

## 🔄 Use Case Flow

**ExternalCallOrchestrator** orchestrates the external call:

```java
@Override
public ExternalCallResult execute(ExternalCallCommand command) {
    String traceId = UUID.randomUUID().toString();
    Instant startTime = Instant.now();

    // 1. Load and merge resilience config
    ResilienceConfig mergedConfig = loadAndMergeConfig(command);

    // 2. Call external service via HttpClientPort
    HttpResponse response = httpClientPort.call(command.request(), mergedConfig);

    // 3. Build response envelope
    ResponseEnvelope envelope = buildResponseEnvelope(response, mergedConfig);

    // 4. Calculate duration
    Duration duration = Duration.between(startTime, Instant.now());

    // 5. Return result
    return new ExternalCallResult(envelope, duration, retryCount, traceId);
}
```

**Config merging**:
```java
// Load system default and max
ResilienceConfigAggregate aggregate =
    ResilienceConfigAggregate.loadSystemConfig(configPort);

// Merge caller config (capped by system max)
ResilienceConfig merged = aggregate.mergeWithCallerConfig(command.callerConfig());

// Warn if caller config exceeded max
if (!callerConfig.equals(merged)) {
    log.warn("Caller config exceeded system max, using capped values");
}
```

---

## ⚙️ Configuration

### application.yml

```yaml
spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:dev}
  config:
    import:
      - "classpath:egress-error-config.yaml"
      - "optional:nacos:${spring.application.name}-${spring.profiles.active:dev}.yaml?group=${NACOS_CONFIG_GROUP:${NACOS_GROUP:DEFAULT_GROUP}}&namespace=${NACOS_NAMESPACE_ID:${NACOS_NAMESPACE:public}}"
      - "optional:nacos:papertrace-${spring.profiles.active:dev}.yaml?group=${NACOS_CONFIG_GROUP:${NACOS_GROUP:DEFAULT_GROUP}}&namespace=${NACOS_NAMESPACE_ID:${NACOS_NAMESPACE:public}}"
patra:
  egress:
    global:
      rate-limit: ${EGRESS_GLOBAL_RATE_LIMIT:1000}

    resilience:
      # System maximum (upper bounds)
      max:
        timeout-seconds: 60
        max-retries: 5
        retry-backoff-seconds: 10
        rate-limit: 1000
        circuit-breaker-threshold: 20
        circuit-breaker-window-seconds: 120
        response-header-whitelist:
          - "Content-Type"
          - "Content-Length"
          - "X-RateLimit-Limit"
          - "X-RateLimit-Remaining"
          - "X-RateLimit-Reset"
          - "Retry-After"
          - "ETag"
          - "Last-Modified"

      # System default (used when caller doesn't provide config)
      default:
        timeout-seconds: 30
        max-retries: 3
        retry-backoff-seconds: 1
        rate-limit: 100
        circuit-breaker-threshold: 10
        circuit-breaker-window-seconds: 60
        response-header-whitelist:
          - "Content-Type"
          - "Content-Length"
          - "X-RateLimit-Limit"
          - "X-RateLimit-Remaining"
          - "X-RateLimit-Reset"
          - "Retry-After"
```

### application-dev.yml

```yaml
spring:
  config:
    activate:
      on-profile: dev

logging:
  level:
    com.patra.egress: DEBUG
```

### application-prod.yml

Used for cloud deployment overrides. Add environment-specific limits or endpoint settings if they differ from the defaults.

**Why two-tier config?**
- **Default**: Sensible defaults for most use cases
- **Max**: Guardrails to prevent abuse (caller can't exceed)

---

## 🔗 Integration

### EgressGatewayClient (Feign)

**Define in patra-ingest or patra-registry**:
```java
@FeignClient(name = "patra-egress-gateway", contextId = "egressGatewayClient")
public interface EgressGatewayClient extends EgressEndpoint {
}
```

**Usage in service**:
```java
@Component
@RequiredArgsConstructor
public class PubMedFetcher {

    private final EgressGatewayClient egressClient;

    public String fetchArticles(String query) {
        // Prepare request
        ExternalCallRequestDTO request = new ExternalCallRequestDTO(
            "https://api.pubmed.gov/search?q=" + query,
            "GET",
            Map.of("Accept", "application/json"),
            null,  // No body
            new ResilienceConfigDTO(30, 3, 1, 100, 10, 60, List.of())
        );

        // Call via egress gateway
        ExternalCallResponseDTO response = egressClient.call(request);

        // Check retry advice
        if (!response.envelope().success() &&
            response.envelope().retryAdvice().retryable()) {
            Duration delay = response.envelope().retryAdvice().suggestedDelay();
            log.info("Retry suggested after: {}", delay);
            // Handle retry logic...
        }

        return response.envelope().body();
    }
}
```

---

## 📊 Observability

### Metrics

- **Gateway metrics**: Requests, latency, error rate per external API
- **Rate limit tracking**: Current rate, remaining capacity
- **Circuit breaker**: Open/closed state, failure rate
- **Retry stats**: Retry count distribution

### Tracing

- **Trace ID**: Generated per external call, propagated to logs
- **Span ID**: Integration with SkyWalking
- **Context propagation**: traceId flows through entire call chain

### Logging

**Structured logs with traceId**:
```
[TID: 550e8400-e29b-41d4-a716-446655440000] 2025-01-12 10:30:45.123  INFO [http-nio-8083-exec-1] c.p.e.a.u.e.ExternalCallOrchestrator : External call started: url=https://api.pubmed.gov/search method=GET
[TID: 550e8400-e29b-41d4-a716-446655440000] 2025-01-12 10:30:45.646  INFO [http-nio-8083-exec-1] c.p.e.a.u.e.ExternalCallOrchestrator : External call completed: statusCode=200 duration=523ms
```

**Sensitive header masking**:
```java
// Automatically masks: Authorization, API-Key, X-API-Key, etc.
Map<String, String> maskedHeaders = SensitiveHeaderMasker.mask(request.headers());
log.debug("Request headers: {}", maskedHeaders);
// Output: {"Authorization": "***MASKED***", "Content-Type": "application/json"}
```

#### 🪵 Logging (Starter v1.0)

`patra-egress-gateway` adopts `patra-spring-boot-starter-logging`:
- Trace context MDC: `traceId`/`correlationId` automatically populated
- Sanitization: mask sensitive headers/bodies via `LogSanitizer`
- Dynamic levels via Nacos

Nacos example (`logging-patra-egress-gateway.yml`):
```yaml
logging.level:
  root: INFO
  com.patra.starter.logging.interceptor: DEBUG
  com.patra.egress.gateway.adapter: DEBUG
```

References: docs/logging/operations-guide.md, specs/001-logging-starter/quickstart.md

---

## 🔒 Security

### Header Whitelisting

**Only whitelisted headers** flow back to caller:
- Prevents leaking sensitive provider headers
- Configurable per system (default + max lists)
- Common headers: Content-Type, Content-Length, X-RateLimit-*, Retry-After, ETag

### Sensitive Header Masking

**Auto-masks in logs**:
- Authorization
- API-Key, X-API-Key
- X-Auth-Token
- Cookie, Set-Cookie

### Body Hashing

**SHA-256 hash** of response body:
- Audit trail (detect tampering)
- Deduplication (same hash = same response)
- Performance (compare hashes instead of full bodies)

---

## 🚀 Running Locally

```bash
cd patra-egress-gateway/patra-egress-gateway-boot
mvn spring-boot:run
```

**Default Port**: 8083

---

## 📝 Current Status & Roadmap

### ✅ Implemented (MVP)

- Hexagonal architecture (api, domain, app, infra, adapter, boot)
- ResilienceConfigAggregate with two-tier config merging
- ExternalCallOrchestrator with config loading
- HttpClientAdapter using Spring RestClient
- ResponseEnvelope with standardized format
- RetryAdvice with intelligent retry logic
- EgressGatewayClient (Feign integration)
- Header whitelisting and sensitive header masking
- Observability (traceId, structured logging)

### 🚧 Planned (Task 6 - Full Resilience)

**Retry**:
- Automatic retry with exponential backoff
- Respect Retry-After header from provider
- Max retries enforcement

**Rate Limiting**:
- Token bucket algorithm
- Per-API rate limiting
- Global gateway rate limiting
- External provider rate limit tracking (X-RateLimit-* headers)

**Circuit Breaker**:
- Resilience4j integration
- Failure threshold monitoring
- Half-open state for recovery
- Fallback strategies

---

## 🔗 Dependencies

**Key dependencies**:
- Spring Boot 3.x
- Spring Cloud OpenFeign (Feign client)
- Spring RestClient (HTTP client)
- Resilience4j (circuit breaker, retry) - *planned*
- Bucket4j (rate limiting) - *planned*

---

## 🔗 Related Documentation

- [Main README](../README.md)
- [Architecture Guide](../docs/ARCHITECTURE.md)
- [API Gateway (Ingress)](../patra-gateway-boot/README.md)

---

**Last Updated**: 2025-01-12
