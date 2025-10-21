# patra-spring-cloud-starter-feign

> Enhanced Feign client configuration with convention-based scanning, error handling, tracing, and retry policies.

## 📌 Purpose

Extends Spring Cloud OpenFeign with Papertrace conventions:

- **Convention-based Feign client scanning** (`com.patra.*.api.rpc.client`)
- Custom error decoder (maps errors to domain exceptions)
- Request interceptors (trace ID propagation, caller service ID)
- Retry policies (exponential backoff)
- Timeout configuration
- Circuit breaker integration

## 🏗️ Convention-Based Scanning

### Automatic Feign Client Discovery

This starter **automatically scans and registers** all `@FeignClient` annotated interfaces under the `com.patra` package.

**Convention**: Place RPC clients in `{module}-api/src/main/java/com/patra/{module}/api/rpc/client/` packages.

**Examples of auto-discovered clients:**

- `com.patra.registry.api.rpc.client.ProvenanceClient`
- `com.patra.registry.api.rpc.client.ExprClient`
- `com.patra.ingest.api.rpc.client.TaskClient` (future)
- `com.patra.data.api.rpc.client.LiteratureClient` (future)

### No Manual Configuration Required

```java
// ❌ OLD WAY: Manual configuration in every service
@EnableFeignClients(basePackages = {"com.patra.registry.api.rpc.client"})
@SpringBootApplication
public class MyApplication {
}

// ✅ NEW WAY: Convention-based automatic discovery
@SpringBootApplication
public class MyApplication {
    // Feign clients automatically discovered via starter
}
```

### Benefits

- ✅ **Convention over Configuration**: Follow naming pattern → automatic registration
- ✅ **DRY Principle**: No scattered `@EnableFeignClients` across services
- ✅ **Consistency**: All services follow same discovery pattern
- ✅ **Maintainability**: Add new Feign clients without updating configuration

### All Feign Clients Discovered

This starter discovers **all** `@FeignClient` interfaces under `com.patra`, including business RPC
clients placed under `com.patra.{module}.api.rpc.client.*` (following the convention).

**No need for individual starters to declare `@EnableFeignClients`** - this starter handles it centrally.

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

### Define Feign Client (Convention-Based)

**Step 1**: Create client interface in `{module}-api/src/main/java/com/patra/{module}/api/rpc/client/`

```java
package com.patra.registry.api.rpc.client;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "patra-registry", contextId = "provenanceClient")
public interface ProvenanceClient extends ProvenanceEndpoint {
    // Methods inherited from endpoint interface
}
```

**Step 2**: Use client in your service (automatically discovered and injected)

```java

@Component
@RequiredArgsConstructor
public class PatraRegistryPortImpl implements PatraRegistryPort {

    private final ProvenanceClient client;  // Auto-wired (no @EnableFeignClients needed!)

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

**That's it!** No `@EnableFeignClients` annotation needed in your application class.

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

## 🔗 Related Documentation

- [Main README](../README.md)
- [patra-spring-boot-starter-provenance](../patra-spring-boot-starter-provenance/README.md) - Provenance client starter
- [Architecture Guide](../docs/ARCHITECTURE.md) - System design patterns

---

**Last Updated**: 2025-10-14
