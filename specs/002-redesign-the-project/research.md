# Research: Enhanced Logging System

**Feature**: 002-redesign-the-project | **Date**: 2025-10-15

## Overview

This document consolidates research findings for implementing an enhanced logging system for the Papertrace medical literature platform. The research focuses on integrating structured logging with trace context propagation, sensitive data sanitization, and dynamic configuration within the constraints of the existing Spring Boot microservices architecture.

---

## 1. Logback Configuration Patterns for Microservices

### Decision: Enhanced Pattern Layout with MDC Integration

**Chosen Approach**: Use Logback's pattern layout with MDC (Mapped Diagnostic Context) fields for trace context, combined with async appenders for performance.

**Rationale**:
- **Compatibility**: Logback is already the default Spring Boot logging implementation
- **Performance**: Async appenders prevent logging from blocking application threads
- **Simplicity**: Pattern layout is simpler than full JSON structured logging (deferred per YAGNI)
- **Integration**: MDC seamlessly integrates with SkyWalking trace context

**Configuration Pattern**:
```xml
<configuration>
    <appender name="ASYNC_CONSOLE" class="ch.qos.logback.classic.AsyncAppender">
        <queueSize>512</queueSize>
        <discardingThreshold>0</discardingThreshold>
        <appender-ref ref="CONSOLE"/>
    </appender>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{ISO8601} [%X{traceId}] [%X{correlationId}] [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="ASYNC_CONSOLE"/>
    </root>
</configuration>
```

**Alternatives Considered**:
- **JSON structured logging (Logstash encoder)**: Deferred until proven necessary. Pattern layout is sufficient for current ELK stack integration.
- **Custom appenders**: Over-engineering. Standard appenders meet requirements.
- **Separate log files per level**: Increases complexity without clear benefit. Log aggregation tools handle filtering.

**Best Practices Applied**:
- Async appenders with non-discarding queue to prevent log loss
- ISO-8601 timestamps for consistent parsing
- Thread name for concurrency debugging
- Abbreviated logger names to reduce verbosity

---

## 2. SLF4J MDC Integration with Apache SkyWalking

### Decision: Use SkyWalking's TraceContext API + Spring Filters

**Chosen Approach**: Leverage SkyWalking's `TraceContext.traceId()` API and populate SLF4J MDC in servlet filters and Feign interceptors.

**Rationale**:
- **Automatic trace ID generation**: SkyWalking already handles distributed trace ID creation and propagation
- **Zero instrumentation in business logic**: Filters/interceptors operate at adapter layer boundaries
- **Standard integration**: SkyWalking is already integrated in the project (per assumptions)
- **Span correlation**: SkyWalking provides parent-child span relationships automatically

**Implementation Pattern**:
```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceContextFilter implements Filter {
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) {
        try {
            String traceId = TraceContext.traceId();
            String correlationId = request.getHeader("X-Correlation-ID")
                                   ?? UUID.randomUUID().toString();

            MDC.put("traceId", traceId);
            MDC.put("correlationId", correlationId);

            chain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}
```

**Alternatives Considered**:
- **Spring Cloud Sleuth**: Deprecated in favor of Micrometer Tracing. Avoided to prevent migration churn.
- **Custom trace ID generation**: Reinventing the wheel. SkyWalking already provides robust tracing.
- **OpenTelemetry**: Future-proof but requires full migration. Defer until SkyWalking proves insufficient.

**Best Practices Applied**:
- MDC cleanup in `finally` block to prevent thread pool pollution
- Support for external correlation IDs (idempotency keys, batch IDs)
- Filter order ensures trace context available before any logging
- Feign interceptors for outbound request trace propagation

**Cross-Thread Propagation**: Use `TaskDecorator` for Spring async tasks and `ThreadPoolTaskExecutor` wrapper to copy MDC to child threads.

---

## 3. Sensitive Data Sanitization Strategies

### Decision: Field-Based Sanitization with Regex Patterns

**Chosen Approach**: Implement field name and regex pattern matching to automatically sanitize sensitive data before logging.

