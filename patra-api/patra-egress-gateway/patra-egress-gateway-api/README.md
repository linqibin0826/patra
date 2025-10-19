# patra-egress-gateway-api — Egress Gateway API Contracts

> **API module** defining the public contract for the egress gateway service — DTOs, endpoints, Feign clients, and error codes.

---

## 📌 Purpose

`patra-egress-gateway-api` provides the **external API contract** for calling the egress gateway service from other microservices. It contains:

1. **Request/Response DTOs**: Type-safe data transfer objects
2. **Endpoint Interfaces**: Spring MVC endpoint definitions
3. **Feign Client**: Ready-to-use Feign client for RPC integration
4. **Error Codes**: Standardized error handling

**Why separate API module?**
- **Decoupling**: Consumers depend only on contracts, not implementation
- **Versioning**: API contracts evolve independently
- **Type Safety**: Compile-time verification of RPC calls

---

## 🗂️ Module Structure

```
patra-egress-gateway-api/
└─ src/main/java/.../api/
   ├─ dto/                           # Data Transfer Objects
   │  ├─ ExternalCallRequestDTO.java    # Request for external call
   │  ├─ ExternalCallResponseDTO.java   # Response with metadata
   │  ├─ ResilienceConfigDTO.java       # Resilience config overrides
   │  ├─ ResponseEnvelopeDTO.java       # Standardized response wrapper
   │  ├─ RetryAdviceDTO.java            # Retry recommendation
   │  ├─ RateLimitStatusDTO.java        # Rate limit info
   │  └─ ExternalRateLimitInfoDTO.java  # Provider rate limit
   │
   ├─ endpoint/                      # Endpoint Interfaces
   │  └─ EgressEndpoint.java            # POST /_internal/egress/call
   │
   ├─ client/                        # Feign Clients
   │  └─ EgressGatewayClient.java       # Feign client for RPC
   │
   └─ error/                         # Error Codes & Exceptions
      ├─ EgressErrors.java               # Standardized error codes
      ├─ EgressException.java            # Base exception
      ├─ CircuitBreakerOpenException.java
      ├─ RateLimitExceededException.java
      ├─ ExternalCallTimeoutException.java
      └─ ConfigValidationException.java
```

---

## 🔌 API Contracts

### 1. EgressEndpoint

**Single endpoint** for executing external HTTP calls:

```java
public interface EgressEndpoint {
    String BASE_PATH = "/_internal/egress";

    @PostMapping(BASE_PATH + "/call")
    ExternalCallResponseDTO call(@Valid @RequestBody ExternalCallRequestDTO request);
}
```

**POST /_internal/egress/call**
- **Purpose**: Execute external HTTP call with resilience patterns
- **Request**: `ExternalCallRequestDTO`
- **Response**: `ExternalCallResponseDTO`
- **Error**: Throws exceptions mapped to HTTP status codes

---

### 2. DTOs

#### ExternalCallRequestDTO

```java
public record ExternalCallRequestDTO(
    @NotBlank String url,              // Target URL (required)
    @NotBlank String method,           // HTTP method (GET/POST/PUT/DELETE)
    Map<String, String> headers,       // Request headers (optional)
    String body,                       // Request body (optional)
    @Valid ResilienceConfigDTO config  // Resilience overrides (optional)
) {}
```

**Example**:
```json
{
  "url": "https://api.pubmed.gov/search?q=cancer",
  "method": "GET",
  "headers": {
    "Accept": "application/json",
    "Authorization": "Bearer ..."
  },
  "body": null,
  "config": {
    "timeoutSeconds": 30,
    "maxRetries": 3,
    "retryBackoffSeconds": 1,
    "rateLimit": 100,
    "circuitBreakerThreshold": 10,
    "circuitBreakerWindowSeconds": 60,
    "responseHeaderWhitelist": ["Content-Type", "X-RateLimit-Remaining"]
  }
}
```

#### ExternalCallResponseDTO

