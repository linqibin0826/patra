# patra-spring-boot-starter-core

> **Core Spring Boot starter** providing foundational auto-configuration for JSON serialization, error handling, observability, and resilience across all Papertrace microservices.

---

## 📌 Purpose

This starter provides **essential infrastructure** that ALL Papertrace services need:

1. **Jackson Configuration**: Standardized JSON serialization (date formats, null handling, etc.)
2. **Error Handling Framework**: Sophisticated error resolution pipeline with tracing and metrics
3. **Observability**: Integration with Micrometer (metrics) and SkyWalking (tracing)
4. **Resilience**: Circuit breaker integration via Resilience4j
5. **Logging**: Pre-configured Logback with trace context propagation

**Key Principle**: Convention over configuration — services get sensible defaults out-of-the-box.

---

## 🔧 Auto-Configurations

### 1. JacksonAutoConfiguration

**Purpose**: Standardize JSON serialization across all services.

**What it does**:
- Configure `ObjectMapper` with Papertrace conventions:
  - ISO-8601 date/time formatting
  - Include non-null fields only
  - Fail on unknown properties (strict mode)
  - Register Java 8 time module
- Provide `@JsonComponent` support
- Enable `JsonNode` support for dynamic JSON fields

**Configuration Properties**:
```yaml
spring:
  jackson:
    default-property-inclusion: non_null
    serialization:
      write-dates-as-timestamps: false
    deserialization:
      fail-on-unknown-properties: true
```

---

### 2. XmlAutoConfiguration

**Purpose**: Provide a project-level `XmlMapper` for modules that process XML (e.g., PubMed payloads).

**What it does**:
- Exposes a singleton `XmlMapper` built from Spring Boot's `Jackson2ObjectMapperBuilder`
- Activates only when `jackson-dataformat-xml` is on the classpath
- Shares the same global Jackson modules and settings as JSON

No extra configuration is required; simply inject `XmlMapper` where needed.

---

### 3. CoreErrorAutoConfiguration

**Purpose**: Unified error handling with extensible resolution pipeline.

**Architecture**:
```
Exception thrown
    ↓
ErrorResolutionEngine
    ↓
ResolutionPipeline (chain of interceptors)
    ├─ TracingInterceptor (propagate trace context)
    ├─ MetricsInterceptor (record error metrics)
    ├─ CircuitBreakerInterceptor (trigger circuit breaker)
    └─ ... (custom interceptors)
    ↓
ErrorMappingContributor (SPI: map exception → HTTP status)
    ↓
ProblemDetail (RFC 7807 format)
```

**Key Components**:

| Component | Purpose |
|-----------|---------|
| **ErrorResolutionEngine** | Orchestrates error resolution through pipeline |
| **ResolutionPipeline** | Chain-of-responsibility for interceptors |
| **ErrorMappingContributor** | SPI for custom exception → HTTP mappings |
| **ProblemFieldContributor** | SPI for adding custom fields to problem details |
| **TraceProvider** | SPI for trace context extraction |

**SPI Extension Points**:

```java
// Custom error mapping
@Component
public class MyErrorMappingContributor implements ErrorMappingContributor {
    @Override
    public ProblemDetail map(Exception ex) {
        if (ex instanceof MyCustomException) {
            return ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage()
            );
        }
        return null;  // Let next contributor handle it
    }
}

// Add custom fields to problem details
@Component
public class MyProblemFieldContributor implements ProblemFieldContributor {
    @Override
    public void contribute(ProblemDetail problem, Exception ex) {
        problem.setProperty("correlationId", MDC.get("correlationId"));
        problem.setProperty("service", "patra-ingest");
    }
}
```

---

## 📊 Observability

### Metrics (Micrometer)

**Auto-configured metrics**:
- Error counts by exception type
- Error resolution duration
- Circuit breaker state changes

