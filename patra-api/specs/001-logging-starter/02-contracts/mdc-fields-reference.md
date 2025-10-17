# MDC Fields Reference

**Version**: 1.0.0 | **Last Updated**: 2025-10-15

## Overview

This document defines standard MDC (Mapped Diagnostic Context) field names used across all Papertrace microservices. Consistent field naming enables reliable log filtering and correlation in centralized log aggregation systems.

---

## Standard MDC Fields

### Trace Context Fields (Managed by TraceContextFilter)

| Field Name | Type | Required | Source | Description | Example |
|------------|------|----------|--------|-------------|---------|
| `traceId` | String (UUID or tracing vendor format) | YES | Tracing library API (e.g., SkyWalking) | Unique identifier for entire request flow across all services | `abc123-def456-ghi789` |
| `correlationId` | String (UUID) | NO | HTTP header `X-Correlation-ID` or generated | Business operation identifier (batch ID, idempotency key) | `batch-20251015-001` |
| `spanId` | String (SkyWalking format) | NO | SkyWalking `ActiveSpan.getSpanId()` | Current operation identifier within the trace | `0.1.2` |
| `parentSpanId` | String (SkyWalking format) | NO | SkyWalking `ActiveSpan.getParentSpanId()` | Parent operation identifier for nested calls | `0.1` |

### Business Context Fields (Application-Managed)

| Field Name | Type | Required | Source | Description | Example |
|------------|------|----------|--------|-------------|---------|
| `userId` | String | NO | Authentication context | Authenticated user identifier | `user-12345` |
| `batchId` | String (UUID) | NO | Batch orchestrator | Batch processing operation identifier | `batch-20251015-001` |
| `taskId` | String | NO | Task orchestrator | Individual task identifier within batch | `task-98765` |
| `entityId` | String | NO | Domain operation | Primary entity being operated on (provenance ID, plan ID) | `provenance-789` |
| `operation` | String | NO | Orchestrator | Business operation being performed | `CREATE_PLAN`, `INGEST_DATA` |

### Technical Context Fields (Infrastructure-Managed)

| Field Name | Type | Required | Source | Description | Example |
|------------|------|----------|--------|-------------|---------|
| `service` | String | YES | Spring Boot `spring.application.name` | Microservice name | `patra-ingest` |
| `environment` | String | YES | Environment variable `SPRING_PROFILES_ACTIVE` | Deployment environment | `production` |
| `hostname` | String | YES | System property `hostname` | Server hostname or pod name | `patra-ingest-7f8b6c-xyz` |
| `clientIp` | String | NO | HTTP request `X-Forwarded-For` or `RemoteAddr` | Client IP address for web requests | `203.0.113.42` |
| `requestUri` | String | NO | HTTP request URI | Request path for web requests | `/api/v1/ingest/pubmed` |
| `httpMethod` | String | NO | HTTP request method | HTTP method for web requests | `POST` |

---

## Field Lifecycle Management

### Automatic Population (TraceContextFilter)

Fields automatically populated by `TraceContextFilter` at servlet boundary:
- `traceId` (from tracing library or generated)
- `correlationId` (from header or generated)
- `spanId` and `parentSpanId` (if available from tracing library)
- `service` (from Spring config)
- `environment` (from Spring profile)
- `hostname` (from system)
- `clientIp` (from HTTP request)
- `requestUri` (from HTTP request)
- `httpMethod` (from HTTP request)

### Manual Population (Application Code)

Fields manually added by orchestrators/services:
- `userId` (after authentication)
- `batchId` (batch processing start)
- `taskId` (individual task execution)
- `entityId` (when operating on specific entity)
- `operation` (business operation name)

### Cleanup

**Critical**: MDC must be cleared in `finally` blocks to prevent thread pool pollution:

```java
try {
    // Populate MDC
    MDC.put("batchId", batchId);
    // ... processing
} finally {
    // Clear custom fields
    MDC.remove("batchId");
}
```

TraceContextFilter handles cleanup of automatic fields.

---

## MDC Key Remapping

Default MDC keys are defined above. Keys can be remapped via Spring properties under `papertrace.logging.mdc.field-names.*` (see `spring-boot-properties.md`).

- All framework components (TraceContextFilter, interceptors, utilities) must honor the active mapping when reading/writing MDC.
- If remapped, update logback patterns and log queries accordingly.

---

## Logback Pattern Configuration

**Standard Pattern** (includes MDC fields):

```xml
<pattern>
  %d{ISO8601} [%X{traceId}] [%X{correlationId}] [%X{service}] [%thread] %-5level %logger{36} - %msg%n
</pattern>
```

**Extended Pattern** (includes more context):

```xml
<pattern>
  %d{ISO8601} [%X{traceId}] [%X{correlationId}] [%X{service}] [%X{environment}] [%X{userId}] [%X{operation}] [%thread] %-5level %logger{36} - %msg%n
</pattern>
```

**Conditional Fields** (show only if present):

```xml
<pattern>
  %d{ISO8601} [%X{traceId:-NO_TRACE}] %replace([%X{correlationId}]){'^\[\]$', ''} [%X{service}] [%thread] %-5level %logger{36} - %msg%n
</pattern>
```

---

## Usage Patterns by Layer

### Adapter Layer (Controllers, Jobs, Listeners)

