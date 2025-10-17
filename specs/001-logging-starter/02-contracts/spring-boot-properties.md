# Spring Boot Logging Properties

**Version**: 1.0.0 | **Last Updated**: 2025-10-15

## Overview

This document specifies Spring Boot configuration properties for the `patra-spring-boot-starter-logging` module. These properties control logging behavior, trace context propagation, and sanitization settings.

---

## Property Prefix

All logging-related properties use the prefix: `papertrace.logging`

---

## Configuration Properties

### 1. Trace Context Configuration

```yaml
papertrace:
  logging:
    trace:
      enabled: true  # Enable/disable trace context propagation
      auto-generate: true  # Generate new trace ID if not present
      header-name: X-Trace-ID  # HTTP header name for trace ID
      correlation-header: X-Correlation-ID  # HTTP header for correlation ID
      warn-on-missing: true  # Log warning if trace context missing
```

**Property Details**:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `papertrace.logging.trace.enabled` | Boolean | `true` | Enable trace context propagation filter |
| `papertrace.logging.trace.auto-generate` | Boolean | `true` | Generate new trace ID if missing (vs. log warning) |
| `papertrace.logging.trace.header-name` | String | `X-Trace-ID` | HTTP header name for extracting trace ID |
| `papertrace.logging.trace.correlation-header` | String | `X-Correlation-ID` | HTTP header for correlation ID |
| `papertrace.logging.trace.warn-on-missing` | Boolean | `true` | Log WARNING when trace context not propagated |

**Usage**:
```yaml
# application.yml
papertrace:
  logging:
    trace:
      enabled: true
      warn-on-missing: true
```

---

### 2. Sanitization Configuration

```yaml
papertrace:
  logging:
    sanitization:
      enabled: true  # Enable/disable sensitive data sanitization
      mode: MANUAL  # AUTO | MANUAL | DISABLED
      patterns:
        - field-name:password
        - field-name:token
        - regex:email  # Predefined pattern
        - regex:credit-card  # Predefined pattern
      custom-patterns:
        - name: API Key
          pattern: "api[_-]?key"
          replacement: "***API_KEY***"
      performance:
        cache-results: true  # Cache sanitization results
        cache-size: 1000  # Max cached entries
        max-message-length: 10000  # Max message length to sanitize
```

**Property Details**:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `papertrace.logging.sanitization.enabled` | Boolean | `true` | Enable sanitization |
| `papertrace.logging.sanitization.mode` | Enum | `MANUAL` | AUTO (aspect-based), MANUAL (explicit calls), DISABLED |
| `papertrace.logging.sanitization.patterns` | List<String> | See below | Predefined patterns to enable |
| `papertrace.logging.sanitization.custom-patterns` | List<Pattern> | Empty | Custom sanitization patterns |
| `papertrace.logging.sanitization.performance.cache-results` | Boolean | `true` | Cache sanitized messages |
| `papertrace.logging.sanitization.performance.cache-size` | Integer | `1000` | LRU cache size |
| `papertrace.logging.sanitization.performance.max-message-length` | Integer | `10000` | Max message length (truncate longer) |

**Sanitization Modes**:

- **AUTO**: Aspect automatically sanitizes log statements (convenience, slight performance overhead)
- **MANUAL**: Developers explicitly call `LogSanitizer.sanitize()` (recommended)
- **DISABLED**: No sanitization (development only, NEVER in production)

**Predefined Patterns**:
- `field-name:password` - Matches password fields
- `field-name:token` - Matches token fields
- `field-name:apiKey` - Matches API key fields
- `regex:email` - Matches email addresses
- `regex:credit-card` - Matches credit card numbers
- `regex:phone` - Matches phone numbers
- `regex:ssn` - Matches Social Security Numbers
- `regex:auth-header` - Matches Authorization headers

**Usage**:
```yaml
# application-production.yml
papertrace:
  logging:
    sanitization:
      enabled: true
      mode: MANUAL  # Require explicit sanitization calls
      patterns:
        - field-name:password
        - field-name:token
        - regex:email
      custom-patterns:
        - name: Internal API Key
          pattern: "internal[_-]?api[_-]?key:\\s*[A-Za-z0-9]+"
          replacement: "***INTERNAL_API_KEY***"
```

---

### 3. MDC Configuration