**Rationale**:
- **Defense in depth**: Cannot rely on developers remembering to sanitize manually
- **Performance**: Regex matching on log messages is fast enough for <50ms p95 requirement
- **Flexibility**: Easy to add new patterns via configuration without code changes
- **Compliance**: Prevents PII/credentials exposure required for audit compliance

**Sanitization Rules**:
```java
public interface SanitizationRule {
    String MASKED = "***REDACTED***";

    // Field name patterns (case-insensitive)
    Set<String> SENSITIVE_FIELDS = Set.of(
        "password", "passwd", "pwd",
        "token", "apiKey", "api_key", "secret",
        "authorization", "auth",
        "ssn", "social_security_number",
        "credit_card", "creditCard", "card_number"
    );

    // Regex patterns for values
    Pattern EMAIL_PATTERN = Pattern.compile("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b");
    Pattern PHONE_PATTERN = Pattern.compile("\\b\\d{3}[-.]?\\d{3}[-.]?\\d{4}\\b");
    Pattern CREDIT_CARD_PATTERN = Pattern.compile("\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b");
}

public class LogSanitizer {
    public String sanitize(String message) {
        String sanitized = message;

        // Sanitize by field name (JSON-like logs)
        for (String field : SENSITIVE_FIELDS) {
            sanitized = sanitized.replaceAll(
                "\"" + field + "\"\\s*:\\s*\"[^\"]+\"",
                "\"" + field + "\":\"" + MASKED + "\""
            );
        }

        // Sanitize by pattern
        sanitized = EMAIL_PATTERN.matcher(sanitized).replaceAll(MASKED);
        sanitized = CREDIT_CARD_PATTERN.matcher(sanitized).replaceAll(MASKED);

        return sanitized;
    }
}
```

**Alternatives Considered**:
- **Annotation-based sanitization**: Requires developer discipline. Too error-prone.
- **Custom Logback encoder**: More foolproof but harder to test and maintain. Keep sanitization as pure functions.
- **No sanitization**: Unacceptable risk of PII/credential leakage.

**Best Practices Applied**:
- Sanitization applied at log entry point (aspect or utility method)
- Configurable patterns via external config (Nacos)
- Unit tested with known sensitive data patterns
- Regular expression review during security audits

**Performance Consideration**: Sanitization adds ~1-5ms per log statement. Acceptable given <50ms p95 constraint and infrequent INFO+ logs.

---

## 4. Dynamic Log Level Configuration with Nacos

### Decision: Spring Cloud Nacos Config with @RefreshScope

**Chosen Approach**: Store log level configuration in Nacos and use Spring's `LoggingSystem` API to update levels dynamically.

**Rationale**:
- **Existing infrastructure**: Nacos already used for centralized configuration
- **No restart required**: Configuration refresh propagates within 60 seconds (meets SC-007)
- **Audit trail**: Nacos config history tracks who changed log levels when
- **Granular control**: Per-package/class log level configuration

**Configuration Structure**:
```yaml
# Nacos config: logging-common.yml (shared across services)
logging:
  level:
    root: INFO
    com.papertrace: DEBUG
    com.papertrace.domain: INFO  # Keep domain logs minimal
    org.springframework.web: WARN
    org.mybatis: WARN
```

**Implementation Pattern**:
```java
@Configuration
public class DynamicLoggingConfiguration {
    @Autowired
    private LoggingSystem loggingSystem;

    @NacosConfigListener(dataId = "logging-common.yml", groupId = "DEFAULT_GROUP")
    public void onConfigChange(String config) {
        // Parse YAML config
        Map<String, String> levels = parseLogLevels(config);

        // Apply to logging system
        levels.forEach((logger, level) -> {
            loggingSystem.setLogLevel(logger, LogLevel.valueOf(level));
        });

        log.info("Log levels updated dynamically: {}", levels);
    }
}
```

**Alternatives Considered**:
- **Spring Boot Actuator /loggers endpoint**: Requires manual API calls. Less scalable than centralized config.
- **Environment variables**: Requires container restart. Defeats purpose of dynamic configuration.
- **JMX**: Complex setup, poor developer experience.

