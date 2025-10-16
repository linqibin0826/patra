# Data Model: Enhanced Logging System

**Feature**: 002-redesign-the-project | **Date**: 2025-10-15

## Overview

This document defines the conceptual data model for the enhanced logging system. Since logging is primarily an infrastructure concern rather than business domain logic, most entities are configuration/utility classes rather than persisted domain aggregates. The model focuses on the structures needed to support structured logging, trace context propagation, and sensitive data sanitization.

---

## Entity Relationships

```
┌─────────────────────┐
│   LogEntry          │  (Conceptual - not persisted as entity)
│─────────────────────│
│ timestamp           │
│ level               │
│ logger              │
│ thread              │
│ traceContext        │───┐
│ message             │   │
│ exception           │   │
└─────────────────────┘   │
                          │
                          ▼
                  ┌─────────────────────┐
                  │  DistributedTraceContext │  (Value Object)
                  │─────────────────────│
                  │ traceId             │
                  │ correlationId       │
                  │ spanId (optional)   │
                  │ parentSpanId (opt)  │
                  └─────────────────────┘

┌─────────────────────┐
│ SanitizationRule    │  (Configuration Entity)
│─────────────────────│
│ id                  │
│ name                │
│ ruleType            │───── Enum: FIELD_NAME | REGEX_PATTERN
│ pattern             │
│ replacement         │
│ enabled             │
│ priority            │
└─────────────────────┘

┌─────────────────────┐
│ LogLevelConfig      │  (Configuration - Nacos managed)
│─────────────────────│
│ logger              │  (e.g., "com.papertrace.ingest")
│ level               │───── Enum: ERROR | WARN | INFO | DEBUG | TRACE
│ service             │  (e.g., "patra-ingest")
│ environment         │  (e.g., "production")
└─────────────────────┘
```

---

## 1. LogEntry (Conceptual Model)

**Type**: Conceptual entity (NOT persisted as domain aggregate)

**Purpose**: Represents a single log record with all metadata and context. This is what gets written to log files/streams by Logback.

**Attributes**:

| Attribute | Type | Constraints | Description |
|-----------|------|-------------|-------------|
| `timestamp` | `Instant` | NOT NULL | ISO-8601 formatted timestamp when log entry was created |
| `level` | `LogLevel` | NOT NULL | Severity level (ERROR, WARN, INFO, DEBUG, TRACE) |
| `logger` | `String` | NOT NULL, max 255 chars | Fully qualified class name or logger name |
| `thread` | `String` | NOT NULL, max 100 chars | Name of the thread that generated the log |
| `traceContext` | `DistributedTraceContext` | NULLABLE | Distributed tracing context (trace ID, correlation ID, span ID) |
| `message` | `String` | NOT NULL | The actual log message (sanitized if contains sensitive data) |
| `exception` | `ThrowableProxy` | NULLABLE | Exception details including stack trace if applicable |
| `mdc` | `Map<String, String>` | NULLABLE | Additional Mapped Diagnostic Context key-value pairs |

**Lifecycle**: Created by SLF4J logger → processed by Logback appenders → written to destinations (console, file, log aggregator)

**Validation Rules**:
- Message must be sanitized before logging if contains user input or external data
- Exception stack traces must not include filtered frames (framework internals)
- Logger name should follow Java package naming conventions

**Notes**:
- This is NOT a domain aggregate (no business logic)
- No repository needed (Logback handles persistence to log files)
- Conceptual model guides MDC field names and log pattern configuration

---

## 2. DistributedTraceContext (Value Object)

**Type**: Immutable Value Object

**Purpose**: Encapsulates distributed tracing information that flows through the system, enabling correlation of log entries across services and operations.

**Attributes**:

| Attribute | Type | Constraints | Description |
|-----------|------|-------------|-------------|
| `traceId` | `String` | NOT NULL, UUID or SkyWalking format | Unique identifier for entire request flow across all services |
| `correlationId` | `String` | NULLABLE, UUID format | Business operation identifier (e.g., batch ID, idempotency key) |
| `spanId` | `String` | NULLABLE, SkyWalking format | Current operation/span identifier within the trace |
| `parentSpanId` | `String` | NULLABLE, SkyWalking format | Parent operation identifier for nested calls |