```java
public record ExternalCallResponseDTO(
    ResponseEnvelopeDTO envelope,  // Standardized response wrapper
    long durationMs,               // Total duration (including retries)
    int retryCount,                // Actual retry attempts
    String traceId                 // Distributed tracing ID
) {}
```

**Example**:
```json
{
  "envelope": {
    "success": true,
    "statusCode": 200,
    "headers": {
      "content-type": "application/json",
      "x-ratelimit-remaining": "95"
    },
    "body": "{ ... }",
    "bodyHash": "abc123...",
    "rateLimitStatus": {
      "limit": 100,
      "remaining": 99,
      "resetInSeconds": 1,
      "externalRateLimitInfo": null
    },
    "retryAdvice": {
      "retryable": false,
      "suggestedDelaySeconds": 0,
      "reason": "Not retryable"
    },
    "snapshotMode": "full"
  },
  "durationMs": 523,
  "retryCount": 0,
  "traceId": "550e8400-e29b-41d4-a716-446655440000"
}
```

#### ResilienceConfigDTO

```java
public record ResilienceConfigDTO(
    Integer timeoutSeconds,              // Request timeout
    Integer maxRetries,                  // Max retry attempts
    Integer retryBackoffSeconds,         // Retry backoff
    Integer rateLimit,                   // Requests per second
    Integer circuitBreakerThreshold,     // Failures to trigger CB
    Integer circuitBreakerWindowSeconds, // CB evaluation window
    List<String> responseHeaderWhitelist // Headers to return
) {}
```

**Merge behavior**: Caller config is capped by system max values.

#### ResponseEnvelopeDTO

```java
public record ResponseEnvelopeDTO(
    boolean success,                     // Based on HTTP status
    int statusCode,                      // HTTP status code
    Map<String, String> headers,         // Whitelisted headers only
    String body,                         // Response body
    String bodyHash,                     // SHA-256 hash
    RateLimitStatusDTO rateLimitStatus,  // Rate limit info
    RetryAdviceDTO retryAdvice,          // Retry recommendation
    String snapshotMode                  // "full" or "metadata-only"
) {}
```

#### RetryAdviceDTO

```java
public record RetryAdviceDTO(
    boolean retryable,           // Should retry?
    long suggestedDelaySeconds,  // Backoff duration
    String reason                // Why retryable/not
) {}
```

**Retryable status codes**: 429, 503, 5xx, 408

#### RateLimitStatusDTO

```java
public record RateLimitStatusDTO(
    int limit,                            // Gateway rate limit
    int remaining,                        // Remaining capacity
    long resetInSeconds,                  // Time until reset
    ExternalRateLimitInfoDTO externalInfo // Provider rate limit (optional)
) {}
```

---

### 3. EgressGatewayClient (Feign)

**Ready-to-use Feign client** for RPC integration:

```java
@FeignClient(name = "patra-egress-gateway", contextId = "egressGatewayClient")
public interface EgressGatewayClient extends EgressEndpoint {
}
```

---

## 🚀 Usage Guide

### Step 1: Add Dependency

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-egress-gateway-api</artifactId>
    <version>${project.version}</version>
</dependency>
```

### Step 2: Enable Feign Clients

```java
@SpringBootApplication
@EnableFeignClients(clients = EgressGatewayClient.class)
public class YourApplication {
    public static void main(String[] args) {
        SpringApplication.run(YourApplication.class, args);
    }
}
```

### Step 3: Inject and Use

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
            null,
            new ResilienceConfigDTO(30, 3, 1, 100, 10, 60, List.of("Content-Type"))
        );

        // Call via egress gateway
        ExternalCallResponseDTO response = egressClient.call(request);

        // Check success
        if (!response.envelope().success()) {
            log.error("External call failed: statusCode={}, body={}",
                response.envelope().statusCode(),
                response.envelope().body());

            // Check retry advice
            RetryAdviceDTO retryAdvice = response.envelope().retryAdvice();
            if (retryAdvice.retryable()) {
                log.info("Retry suggested after {}s: {}",
                    retryAdvice.suggestedDelaySeconds(),
                    retryAdvice.reason());
                // Handle retry...
            }
            throw new ExternalCallException("Call failed");
        }

        // Process response
        String body = response.envelope().body();
        log.info("Call succeeded: durationMs={}, retryCount={}, traceId={}",
            response.durationMs(),
            response.retryCount(),
            response.traceId());

        return body;
    }
}
```