**Best Practices Applied**:
- Separate common config from service-specific overrides
- Log level changes are audited (Nacos change history)
- Automatic revert policies (monitor for performance degradation)
- Default to INFO in production, DEBUG in staging

---

## 5. Lombok @XSlf4j vs Alternatives

### Decision: Use Lombok @Slf4j (NOT @XSlf4j) + Explicit Sanitization

**Chosen Approach**: Standard Lombok `@Slf4j` annotation with explicit sanitization utility calls when logging potentially sensitive data.

**Rationale**:
- **Standard approach**: `@Slf4j` is widely used and well-supported
- **No magic**: Explicit sanitization calls make security visible in code reviews
- **Compatibility**: Works with all SLF4J implementations (Logback, Log4j2)
- **Simplicity**: `@XSlf4j` (extended Lombok logger) is non-standard and adds complexity

**Correction**: `@XSlf4j` is **NOT** a real Lombok annotation. The feature spec mentioned it in error. Standard `@Slf4j` is the correct choice.

**Usage Pattern**:
```java
@Slf4j
@Service
public class UserService {
    @Autowired
    private LogSanitizer sanitizer;

    public void createUser(User user) {
        log.info("Creating user: {}", sanitizer.sanitize(user.toString()));
        // ... business logic
    }

    public void authenticateUser(String username, String password) {
        // NEVER log passwords directly
        log.debug("Authenticating user: {}", username);
        // ... authentication logic
    }
}
```

**Alternatives Considered**:
- **@CommonsLog, @Log4j2**: Less common in Spring Boot ecosystem. Stick with SLF4J.
- **Manual logger declaration**: Verbose boilerplate. Lombok reduces noise.
- **No annotation**: Acceptable in domain layer (pure Java). Use Lombok elsewhere.

**Best Practices Applied**:
- Domain layer: Plain `Logger` declaration (no Lombok to keep pure Java)
- All other layers: `@Slf4j` for convenience
- Always use parameterized logging: `log.info("Message {}", param)` NOT `log.info("Message " + param)`
- Sanitize before logging user input or external data

---

## 6. Log Level Usage Guidelines

### Decision: Semantic Log Levels with Clear Criteria

**Defined Semantics**:

| Level | When to Use | Examples |
|-------|-------------|----------|
| **ERROR** | System failures requiring immediate attention. Operation cannot continue. | Database connection failure, external API unreachable after retries, unhandled exceptions, data corruption detected |
| **WARN** | Recoverable issues or degraded functionality. Operation continues with fallback. | Retry triggered, cache miss forcing DB query, deprecated API usage, configuration inconsistency |
| **INFO** | Key business events and state changes. Normal production logging. | Application startup/shutdown, batch processing start/complete, authentication success, data ingestion summary |
| **DEBUG** | Detailed processing flow for troubleshooting. Enabled temporarily in production. | Method entry/exit, decision branches, variable states at checkpoints, external API request/response details |
| **TRACE** | Fine-grained diagnostics. Loop iterations, low-level library calls. | Rare in application code. Mostly for framework/library debugging. |

**Rationale**:
- Clear criteria reduce inconsistent logging
- INFO level provides operational visibility without noise
- DEBUG available for troubleshooting without redeployment
- ERROR indicates actionable alerts (no false positives)

**Anti-Patterns to Avoid**:
- ❌ Logging stack traces at INFO level (use ERROR)
- ❌ Logging successful operations at DEBUG (use INFO for key events)
- ❌ Logging "entering method X" at INFO (use DEBUG)
- ❌ Logging inside tight loops at INFO/DEBUG (use aggregation or TRACE)

---

## 7. Asynchronous Boundaries and Trace Context

### Decision: ThreadPoolTaskExecutor Wrapper with MDC Propagation

**Chosen Approach**: Wrap all thread pools and async operations with `TaskDecorator` that copies MDC context.