**Access**:
```java
@Autowired
private MeterRegistry meterRegistry;

// Record custom metric
meterRegistry.counter("plan.ingestion.success").increment();
```

### Tracing (SkyWalking)

**Features**:
- Automatic trace context propagation in logs
- Trace ID injection into error responses
- Integration with SkyWalking agent

**Log Format** (with trace):
```
[2025-01-12 10:30:45] [TID:abc123] [INFO] [PlanIngestionOrchestrator] Plan ingestion success, planId=123
```

---

## 🛡️ Resilience

### Circuit Breaker Integration

**Resilience4j** auto-configured for error handling:

```java
@CircuitBreaker(name = "registry-service", fallbackMethod = "registryFallback")
public ProvenanceConfig fetchConfig(ProvenanceCode code) {
    return registryClient.getConfig(code);
}

public ProvenanceConfig registryFallback(ProvenanceCode code, Exception ex) {
    // Fallback logic
    return getCachedConfig(code);
}
```

**Circuit breaker events** are automatically logged and metrics are recorded.

---

## 🔗 Dependencies

This starter is included by ALL microservice `-boot` modules:

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-core</artifactId>
</dependency>
```

**Transitive dependencies** (auto-included):
- `patra-common` (domain base classes)
- Spring Boot Autoconfigure
- Spring Boot Starter JSON (Jackson)
- Micrometer Core
- Resilience4j CircuitBreaker
- SkyWalking Logback toolkit

---

## 🚀 Usage

### In Application Layer

**Error handling**:
```java
@Service
public class PlanIngestionOrchestrator {

    public PlanIngestionResult ingest(PlanIngestionCommand cmd) {
        try {
            // Business logic
            return result;
        } catch (DomainException ex) {
            // Domain exceptions are automatically mapped to app exceptions
            throw new PlanAssemblyException("Plan assembly failed", ex);
        }
    }
}
```

### In Adapter Layer

**Error mapping**:
```java
@RestControllerAdvice
public class RegistryErrorMappingContributor implements ErrorMappingContributor {

    @Override
    public ProblemDetail map(Exception ex) {
        if (ex instanceof ProvenanceNotFoundException) {
            return ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND,
                "Provenance not found: " + ex.getMessage()
            );
        }
        return null;
    }
}
```

**Automatic features**:
- Trace ID injected into error responses
- Metrics recorded for all errors
- Circuit breaker triggered on repeated failures

---

## ⚙️ Configuration

### application.yml

```yaml
# Error handling
patra:
  error:
    include-trace: true              # Include trace ID in errors
    include-stack-trace: false       # Hide stack traces in prod
    max-resolution-time: 5000        # Max time for error resolution (ms)

# Tracing
patra:
  tracing:
    enabled: true
    trace-header: X-Trace-ID         # HTTP header for trace ID
    span-header: X-Span-ID

# Circuit breaker
resilience4j:
  circuitbreaker:
    instances:
      registry-service:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
```

---

## 🧪 Testing

### Unit Tests

```java
@SpringBootTest
class ErrorResolutionEngineTest {

    @Autowired
    private ErrorResolutionEngine engine;

    @Test
    void shouldMapDomainExceptionToHttp404() {
        // Given
        Exception ex = new ProvenanceNotFoundException("Not found");

        // When
        ErrorResolution resolution = engine.resolve(ex);

        // Then
        assertEquals(404, resolution.httpStatus());
        assertEquals("Provenance not found", resolution.message());
    }
}
```

---

## 📈 Performance

**Error Resolution Overhead**: < 1ms per error (measured with Micrometer)

**Circuit Breaker**: Fail-fast after threshold reached (prevents cascading failures)

**Logging**: Async logging with SkyWalking (non-blocking)

---

## 🔗 Related Documentation

- [Main README](../README.md)
- [Architecture Guide](../docs/ARCHITECTURE.md)
- [patra-common README](../patra-common/README.md) — Error base classes

---

**Last Updated**: 2025-01-12
