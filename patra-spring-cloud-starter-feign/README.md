# patra-spring-cloud-starter-feign

> Enhanced Feign client configuration with error handling, tracing, and retry policies.

## 📌 Purpose

Extends Spring Cloud OpenFeign with Papertrace conventions:
- Custom error decoder (maps errors to domain exceptions)
- Request interceptors (trace ID propagation)
- Retry policies (exponential backoff)
- Timeout configuration
- Circuit breaker integration

## 🔧 Auto-Configurations

### Error Decoder
- Maps HTTP 404 → `NotFoundException`
- Maps HTTP 409 → `ConflictException`
- Maps HTTP 5xx → `RemoteServiceException`
- Extracts Problem Detail from responses

### Request Interceptor
- Injects `X-Trace-ID` header
- Injects `X-Span-ID` header
- Propagates correlation ID

### Retry Configuration
- Max attempts: 3
- Backoff: 100ms, 300ms, 900ms (exponential)
- Retryable: 502, 503, 504 status codes

## 🔗 Dependencies

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-cloud-starter-feign</artifactId>
</dependency>
```

Includes: Spring Cloud OpenFeign, Resilience4j Retry

## 🚀 Usage

### Define Feign Client
```java
@FeignClient(name = "patra-registry", contextId = "provenanceClient")
public interface ProvenanceClient extends ProvenanceEndpoint {
    // Methods inherited from endpoint interface
}
```

### Usage in Service
```java
@Component
@RequiredArgsConstructor
public class PatraRegistryPortImpl implements PatraRegistryPort {

    private final ProvenanceClient client;  // Auto-wired

    @Override
    public ProvenanceConfigSnapshot fetchConfig(ProvenanceCode code) {
        try {
            return client.getConfiguration(code, null, Instant.now());
        } catch (NotFoundException ex) {
            throw new ConfigNotFoundException("Config not found for: " + code, ex);
        }
    }
}
```

### Configuration
```yaml
feign:
  client:
    config:
      default:
        connect-timeout: 5000
        read-timeout: 10000
        retry-max-attempts: 3
```

---

**Last Updated**: 2025-01-12