**Rationale**:
- **Async processing common**: Batch jobs, message consumers, scheduled tasks all use thread pools
- **MDC thread-local**: Without propagation, child threads lose trace context
- **Spring Boot integration**: `TaskDecorator` is standard Spring mechanism

**Implementation Pattern**:
```java
@Configuration
public class AsyncConfiguration {
    @Bean
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setTaskDecorator(new MdcTaskDecorator());
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

**Alternatives Considered**:
- **Manual MDC copy in every async method**: Error-prone, violates DRY
- **Aspect-based propagation**: More complex than decorator pattern
- **No propagation**: Unacceptable loss of traceability

**Best Practices Applied**:
- Apply decorator to ALL thread pools (executor services, scheduled tasks, message listeners)
- RocketMQ message consumers: Extract trace ID from message headers, populate MDC
- Use `@Async` with configured executor (not default ForkJoinPool)

---

## 8. Batch Processing Log Aggregation

### Decision: Summary Logging for Batch Operations

**Chosen Approach**: Log batch start/end at INFO, individual item failures at WARN/ERROR, detailed progress at DEBUG.

**Rationale**:
- **Prevent log flooding**: Logging 10,000 items individually overwhelms logs
- **Business visibility**: Operations team cares about success/failure rates, not individual items
- **Troubleshooting support**: DEBUG logs available when investigating specific failures

**Pattern**:
```java
@Slf4j
public class BatchProcessor {
    public void processBatch(List<Item> items) {
        String batchId = UUID.randomUUID().toString();
        MDC.put("batchId", batchId);

        log.info("Batch processing started: batchId={}, itemCount={}", batchId, items.size());

        int successCount = 0;
        int errorCount = 0;

        for (Item item : items) {
            try {
                processItem(item);
                successCount++;
                log.debug("Item processed successfully: {}", item.getId());
            } catch (Exception e) {
                errorCount++;
                log.error("Item processing failed: itemId={}, error={}", item.getId(), e.getMessage(), e);
            }
        }

        log.info("Batch processing completed: batchId={}, success={}, errors={}",
                 batchId, successCount, errorCount);
    }
}
```

**Alternatives Considered**:
- **Log every item at INFO**: Unacceptable log volume (violates SC-004)
- **No item-level logging**: Insufficient for troubleshooting individual failures
- **Separate error log file**: Adds complexity without clear benefit

**Best Practices Applied**:
- Batch ID in MDC for filtering all related logs
- Summary statistics at INFO level (success/error counts)
- Individual failures at ERROR with full context
- Detailed progress at DEBUG (opt-in for troubleshooting)

---

## 9. External API Call Logging

### Decision: Structured API Call Logging with Request/Response Sanitization

**Chosen Approach**: Log all external API calls with standardized metadata (URL, method, status, duration) and sanitized request/response bodies.

**Rationale**:
- **Audit compliance**: FR-006 requires logging all external API interactions
- **Performance monitoring**: Response time critical for SLA tracking
- **Troubleshooting**: Request/response details essential for debugging integration failures

**Implementation Pattern**:
```java
@Slf4j
@Component
public class ApiCallLogger {
    @Autowired
    private LogSanitizer sanitizer;