**Implementation**:

```java
/**
 * Immutable value object representing distributed tracing context.
 * Integrates with Apache SkyWalking for trace ID generation and propagation.
 */
public record DistributedTraceContext(
    @NonNull String traceId,
    String correlationId,
    String spanId,
    String parentSpanId
) {
    /**
     * Extracts trace context from current SkyWalking trace and MDC.
     */
    public static DistributedTraceContext fromCurrent() {
        String traceId = org.apache.skywalking.apm.toolkit.trace.TraceContext.traceId();  // SkyWalking API
        String correlationId = MDC.get("correlationId");
        String spanId = ActiveSpan.getSpanId();    // SkyWalking API
        String parentSpanId = ActiveSpan.getParentSpanId();

        return new DistributedTraceContext(
            traceId != null ? traceId : UUID.randomUUID().toString(),
            correlationId,
            spanId,
            parentSpanId
        );
    }

    /**
     * Populates SLF4J MDC with trace context for logging.
     */
    public void populateMDC() {
        MDC.put("traceId", traceId);
        if (correlationId != null) {
            MDC.put("correlationId", correlationId);
        }
        if (spanId != null) {
            MDC.put("spanId", spanId);
        }
    }

    /**
     * Creates minimal trace context with only trace ID (for external boundaries).
     */
    public static DistributedTraceContext minimal(String traceId) {
        return new DistributedTraceContext(traceId, null, null, null);
    }
}
```

**Validation Rules**:
- `traceId` is REQUIRED and must be non-empty
- If `traceId` is missing from SkyWalking, generate new UUID and log WARNING
- `correlationId` should be preserved across async boundaries if present
- `spanId` and `parentSpanId` managed by SkyWalking (read-only from application perspective)

**Usage**:
- Created by `TraceContextFilter` at servlet boundary
- Propagated via `MdcTaskDecorator` to async threads
- Extracted and forwarded in Feign interceptors for outbound calls
- Populated in RocketMQ message headers for event-driven flows

**Equality**: By `traceId` only (other fields are metadata for correlation)

---

## 3. SanitizationRule (Configuration Entity)

**Type**: Configuration Entity (potentially persisted in DB or configuration management)

**Purpose**: Defines patterns for identifying and masking sensitive data in log messages to prevent PII/credential exposure.

**Attributes**:

| Attribute | Type | Constraints | Description |
|-----------|------|-------------|-------------|
| `id` | `Long` | Primary Key | Unique identifier for the rule |
| `name` | `String` | NOT NULL, max 100 chars, unique | Human-readable rule name (e.g., "Email Sanitization") |
| `ruleType` | `RuleType` | NOT NULL | Type of matching: FIELD_NAME or REGEX_PATTERN |
| `pattern` | `String` | NOT NULL, max 500 chars | Field name or regex pattern to match |
| `replacement` | `String` | NOT NULL, max 50 chars | Replacement text (e.g., "***REDACTED***") |
| `enabled` | `Boolean` | NOT NULL, default TRUE | Whether the rule is currently active |
| `priority` | `Integer` | NOT NULL, default 100 | Execution order (lower number = higher priority) |
| `createdAt` | `Instant` | NOT NULL | When the rule was created |
| `updatedAt` | `Instant` | NOT NULL | Last modification timestamp |

**Enum: RuleType**:
- `FIELD_NAME`: Match JSON/structured field names (case-insensitive)
- `REGEX_PATTERN`: Match patterns in message text using regular expressions

**Implementation**:

```java
@Entity
@Table(name = "sanitization_rule")
@Getter
@Setter
public class SanitizationRule {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RuleType ruleType;

    @Column(nullable = false, length = 500)
    private String pattern;

    @Column(nullable = false, length = 50)
    private String replacement;

    @Column(nullable = false)
    private Boolean enabled = true;

    @Column(nullable = false)
    private Integer priority = 100;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    public enum RuleType {
        FIELD_NAME,      // Match JSON field names
        REGEX_PATTERN    // Match regex patterns in text
    }
}
```