---

## ⚠️ Error Codes

### EgressErrors

**Standardized error codes** following pattern: `EGR-{segment}{number}`

#### HTTP-Aligned Errors (0xxx)

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `EGR-0400` | 400 | Bad Request |
| `EGR-0422` | 422 | Unprocessable Entity (config validation failed) |
| `EGR-0429` | 429 | Too Many Requests (rate limit exceeded) |
| `EGR-0500` | 500 | Internal Server Error |
| `EGR-0503` | 503 | Service Unavailable (circuit breaker open) |
| `EGR-0504` | 504 | Gateway Timeout (external call timeout) |

#### Business Errors (1xxx)

| Error Code | HTTP Status | Description |
|------------|-------------|-------------|
| `EGR-1001` | 500 | Config load failed |
| `EGR-1002` | 422 | Config validation failed |
| `EGR-1003` | 502 | External service call failed |
| `EGR-1004` | 500 | Response envelope build failed |

### Exception Hierarchy

```
EgressException (base)
  ├─ CircuitBreakerOpenException (EGR-0503)
  ├─ RateLimitExceededException (EGR-0429)
  ├─ ExternalCallTimeoutException (EGR-0504)
  └─ ConfigValidationException (EGR-0422)
```

---

## 🔒 Security Considerations

### Header Whitelisting

**Only whitelisted headers** flow back to caller:
- Prevents leaking sensitive provider headers
- Configurable via `responseHeaderWhitelist` in config
- Default whitelist: Content-Type, Content-Length, X-RateLimit-*, Retry-After

### Sensitive Header Masking

**Automatically masked in logs**:
- Authorization
- API-Key, X-API-Key
- X-Auth-Token
- Cookie, Set-Cookie

### Body Hashing

**SHA-256 hash** of response body:
- Audit trail (detect tampering)
- Deduplication (compare hashes)
- Performance (avoid full body comparison)

---

## 📊 Best Practices

### 1. Always Provide Resilience Config

```java
// ✅ Good: Provide config for critical calls
ResilienceConfigDTO config = new ResilienceConfigDTO(
    30,   // timeout
    3,    // maxRetries
    1,    // retryBackoff
    100,  // rateLimit
    10,   // circuitBreakerThreshold
    60,   // circuitBreakerWindow
    List.of("Content-Type", "X-RateLimit-Remaining")
);
```

```java
// ❌ Bad: Relying on system default for all calls
ResilienceConfigDTO config = null;  // Uses system default
```

### 2. Handle Retry Advice

```java
// ✅ Good: Check retry advice
if (!response.envelope().success() && response.envelope().retryAdvice().retryable()) {
    long delaySeconds = response.envelope().retryAdvice().suggestedDelaySeconds();
    // Schedule retry with delay
}
```

```java
// ❌ Bad: Ignoring retry advice
if (!response.envelope().success()) {
    throw new Exception();  // No retry handling
}
```

### 3. Monitor Rate Limits

```java
// ✅ Good: Check rate limit status
RateLimitStatusDTO rateLimit = response.envelope().rateLimitStatus();
if (rateLimit.remaining() < 10) {
    log.warn("Rate limit nearly exhausted: {}/{}", rateLimit.remaining(), rateLimit.limit());
    // Slow down or backoff
}
```

### 4. Use TraceId for Debugging

```java
// ✅ Good: Log traceId for correlation
log.info("External call completed: traceId={}, duration={}ms",
    response.traceId(), response.durationMs());
```

---

## 🔗 Related Documentation

- [patra-egress-gateway Service README](../README.md)
- [Main README](../../README.md)
- [Architecture Guide](../../docs/ARCHITECTURE.md)

---

**Last Updated**: 2025-01-12