```yaml
papertrace:
  logging:
    mdc:
      auto-populate:
        service: true  # Auto-populate service name
        environment: true  # Auto-populate environment
        hostname: true  # Auto-populate hostname
        client-ip: true  # Auto-populate client IP
        request-uri: true  # Auto-populate request URI
        http-method: true  # Auto-populate HTTP method
      field-names:
        service: service  # MDC key for service name
        environment: environment
        hostname: hostname
        client-ip: clientIp
        request-uri: requestUri
        http-method: httpMethod
```

**Property Details**:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `papertrace.logging.mdc.auto-populate.*` | Boolean | `true` | Auto-populate specific MDC fields |
| `papertrace.logging.mdc.field-names.*` | String | See above | Customize MDC field names |

**Usage**:
```yaml
# application.yml
papertrace:
  logging:
    mdc:
      auto-populate:
        service: true
        client-ip: true
      field-names:
        service: serviceName  # Override default field name
```

---

### MDC Key Remapping

You can remap MDC key names via `papertrace.logging.mdc.field-names.*`. All framework components (filters/interceptors/utilities) must read and write MDC using this mapping. If you change keys, update:
- Logback patterns (e.g., `%X{serviceName}` instead of `%X{service}`)
- Queries/dashboards in your log aggregation tool

Default mapping:
- `service` → `service`
- `environment` → `environment`
- `hostname` → `hostname`
- `client-ip` → `clientIp`
- `request-uri` → `requestUri`
- `http-method` → `httpMethod`
- Trace keys (`traceId`, `correlationId`, `spanId`, `parentSpanId`) are also respected if remapped under `field-names`.

---

### Standard vs Example‑Only Fields

The standardized MDC keys are defined in `mdc-fields-reference.md`. Some examples (e.g., `externalService`) illustrate patterns but are not standardized. Treat them as service‑local unless explicitly adopted in your service’s standards.

---

### 4. Async Logging Configuration

```yaml
papertrace:
  logging:
    async:
      enabled: true  # Enable async appenders
      queue-size: 512  # Max queued log entries
      discarding-threshold: 0  # Queue % to start discarding DEBUG/TRACE
      never-block: false  # Block if queue full (vs. discard)
      include-caller-data: false  # Include caller class/method (expensive)
```

**Property Details**:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `papertrace.logging.async.enabled` | Boolean | `true` | Enable async appenders |
| `papertrace.logging.async.queue-size` | Integer | `512` | Max queued log entries |
| `papertrace.logging.async.discarding-threshold` | Integer | `0` | Start discarding at X% queue full (0=never) |
| `papertrace.logging.async.never-block` | Boolean | `false` | Never block threads (discard logs instead) |
| `papertrace.logging.async.include-caller-data` | Boolean | `false` | Capture caller info (performance impact) |

**Usage**:
```yaml
# application-production.yml
papertrace:
  logging:
    async:
      enabled: true
      queue-size: 1024  # Higher for production
      discarding-threshold: 200  # Discard DEBUG when 80% full
```

---

### 5. External API Call Logging

```yaml
papertrace:
  logging:
    api:
      enabled: true  # Enable automatic API call logging
      log-request-body: false  # Log request bodies (INFO level)
      log-response-body: false  # Log response bodies (INFO level)
      log-headers: false  # Log HTTP headers (sanitized)
      max-body-length: 1000  # Max body length to log
      success-threshold-ms: 3000  # Log WARN if response > threshold
      excluded-paths:
        - /health
        - /metrics
```

**Property Details**:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `papertrace.logging.api.enabled` | Boolean | `true` | Enable Feign/RestTemplate logging |
| `papertrace.logging.api.log-request-body` | Boolean | `false` | Log request bodies at INFO (DEBUG always logs) |
| `papertrace.logging.api.log-response-body` | Boolean | `false` | Log response bodies at INFO (DEBUG always logs) |
| `papertrace.logging.api.log-headers` | Boolean | `false` | Log HTTP headers (sanitized) |
| `papertrace.logging.api.max-body-length` | Integer | `1000` | Truncate bodies longer than this |
| `papertrace.logging.api.success-threshold-ms` | Long | `3000` | Log WARN if call takes longer |
| `papertrace.logging.api.excluded-paths` | List<String> | Empty | Exclude paths from logging |

**Usage**:
```yaml
# application-staging.yml
papertrace:
  logging:
    api:
      enabled: true
      log-request-body: true  # Enable in staging for debugging
      log-response-body: true
      success-threshold-ms: 2000
      excluded-paths:
        - /actuator/health
```

---

### 6. Batch Processing Logging