**Validation Rules**:
- `pattern` must be valid regex if `ruleType` is REGEX_PATTERN
- `name` must be unique across all rules
- `priority` determines execution order (apply higher priority rules first)
- Disabled rules (`enabled = false`) are skipped during sanitization

**Default Rules** (seed data):

| Name | Type | Pattern | Replacement |
|------|------|---------|-------------|
| Password Field | FIELD_NAME | `password\|passwd\|pwd` | `***REDACTED***` |
| Token Field | FIELD_NAME | `token\|apiKey\|api_key\|secret\|authorization` | `***REDACTED***` |
| Email Pattern | REGEX_PATTERN | `\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z\|a-z]{2,}\b` | `***EMAIL***` |
| Credit Card | REGEX_PATTERN | `\b\d{4}[\s-]?\d{4}[\s-]?\d{4}[\s-]?\d{4}\b` | `***CARD***` |
| Phone Number | REGEX_PATTERN | `\b\d{3}[-.]?\d{3}[-.]?\d{4}\b` | `***PHONE***` |
| SSN | REGEX_PATTERN | `\b\d{3}-\d{2}-\d{4}\b` | `***SSN***` |
| Authorization Header | REGEX_PATTERN | `Authorization:\s*Bearer\s+[A-Za-z0-9\-._~+/]+=*` | `Authorization: Bearer ***TOKEN***` |

**Persistence**:
- **Option 1 (Simple)**: Hardcoded in `patra-common` as constants (YAGNI - start here)
- **Option 2 (Flexible)**: Store in Nacos configuration for dynamic updates
- **Option 3 (Complex)**: Store in database with CRUD UI (defer until proven necessary)

**Recommendation**: Start with Option 1 (hardcoded rules in `patra-common`). Migrate to Option 2 if rules need frequent updates. Only implement Option 3 if business users need to manage rules without code deployment.

---

## 4. LogLevelConfig (Configuration - Nacos Managed)

**Type**: Configuration entity (NOT persisted in application DB, managed by Nacos)

**Purpose**: Defines dynamic log level configuration per logger/package/class, enabling runtime troubleshooting without redeployment.

**Attributes**:

| Attribute | Type | Constraints | Description |
|-----------|------|-------------|-------------|
| `logger` | `String` | NOT NULL, max 255 chars | Logger name or package prefix (e.g., "com.papertrace.ingest") |
| `level` | `LogLevel` | NOT NULL | Target log level (ERROR, WARN, INFO, DEBUG, TRACE) |
| `service` | `String` | NULLABLE, max 50 chars | Service name for service-specific overrides (e.g., "patra-ingest") |
| `environment` | `String` | NULLABLE, max 20 chars | Environment name (e.g., "production", "staging", "dev") |

**Enum: LogLevel**:
```java
public enum LogLevel {
    ERROR(40),
    WARN(30),
    INFO(20),
    DEBUG(10),
    TRACE(0);

    private final int severity;

    LogLevel(int severity) {
        this.severity = severity;
    }

    public boolean isEnabledFor(LogLevel threshold) {
        return this.severity >= threshold.severity;
    }
}
```

**Configuration Structure (Nacos YAML)**:

```yaml
# Config ID: logging-common.yml (shared across all services)
logging:
  level:
    root: INFO                              # Default for all loggers
    com.papertrace: DEBUG                   # Project-wide debug
    com.papertrace.domain: INFO             # Keep domain logs minimal
    org.springframework.web: WARN           # Reduce framework noise
    org.mybatis: WARN                       # Reduce MyBatis noise
    com.zaxxer.hikari: INFO                 # Connection pool events

# Config ID: logging-patra-ingest.yml (service-specific overrides)
logging:
  level:
    com.papertrace.ingest.adapter.job: DEBUG   # Debug scheduled jobs
    com.papertrace.ingest.infra.client: TRACE  # Trace external API calls
```