```java
@RestController
@Slf4j
public class IngestController {
    @Autowired
    private LogContextEnricher enricher;

    @PostMapping("/ingest")
    public ResponseEntity<?> ingest(@RequestBody IngestRequest request) {
        // TraceContextFilter already populated traceId, correlationId, etc.

        // Enrich with business context
        enricher.enrich("operation", "INGEST_DATA");
        enricher.enrich("source", request.getSource());

        try {
            log.info("Starting data ingestion");  // Includes all MDC fields
            // ... delegate to orchestrator
        } finally {
            enricher.clearEnriched();
        }
    }
}
```

### Application Layer (Orchestrators)

```java
@Service
@Slf4j
public class BatchOrchestrator {
    @Autowired
    private TraceContextHolder traceContextHolder;

    public void processBatch(String batchId) {
        // Set correlation ID to batch ID
        DistributedTraceContext context = traceContextHolder.withCorrelationId(batchId);
        traceContextHolder.populateMDC(context);

        MDC.put("batchId", batchId);
        MDC.put("operation", "PROCESS_BATCH");

        try {
            log.info("Batch processing started: itemCount={}", items.size());
            // ... processing
            log.info("Batch processing completed");
        } finally {
            MDC.remove("batchId");
            MDC.remove("operation");
            traceContextHolder.clearMDC();
        }
    }
}
```

### Infrastructure Layer (Repository, External Clients)

```java
@Slf4j
@Component
public class PubMedClient {
    public String fetchData(String query) {
        // MDC already populated by upper layers

        MDC.put("externalService", "PubMed");
        try {
            log.debug("Calling PubMed API: query={}", query);
            // ... API call
            log.info("PubMed API call succeeded: duration={}ms", duration);
        } catch (Exception e) {
            log.error("PubMed API call failed", e);
            throw e;
        } finally {
            MDC.remove("externalService");
        }
    }
}
```

### Domain Layer (Pure Java - Minimal MDC Usage)

```java
@Slf4j  // Or plain Logger declaration
public class PlanAggregate {
    private static final Logger log = LoggerFactory.getLogger(PlanAggregate.class);

    public void validate() {
        // Domain layer should NOT manipulate MDC
        // Only log business validation results
        if (planKey == null) {
            log.warn("Plan validation failed: planKey is null");
            throw new ValidationException("Plan key is required");
        }
    }
}
```

---

## Async Task MDC Propagation

### Thread Pool Configuration

```java
@Configuration
public class AsyncConfiguration {
    @Bean
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setTaskDecorator(new MdcTaskDecorator());  // Propagate MDC
        executor.initialize();
        return executor;
    }
}

public class MdcTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable task) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                task.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
```

### Message Queue Consumer

```java
@Slf4j
@RocketMQMessageListener(topic = "task-events", consumerGroup = "task-processor")
public class TaskEventListener implements RocketMQListener<TaskEvent> {
    @Autowired
    private TraceContextHolder traceContextHolder;

    @Override
    public void onMessage(TaskEvent event) {
        // Extract trace context from message headers
        String traceId = event.getHeader("traceId");
        String correlationId = event.getHeader("correlationId");

        DistributedTraceContext context = DistributedTraceContext.withCorrelation(traceId, correlationId);
        traceContextHolder.populateMDC(context);

        MDC.put("taskId", event.getTaskId());

        try {
            log.info("Processing task event: taskId={}", event.getTaskId());
            // ... processing
        } finally {
            MDC.remove("taskId");
            traceContextHolder.clearMDC();
        }
    }
}
```

---

## Log Filtering Examples (ELK/Splunk)

### Find all logs for specific trace

```
traceId:"abc123-def456-ghi789"
```

### Find all logs for specific batch

```
correlationId:"batch-20251015-001" OR batchId:"batch-20251015-001"
```

### Find all logs for specific user

```
userId:"user-12345"
```

### Find all errors in patra-ingest service

```
service:"patra-ingest" AND level:ERROR
```

### Find all external API calls

```
externalService:* AND level:(INFO OR WARN OR ERROR)
```

---

## Best Practices

### DO ✅

- Use `traceId` and `correlationId` for distributed tracing
- Add `batchId` for batch operations
- Add `entityId` when operating on specific entity
- Clear MDC in `finally` blocks
- Use descriptive operation names (`CREATE_PLAN`, not `operation1`)
- Propagate MDC to async tasks via `TaskDecorator`

### DON'T ❌

- Add large objects to MDC (performance impact)
- Add sensitive data to MDC (passwords, tokens)
- Manipulate MDC in domain layer (violates purity)
- Forget to clear MDC (causes thread pool pollution)
- Use inconsistent field names across services
- Add redundant fields already in log message

Note: Fields like `externalService` shown in examples are not part of the standard set; treat them as example-only unless standardized in your service.

---

## Field Naming Conventions

- **camelCase**: All MDC field names use camelCase (e.g., `userId`, `batchId`, `traceId`)
- **No abbreviations**: Use full words unless widely understood (e.g., `id` is ok, `usr` is not)
- **Suffixes**: Use `Id` suffix for identifiers (e.g., `userId`, `taskId`, `batchId`)
- **Prefixes**: Avoid prefixes like `mdc_` or `x_` (MDC namespace is implicit)

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-10-15 | Initial MDC fields specification |

---

## Related Documents

- [utility-api.md](./utility-api.md) - Java API for trace context management
- [integrations/trace-context-filter.md](./integrations/trace-context-filter.md) - HTTP boundary filter contract
- [integrations/feign-interceptor-contract.md](./integrations/feign-interceptor-contract.md) - Outbound call propagation
- [spring-boot-properties.md](./spring-boot-properties.md) - Spring Boot logging properties
- [schemas/logging-config.schema.yml](../03-schemas/logging-config.schema.yml) - Nacos logging schema