    public void logApiCall(String service, String url, String method,
                           int statusCode, long durationMs,
                           String requestBody, String responseBody) {

        String sanitizedRequest = sanitizer.sanitize(requestBody);
        String sanitizedResponse = sanitizer.sanitize(responseBody);

        if (statusCode >= 500) {
            log.error("External API call failed: service={}, url={}, method={}, status={}, duration={}ms, request={}, response={}",
                      service, url, method, statusCode, durationMs, sanitizedRequest, sanitizedResponse);
        } else if (statusCode >= 400) {
            log.warn("External API call returned client error: service={}, url={}, method={}, status={}, duration={}ms",
                     service, url, method, statusCode, durationMs);
            log.debug("Request: {}, Response: {}", sanitizedRequest, sanitizedResponse);
        } else {
            log.info("External API call succeeded: service={}, url={}, method={}, status={}, duration={}ms",
                     service, url, method, statusCode, durationMs);
            log.debug("Request: {}, Response: {}", sanitizedRequest, sanitizedResponse);
        }
    }
}
```

**Integration Points**:
- **Feign clients**: Use `RequestInterceptor` and `ResponseInterceptor`
- **RestTemplate**: Use `ClientHttpRequestInterceptor`
- **WebClient**: Use `ExchangeFilterFunction`

**Alternatives Considered**:
- **Spring Cloud Sleuth automatic logging**: Provides trace IDs but lacks request/response details
- **No request/response logging**: Insufficient for troubleshooting integration issues
- **Always log full bodies**: Performance impact and potential PII exposure

**Best Practices Applied**:
- Always sanitize request/response bodies
- Log level based on status code (ERROR for 5xx, WARN for 4xx, INFO for 2xx/3xx)
- Include duration for performance analysis
- Full details at DEBUG, summaries at INFO/WARN/ERROR

---

## 10. Migration Strategy

### Decision: Phased Rollout by Module Type

**Chosen Approach**: Migrate in phases (infrastructure → adapters → application → codebase-wide cleanup) to minimize risk.

**Phased Plan**:

**Phase 1: Infrastructure Setup (Week 1)**
- Create `patra-spring-boot-starter-logging` with auto-configuration
- Implement sanitization utilities in `patra-common`
- Configure Nacos with default log levels
- Test in `patra-registry` as pilot service

**Phase 2: Critical Path Services (Week 2-3)**
- Migrate `patra-ingest` (high-volume batch processing)
- Migrate `patra-gateway-boot` (entry point for trace context)
- Validate trace context propagation end-to-end
- Monitor performance impact (<5% throughput requirement)

**Phase 3: Remaining Services (Week 4-5)**
- Migrate all other microservices following established pattern
- Update adapter layers first (controllers, jobs, Feign clients)
- Update application orchestrators second
- Keep domain layer changes minimal (plain SLF4J only)

**Phase 4: Codebase-wide Cleanup (Week 6)**
- Review and update legacy log statements to new standards
- Add missing logs per FR-006, FR-007, FR-009, FR-010
- Remove redundant/verbose logging identified during review
- Validate SC-004 (40% log volume reduction at INFO)

**Rationale**:
- Pilot service validates approach before full rollout
- Critical path services first ensures trace context works end-to-end
- Phased approach allows performance monitoring at each stage
- Minimizes impact if issues discovered

**Rollback Plan**: Each phase is independently deployable. If issues arise, revert to previous starter version and continue troubleshooting.

---

## Recommendations Summary

1. ✅ **Use Logback pattern layout with async appenders** (defer JSON structured logging)
2. ✅ **Integrate SkyWalking trace context via MDC** (servlet filters + Feign interceptors)
3. ✅ **Implement field-based sanitization with regex patterns** (defense in depth)
4. ✅ **Configure dynamic log levels via Nacos** (Spring Cloud Config refresh)
5. ✅ **Use Lombok @Slf4j** (NOT @XSlf4j which doesn't exist)
6. ✅ **Define semantic log level criteria** (ERROR=actionable, INFO=business events, DEBUG=troubleshooting)
7. ✅ **Propagate MDC across async boundaries** (TaskDecorator for thread pools)
8. ✅ **Aggregate batch processing logs** (summary at INFO, details at DEBUG)
9. ✅ **Standardize external API logging** (URL, method, status, duration, sanitized bodies)
10. ✅ **Phased migration strategy** (infrastructure → critical path → remaining → cleanup)

---

## Next Steps

Proceed to **Phase 1: Design & Contracts** to generate:
- `data-model.md` - Logging-related entities (log entry, trace context, sanitization rule)
- `contracts/` - Logging utility API specifications (if exposing via REST endpoints)
- `quickstart.md` - Developer guide for using new logging system

**Updated Agent Context**: Run `.specify/scripts/bash/update-agent-context.sh claude` to capture logging technology decisions for future context.
