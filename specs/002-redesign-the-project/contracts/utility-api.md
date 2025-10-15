# Logging Utility API Contracts

**Version**: 1.0.0 | **Package**: `com.papertrace.common.logging`

## Overview

This document specifies the Java API contracts for logging utilities provided by `patra-common`. These utilities support sensitive data sanitization, trace context management, and log message enrichment.

---

## 1. LogSanitizer

**Package**: `com.papertrace.common.logging.sanitizer`
**Purpose**: Automatically sanitize sensitive data in log messages to prevent PII/credential exposure

### Interface

```java
package com.papertrace.common.logging.sanitizer;

/**
 * Sanitizes sensitive data from log messages using configurable rules.
 * Thread-safe singleton managed by Spring.
 *
 * @since 1.0.0
 */
public interface LogSanitizer {

    /**
     * Sanitizes a log message by replacing sensitive data with masked values.
     *
     * @param message the original log message (may contain sensitive data)
     * @return sanitized message with sensitive data replaced
     * @throws IllegalArgumentException if message is null
     */
    @NonNull
    String sanitize(@NonNull String message);

    /**
     * Sanitizes a JSON string by replacing sensitive field values.
     *
     * @param json the JSON string (may contain sensitive fields)
     * @return sanitized JSON with sensitive fields masked
     * @throws IllegalArgumentException if json is null or invalid JSON
     */
    @NonNull
    String sanitizeJson(@NonNull String json);

    /**
     * Sanitizes an object by converting to string and applying sanitization rules.
     * Useful for logging DTOs or entities.
     *
     * @param object the object to sanitize (toString() will be called)
     * @return sanitized string representation
     * @throws IllegalArgumentException if object is null
     */
    @NonNull
    String sanitizeObject(@NonNull Object object);

    /**
     * Checks if a message contains potentially sensitive data without sanitizing.
     * Useful for conditional logging decisions.
     *
     * @param message the message to check
     * @return true if message matches any sanitization rule patterns
     */
    boolean containsSensitiveData(@NonNull String message);
}
```

### Default Implementation

```java
package com.papertrace.common.logging.sanitizer;

import org.springframework.stereotype.Component;
import java.util.regex.Pattern;
import java.util.Set;

/**
 * Default implementation using regex patterns and field name matching.
 */
@Component
public class DefaultLogSanitizer implements LogSanitizer {

    private static final String REDACTED = "***REDACTED***";

    // Field name patterns (case-insensitive)
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
        "password", "passwd", "pwd",
        "token", "apiKey", "api_key", "secret",
        "authorization", "auth",
        "ssn", "social_security_number",
        "credit_card", "creditCard", "card_number"
    );

    // Regex patterns
    private static final Pattern EMAIL_PATTERN =
        Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");
    private static final Pattern CREDIT_CARD_PATTERN =
        Pattern.compile("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b");
    private static final Pattern PHONE_PATTERN =
        Pattern.compile("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b");
    private static final Pattern AUTH_HEADER_PATTERN =
        Pattern.compile("Authorization:\\s*Bearer\\s+[A-Za-z0-9\\-._~+/]+=*");

    @Override
    public String sanitize(String message) {
        if (message == null) {
            throw new IllegalArgumentException("Message cannot be null");
        }

        String sanitized = message;

        // Sanitize field names
        for (String field : SENSITIVE_FIELDS) {
            sanitized = sanitized.replaceAll(
                "(?i)\"" + field + "\"\\s*:\\s*\"[^\"]+\"",
                "\"" + field + "\":\"" + REDACTED + "\""
            );
        }

        // Sanitize patterns
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll("***EMAIL***");
        sanitized = CREDIT_CARD_PATTERN.matcher(sanitized).replaceAll("***CARD***");
        sanitized = PHONE_PATTERN.matcher(sanitized).replaceAll("***PHONE***");
        sanitized = AUTH_HEADER_PATTERN.matcher(sanitized).replaceAll("Authorization: Bearer ***TOKEN***");

        return sanitized;
    }

    @Override
    public String sanitizeJson(String json) {
        // Delegate to sanitize() for now
        // Future: Use JSON parser for precise field sanitization
        return sanitize(json);
    }

    @Override
    public String sanitizeObject(Object object) {
        if (object == null) {
            throw new IllegalArgumentException("Object cannot be null");
        }
        return sanitize(object.toString());
    }

    @Override
    public boolean containsSensitiveData(String message) {
        if (message == null) return false;

        for (String field : SENSITIVE_FIELDS) {
            if (message.toLowerCase().contains(field)) {
                return true;
            }
        }

        return EMAIL_PATTERN.matcher(message).find()
            || CREDIT_CARD_PATTERN.matcher(message).find()
            || PHONE_PATTERN.matcher(message).find()
            || AUTH_HEADER_PATTERN.matcher(message).find();
    }
}
```