```yaml
papertrace:
  logging:
    batch:
      enabled: true  # Enable batch logging utilities
      summary-interval: 1000  # Log progress every N items
      log-individual-failures: true  # Log each item failure
      max-failures-logged: 100  # Max individual failures to log
```

**Property Details**:

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `papertrace.logging.batch.enabled` | Boolean | `true` | Enable batch logging |
| `papertrace.logging.batch.summary-interval` | Integer | `1000` | Log progress every N items |
| `papertrace.logging.batch.log-individual-failures` | Boolean | `true` | Log each item failure at ERROR |
| `papertrace.logging.batch.max-failures-logged` | Integer | `100` | Stop logging failures after N |

**Usage**:
```yaml
# application.yml
papertrace:
  logging:
    batch:
      summary-interval: 500  # More frequent updates
      max-failures-logged: 50
```

---

## Environment-Specific Configurations

### Development

```yaml
# application-dev.yml
logging:
  level:
    root: DEBUG

papertrace:
  logging:
    trace:
      warn-on-missing: false  # Don't warn in dev
    sanitization:
      mode: DISABLED  # Disable in dev (easier debugging)
    api:
      log-request-body: true
      log-response-body: true
```

### Staging

```yaml
# application-staging.yml
logging:
  level:
    root: DEBUG
    com.papertrace: DEBUG

papertrace:
  logging:
    sanitization:
      mode: MANUAL  # Enable but manual
    api:
      log-request-body: true
      log-response-body: true
```

### Production

```yaml
# application-production.yml
logging:
  level:
    root: INFO
    com.papertrace: INFO

papertrace:
  logging:
    sanitization:
      mode: MANUAL  # Require explicit calls
      enabled: true
    async:
      queue-size: 1024
      discarding-threshold: 200
    api:
      log-request-body: false
      log-response-body: false
      success-threshold-ms: 2000
```

---

## Complete Example

```yaml
# application.yml (base config)
spring:
  application:
    name: patra-ingest

papertrace:
  logging:
    # Trace context
    trace:
      enabled: true
      auto-generate: true
      warn-on-missing: true

    # Sanitization
    sanitization:
      enabled: true
      mode: MANUAL
      patterns:
        - field-name:password
        - field-name:token
        - regex:email
        - regex:credit-card

    # MDC auto-population
    mdc:
      auto-populate:
        service: true
        environment: true
        client-ip: true

    # Async logging
    async:
      enabled: true
      queue-size: 512
      discarding-threshold: 0

    # API logging
    api:
      enabled: true
      success-threshold-ms: 3000
      excluded-paths:
        - /actuator/health
        - /actuator/metrics

    # Batch logging
    batch:
      enabled: true
      summary-interval: 1000
      max-failures-logged: 100

# Standard Spring Boot logging
logging:
  level:
    root: INFO
    com.papertrace: DEBUG
  pattern:
    console: "%d{ISO8601} [%X{traceId}] [%X{correlationId}] [%X{service}] [%thread] %-5level %logger{36} - %msg%n"
```

---

## Property Validation

Properties are validated at startup:
- Integer ranges enforced (e.g., `queue-size` must be 64-8192)
- Enum values checked (e.g., `mode` must be AUTO|MANUAL|DISABLED)
- Invalid properties cause startup failure with descriptive error

**Validation Errors**:
```
Configuration property 'papertrace.logging.async.queue-size' with value '32' is invalid.
Expected: integer between 64 and 8192.
```

---

## Actuator Integration

### Endpoints

**GET** `/actuator/loggers`
- View current log levels for all loggers

**POST** `/actuator/loggers/{loggerName}`
- Change log level dynamically (alternative to Nacos)

Optional custom endpoint (if enabled by the starter):

**GET** `/actuator/loggers/trace-context`
- View current trace context status (implementation-defined payload)

**Example**:
```bash
# View log levels
curl http://localhost:8080/actuator/loggers

# Change log level
curl -X POST http://localhost:8080/actuator/loggers/com.papertrace.ingest \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'
```

---

## Version History

| Version | Date | Changes |
|---------|------|---------|
| 1.0.0 | 2025-10-15 | Initial properties specification |

---

## Related Documents

- [schemas/logging-config.schema.yml](../03-schemas/logging-config.schema.yml) - Nacos configuration schema
- [utility-api.md](./utility-api.md) - Java API specifications
- [mdc-fields-reference.md](./mdc-fields-reference.md) - MDC field standards