**Matching Rules**:
1. Exact match takes precedence (e.g., `com.papertrace.ingest.SomeClass`)
2. Longest prefix match (e.g., `com.papertrace.ingest` beats `com.papertrace`)
3. Root logger (`root`) is fallback for unmatched loggers
4. Service-specific config overrides common config

**Dynamic Update Mechanism**:
```java
@Configuration
public class DynamicLoggingConfiguration {
    @Autowired
    private LoggingSystem loggingSystem;

    @NacosConfigListener(dataId = "logging-common.yml")
    public void onCommonConfigChange(String config) {
        applyLogLevels(parseYaml(config));
    }

    @NacosConfigListener(dataId = "logging-${spring.application.name}.yml")
    public void onServiceConfigChange(String config) {
        applyLogLevels(parseYaml(config));
    }

    private void applyLogLevels(Map<String, LogLevel> levels) {
        levels.forEach((logger, level) -> {
            loggingSystem.setLogLevel(logger, level);
            log.info("Log level updated: logger={}, level={}", logger, level);
        });
    }
}
```

**Validation Rules**:
- Logger name must follow Java package naming conventions
- Changes take effect within 60 seconds (Nacos refresh interval)
- Invalid log levels are ignored with WARNING logged
- Root logger cannot be disabled (minimum WARN enforced)

**Audit Trail**:
- Nacos provides built-in change history (who changed what when)
- Application logs all log level changes at INFO level
- Monitor for excessive DEBUG/TRACE in production (performance risk)

**Best Practices**:
- Production default: `root=INFO`, `com.papertrace=INFO`
- Staging default: `root=DEBUG`, `com.papertrace=DEBUG`
- Development default: `root=DEBUG`, `com.papertrace=TRACE`
- Never set `root=TRACE` in production (performance impact)
- Temporary DEBUG for troubleshooting, revert after issue resolved

---

## 5. ApiCallLog (Conceptual Model)

**Type**: Conceptual entity (NOT persisted, logged as structured message)

**Purpose**: Represents metadata for external API calls to support audit compliance (FR-006) and troubleshooting.

**Attributes**:

| Attribute | Type | Constraints | Description |
|-----------|------|-------------|-------------|
| `service` | `String` | NOT NULL, max 50 chars | External service name (e.g., "PubMed", "EPMC") |
| `url` | `String` | NOT NULL, max 500 chars | Full request URL (sanitized for query params) |
| `method` | `String` | NOT NULL, max 10 chars | HTTP method (GET, POST, PUT, DELETE) |
| `statusCode` | `Integer` | NOT NULL, 100-599 | HTTP response status code |
| `durationMs` | `Long` | NOT NULL, >= 0 | Request duration in milliseconds |
| `requestBody` | `String` | NULLABLE, sanitized | Request body (truncated if >1000 chars, sanitized) |
| `responseBody` | `String` | NULLABLE, sanitized | Response body (truncated if >1000 chars, sanitized) |
| `errorMessage` | `String` | NULLABLE, max 500 chars | Error message if request failed |

**Logging Format**:
```java
// Success (2xx)
log.info("API call succeeded: service={}, url={}, method={}, status={}, duration={}ms",
         service, url, method, statusCode, durationMs);
log.debug("Request: {}, Response: {}", sanitizedRequest, sanitizedResponse);

// Client error (4xx)
log.warn("API call client error: service={}, url={}, method={}, status={}, duration={}ms",
         service, url, method, statusCode, durationMs);

// Server error (5xx)
log.error("API call failed: service={}, url={}, method={}, status={}, duration={}ms, error={}",
          service, url, method, statusCode, durationMs, errorMessage);
```

**Usage**: Logged by Feign interceptors, RestTemplate interceptors, or WebClient filters.

---

## 6. BatchProcessingLog (Conceptual Model)

**Type**: Conceptual entity (NOT persisted, logged as structured message)

**Purpose**: Represents summary information for batch processing operations to prevent log flooding while maintaining visibility.

**Attributes**:

| Attribute | Type | Constraints | Description |
|-----------|------|-------------|-------------|
| `batchId` | `String` | NOT NULL, UUID format | Unique identifier for this batch operation |
| `batchType` | `String` | NOT NULL, max 50 chars | Type of batch (e.g., "PubMed Ingest", "Data Parsing") |
| `itemCount` | `Integer` | NOT NULL, >= 0 | Total number of items in the batch |
| `successCount` | `Integer` | NOT NULL, >= 0 | Number of items processed successfully |
| `errorCount` | `Integer` | NOT NULL, >= 0 | Number of items that failed processing |
| `startTime` | `Instant` | NOT NULL | Batch processing start timestamp |
| `endTime` | `Instant` | NULLABLE | Batch processing end timestamp (null if in progress) |
| `durationMs` | `Long` | NULLABLE, >= 0 | Total processing duration in milliseconds |

**Logging Format**:
```java
// Batch start
log.info("Batch processing started: batchId={}, type={}, itemCount={}",
         batchId, batchType, itemCount);

// Batch completion
log.info("Batch processing completed: batchId={}, type={}, success={}, errors={}, duration={}ms",
         batchId, batchType, successCount, errorCount, durationMs);

// Individual item failure
log.error("Batch item failed: batchId={}, itemId={}, error={}",
          batchId, itemId, errorMessage, exception);
```

**Validation Rules**:
- `successCount + errorCount <= itemCount`
- `batchId` must be unique and placed in MDC for filtering
- Individual item errors logged separately at ERROR level
- Progress updates every N items (e.g., 1000) at DEBUG level

---

## Entity Storage Summary

| Entity | Persistence | Storage Location | Mutability |
|--------|-------------|------------------|------------|
| `LogEntry` | Transient (written to logs) | Log files/aggregator | Immutable |
| `DistributedTraceContext` | Transient (in MDC) | Thread-local storage | Immutable |
| `SanitizationRule` | Configuration | `patra-common` constants (initially) | Read-only at runtime |
| `LogLevelConfig` | Configuration | Nacos config server | Dynamic (hot reload) |
| `ApiCallLog` | Transient (logged) | Log files/aggregator | Immutable |
| `BatchProcessingLog` | Transient (logged) | Log files/aggregator | Immutable |

---

## State Transitions

### Trace Context Lifecycle

```
[Request Arrives] → [TraceContextFilter extracts/generates trace ID]
                → [Populate MDC]
                → [Business logic executes (logs include trace ID)]
                → [Async operation spawned] → [MdcTaskDecorator copies MDC]
                → [Response sent / MDC cleared]
```

### Log Level Configuration Lifecycle

```
[Nacos config updated] → [Config change event triggered]
                      → [Application receives new config]
                      → [LoggingSystem.setLogLevel() called]
                      → [New level effective immediately]
                      → [Audit log written]
```

---

## Validation & Invariants

### Cross-Entity Invariants

1. **Trace ID Consistency**: All log entries within a request flow MUST have the same `traceId`
2. **Sanitization Coverage**: Any log message containing user input or external data MUST pass through sanitization
3. **Level Semantics**: Log level usage MUST follow defined semantics (ERROR=actionable, INFO=business events, etc.)
4. **Performance**: Sanitization MUST complete in <50ms p95, async appenders MUST not block application threads
5. **No PII Leakage**: Zero instances of sensitive data in logs (validated via automated scanning)

---

## Notes for Implementation

- **Domain Layer**: Keep pure Java, use plain `Logger` declaration (no Lombok)
- **Other Layers**: Use `@Slf4j` annotation, call sanitization explicitly when needed
- **Testing**: Unit test sanitization rules with known sensitive patterns, integration test trace context propagation
- **Migration**: Start with hardcoded sanitization rules, migrate to Nacos if dynamic updates needed
- **Performance**: Monitor log volume and sanitization overhead during rollout

---

## Next Steps

Proceed to generate:
- `contracts/` - API specifications for logging utilities (if exposing REST endpoints)
- `quickstart.md` - Developer guide for adopting new logging standards