### Usage Example

```java
@Slf4j
@Service
public class UserService {
    @Autowired
    private LogSanitizer sanitizer;

    public void createUser(User user) {
        // Sanitize before logging user input
        log.info("Creating user: {}", sanitizer.sanitizeObject(user));
    }

    public void processApiResponse(String jsonResponse) {
        // Sanitize JSON responses
        log.debug("API response: {}", sanitizer.sanitizeJson(jsonResponse));
    }
}
```

---

## 2. TraceContextHolder

**Package**: `com.papertrace.common.logging.context`
**Purpose**: Extract and manage distributed trace context from SkyWalking and MDC

### Interface

```java
package com.papertrace.common.logging.context;

import java.util.Optional;

/**
 * Manages distributed trace context for logging and propagation.
 * Integrates with Apache SkyWalking for trace ID generation.
 *
 * @since 1.0.0
 */
public interface TraceContextHolder {

    /**
     * Extracts current trace context from SkyWalking and MDC.
     *
     * @return current trace context, or empty if not available
     */
    Optional<TraceContext> current();

    /**
     * Extracts current trace context, generating new one if not available.
     *
     * @return current or newly generated trace context
     */
    @NonNull
    TraceContext currentOrGenerate();

    /**
     * Populates SLF4J MDC with trace context fields.
     *
     * @param context the trace context to populate
     */
    void populateMDC(@NonNull TraceContext context);

    /**
     * Clears trace context from MDC.
     * Should be called in finally blocks to prevent thread pool pollution.
     */
    void clearMDC();

    /**
     * Creates a new trace context with generated trace ID.
     * Use when starting a new trace at system boundary.
     *
     * @return newly generated trace context
     */
    @NonNull
    TraceContext generateNew();

    /**
     * Creates trace context with specific correlation ID.
     * Use for batch operations or business process tracking.
     *
     * @param correlationId business operation identifier
     * @return trace context with specified correlation ID
     */
    @NonNull
    TraceContext withCorrelationId(@NonNull String correlationId);
}
```

### Value Object: TraceContext

```java
package com.papertrace.common.logging.context;

import lombok.NonNull;

/**
 * Immutable value object representing distributed trace context.
 *
 * @param traceId unique identifier for entire request flow
 * @param correlationId business operation identifier (optional)
 * @param spanId current operation identifier (optional)
 * @param parentSpanId parent operation identifier (optional)
 */
public record TraceContext(
    @NonNull String traceId,
    String correlationId,
    String spanId,
    String parentSpanId
) {
    /**
     * Creates minimal trace context with only trace ID.
     */
    public static TraceContext minimal(String traceId) {
        return new TraceContext(traceId, null, null, null);
    }

    /**
     * Creates trace context with trace ID and correlation ID.
     */
    public static TraceContext withCorrelation(String traceId, String correlationId) {
        return new TraceContext(traceId, correlationId, null, null);
    }
}
```

### Default Implementation

```java
package com.papertrace.common.logging.context;

import org.apache.skywalking.apm.toolkit.trace.TraceContext;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.UUID;

@Component
public class DefaultTraceContextHolder implements TraceContextHolder {

    @Override
    public Optional<TraceContext> current() {
        String traceId = TraceContext.traceId();
        if (traceId == null || traceId.isEmpty() || "N/A".equals(traceId)) {
            return Optional.empty();
        }

        String correlationId = MDC.get("correlationId");
        String spanId = TraceContext.spanId();
        String parentSpanId = MDC.get("parentSpanId");

        return Optional.of(new TraceContext(traceId, correlationId, spanId, parentSpanId));
    }

    @Override
    public TraceContext currentOrGenerate() {
        return current().orElseGet(this::generateNew);
    }

    @Override
    public void populateMDC(TraceContext context) {
        MDC.put("traceId", context.traceId());
        if (context.correlationId() != null) {
            MDC.put("correlationId", context.correlationId());
        }
        if (context.spanId() != null) {
            MDC.put("spanId", context.spanId());
        }
    }

    @Override
    public void clearMDC() {
        MDC.remove("traceId");
        MDC.remove("correlationId");
        MDC.remove("spanId");
        MDC.remove("parentSpanId");
    }

    @Override
    public TraceContext generateNew() {
        String traceId = UUID.randomUUID().toString();
        return TraceContext.minimal(traceId);
    }

    @Override
    public TraceContext withCorrelationId(String correlationId) {
        String traceId = TraceContext.traceId();
        if (traceId == null || "N/A".equals(traceId)) {
            traceId = UUID.randomUUID().toString();
        }
        return TraceContext.withCorrelation(traceId, correlationId);
    }
}
```

### Usage Example

```java
@Slf4j
@Service
public class BatchOrchestrator {
    @Autowired
    private TraceContextHolder traceContextHolder;

    @Async
    public void processBatchAsync(String batchId) {
        // Generate trace context with correlation ID
        TraceContext context = traceContextHolder.withCorrelationId(batchId);
        traceContextHolder.populateMDC(context);

        try {
            log.info("Starting batch processing: batchId={}", batchId);
            // ... business logic
        } finally {
            traceContextHolder.clearMDC();
        }
    }
}
```

---

## 3. LogContextEnricher

**Package**: `com.papertrace.common.logging.context`
**Purpose**: Enrich MDC with additional contextual information beyond trace context

### Interface

```java
package com.papertrace.common.logging.context;

import java.util.Map;

/**
 * Enriches SLF4J MDC with additional contextual information.
 *
 * @since 1.0.0
 */
public interface LogContextEnricher {

    /**
     * Adds a key-value pair to MDC for the current thread.
     *
     * @param key MDC key
     * @param value MDC value
     */
    void enrich(@NonNull String key, @NonNull String value);

    /**
     * Adds multiple key-value pairs to MDC.
     *
     * @param context map of MDC entries
     */
    void enrichAll(@NonNull Map<String, String> context);

    /**
     * Removes a key from MDC.
     *
     * @param key MDC key to remove
     */
    void remove(@NonNull String key);

    /**
     * Clears all enriched context (except trace context).
     */
    void clearEnriched();

    /**
     * Copies current MDC context for async propagation.
     *
     * @return copy of MDC context map
     */
    @NonNull
    Map<String, String> copyContext();

    /**
     * Restores MDC context (for async tasks).
     *
     * @param context MDC context map to restore
     */
    void restoreContext(@NonNull Map<String, String> context);
}
```

### Default Implementation

```java
package com.papertrace.common.logging.context;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.HashMap;

@Component
public class DefaultLogContextEnricher implements LogContextEnricher {

    // Reserved keys managed by trace context
    private static final Set<String> RESERVED_KEYS = Set.of(
        "traceId", "correlationId", "spanId", "parentSpanId"
    );

    @Override
    public void enrich(String key, String value) {
        if (RESERVED_KEYS.contains(key)) {
            throw new IllegalArgumentException("Key '" + key + "' is reserved for trace context");
        }
        MDC.put(key, value);
    }

    @Override
    public void enrichAll(Map<String, String> context) {
        context.forEach(this::enrich);
    }

    @Override
    public void remove(String key) {
        if (!RESERVED_KEYS.contains(key)) {
            MDC.remove(key);
        }
    }

    @Override
    public void clearEnriched() {
        Map<String, String> current = MDC.getCopyOfContextMap();
        if (current != null) {
            current.keySet().stream()
                .filter(key -> !RESERVED_KEYS.contains(key))
                .forEach(MDC::remove);
        }
    }

    @Override
    public Map<String, String> copyContext() {
        Map<String, String> context = MDC.getCopyOfContextMap();
        return context != null ? new HashMap<>(context) : new HashMap<>();
    }

    @Override
    public void restoreContext(Map<String, String> context) {
        MDC.setContextMap(context);
    }
}
```

### Usage Example

```java
@Slf4j
@Service
public class PaymentService {
    @Autowired
    private LogContextEnricher enricher;

    public void processPayment(Payment payment) {
        // Enrich MDC with business context
        enricher.enrich("userId", payment.getUserId());
        enricher.enrich("transactionId", payment.getTransactionId());

        try {
            log.info("Processing payment");  // Logs include userId and transactionId
            // ... business logic
        } finally {
            enricher.clearEnriched();
        }
    }
}
```

---

## API Usage Guidelines

### When to Use Each API

| API | Use Case |
|-----|----------|
| `LogSanitizer.sanitize()` | Logging user input, external API responses, any data that might contain PII |
| `TraceContextHolder.current()` | Checking if trace context is available (e.g., for conditional logging) |
| `TraceContextHolder.populateMDC()` | At system boundaries (filters, interceptors, message consumers) |
| `TraceContextHolder.withCorrelationId()` | Starting batch operations or business processes with specific ID |
| `LogContextEnricher.enrich()` | Adding business context (user ID, transaction ID, entity ID) to logs |

### Best Practices

1. **Always sanitize external data**: User input, API responses, database query results
2. **Populate trace context early**: At adapter layer boundaries (filters, interceptors)
3. **Clear MDC in finally blocks**: Prevent thread pool pollution
4. **Use correlation IDs for batches**: Makes filtering batch-related logs easy
5. **Enrich with business context sparingly**: Only add context that aids troubleshooting

### Performance Considerations

- Sanitization adds ~1-5ms per call (acceptable for INFO+ logs)
- MDC operations are thread-local (fast, no synchronization)
- Avoid sanitizing DEBUG/TRACE logs unless necessary (performance)
- Cache sanitization results for repeated log messages

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-10-15 | Initial API design |
