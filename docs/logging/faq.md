# Logging System - Frequently Asked Questions (FAQ)

**Feature**: Enhanced Logging System | **Phase**: 6 - User Story 4 | **Date**: 2025-10-17

## Overview

This FAQ provides quick answers to common questions about the enhanced logging system in the Papertrace platform. Questions are organized by topic for easy navigation.

---

## Table of Contents

1. [General Questions](#general-questions)
2. [Logger Declaration](#logger-declaration)
3. [Log Levels](#log-levels)
4. [Trace Context](#trace-context)
5. [MDC (Mapped Diagnostic Context)](#mdc-mapped-diagnostic-context)
6. [Sanitization](#sanitization)
7. [Configuration](#configuration)
8. [Performance](#performance)
9. [Service Identifiers (FR-015)](#service-identifiers-fr-015)
10. [Best Practices](#best-practices)

---

## General Questions

### Q1: Do I need to use the new logging system?

**A:** Yes, all Papertrace microservices must migrate to the enhanced logging system. The system provides:
- Distributed trace context propagation
- Dynamic log level configuration
- Automatic sensitive data sanitization
- Consistent service/layer identification (FR-015)
- Performance optimization

**Timeline:**
- **Phase 3-6** (Current): Core services migrating (patra-registry, patra-ingest, patra-gateway)
- **Phase 7**: All services must complete migration

---

### Q2: What's the difference between the old and new logging systems?

**A:** Comparison:

| Feature | Old System | New System |
|---------|------------|------------|
| Trace Context | Manual propagation | Automatic via filters/interceptors |
| Log Levels | Static (application.yml) | Dynamic (Nacos, 60s refresh) |
| Sanitization | Manual | Automatic via `LogSanitizer` |
| Service IDs (FR-015) | Inconsistent | Canonical format `[service=X][layer=Y]` |
| Async MDC | Lost | Preserved via `MdcTaskDecorator` |
| Configuration | Per-service logback.xml | Centralized in starter |

---

### Q3: Where can I find documentation?

**A:** Documentation locations:

- **Quickstart**: `specs/001-logging-starter/quickstart.md`
- **Layer Examples**: `docs/logging/layer-specific-examples.md`
- **Common Patterns**: `docs/logging/common-patterns.md`
- **Troubleshooting**: `docs/logging/troubleshooting.md`
- **This FAQ**: `docs/logging/faq.md`
- **Specification**: `specs/001-logging-starter/spec.md`

---

## Logger Declaration

### Q4: Should I use `@Slf4j` or plain `Logger`?

**A:** It depends on the layer:

| Layer | Logger Declaration | Reason |
|-------|-------------------|--------|
| Adapter | `@Slf4j` | Lombok integration, Spring context |
| Application | `@Slf4j` | Lombok integration, Spring context |
| Domain | `private static final Logger log = LoggerFactory.getLogger(...)` | Pure Java, no framework dependencies |
| Infrastructure | `@Slf4j` | Lombok integration, Spring context |

**Example (Adapter/App/Infra):**
```java
@Service
@Slf4j
public class MyService {
    public void process() {
        log.info("Processing started");
    }
}
```

**Example (Domain):**
```java
public class MyAggregate {
    private static final Logger log = LoggerFactory.getLogger(MyAggregate.class);

    public void validate() {
        log.debug("Validating aggregate");
    }
}
```

---

### Q5: Why can't I use `@Slf4j` in domain layer?

**A:** Domain layer follows DDD principles and must remain pure Java with no framework dependencies:

**Reasons:**
1. **Domain purity**: No Lombok, no Spring annotations
2. **Portability**: Domain logic can be reused outside Spring context
3. **Testability**: Simpler unit testing without framework mocks
4. **Architectural boundaries**: Clear separation of concerns

**Enforcement:**
ArchUnit tests verify this constraint:
```java
@ArchTest
static final ArchRule domain_should_not_use_lombok =
    noClasses().that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAPackage("lombok..");
```

---

### Q6: What if I need multiple loggers in one class?

**A:** You can declare multiple loggers with different names:

```java
@Slf4j  // Creates default logger: log
public class MyService {

    // Additional logger for specific subsystem
    private static final Logger perfLog = LoggerFactory.getLogger("performance");
    private static final Logger auditLog = LoggerFactory.getLogger("audit");

    public void process() {
        log.info("Processing started");  // General application log
        perfLog.debug("Operation duration: {}ms", duration);  // Performance log
        auditLog.info("User action: {}", action);  // Audit trail
    }
}
```

---

## Log Levels

### Q7: When should I use each log level?

**A:** Log level guidelines:

| Level | When to Use | Examples |
|-------|-------------|----------|
| ERROR | System failures, exceptions requiring attention | Database connection lost, external API timeout |
| WARN | Recoverable issues, anomalies, retries | Validation failure, rate limit hit, retry attempt |
| INFO | Key business events, milestones | Request received, batch completed, user created |
| DEBUG | Detailed diagnostics, may be high-frequency | Method entry/exit, variable values, query details |
| TRACE | Very detailed diagnostics, verbose | Loop iterations, serialization, algorithm steps |

**Golden Rule**: INFO and above are NEVER sampled. DEBUG/TRACE may be sampled under high load.

---

### Q8: Can I change log levels without restarting the service?

**A:** Yes! Log levels are dynamically configurable via Nacos:

**Steps:**
1. Update Nacos configuration:
   - File: `logging-patra-{service}.yml` (service-specific) or `logging-common.yml` (all services)
   - Property: `logging.level.com.patra.myservice: DEBUG`
2. Wait up to 60 seconds for automatic refresh
3. Verify: `curl -X GET http://localhost:8080/actuator/loggers/com.patra.myservice`

**Force immediate refresh:**
```bash
curl -X POST http://localhost:8080/actuator/refresh
```

**See also**: `docs/logging/troubleshooting-log-levels.md`

---

### Q9: Why are my DEBUG logs not appearing?

**A:** Common causes:

1. **Log level is INFO/WARN**: Check Nacos configuration
2. **Logger name mismatch**: Ensure package name matches configuration
3. **Sampling active**: DEBUG logs may be sampled under high load (see log-sampling-guide.md)
4. **Configuration not refreshed**: Wait 60s or force refresh

**Diagnostic:**
```bash
# Check current log level
curl -X GET http://localhost:8080/actuator/loggers/com.patra.myservice

# Check for DEBUG logs in output
grep "DEBUG" logs/application.log | tail -20
```

---

## Trace Context

### Q10: What is trace context and why is it important?

**A:** Trace context enables request correlation across microservices:

**Components:**
- **Trace ID**: Unique ID for entire request chain (e.g., `abc123`)
- **Correlation ID**: Groups related operations (e.g., batch ID, user session)

**Benefits:**
1. **Cross-service tracing**: Search by trace ID to find all logs from one request
2. **Batch grouping**: Correlation ID groups logs from batch processing
3. **Root cause analysis**: See complete request flow across all services
4. **Performance profiling**: Measure latency across service boundaries

**Example Log:**
```
2025-10-17T10:23:45.123+08:00 INFO [traceId=abc123][correlationId=batch-001] Processing request
```

**Search by trace ID:**
```bash
grep "traceId=abc123" logs/application.log
```

---

### Q11: How is trace ID generated?

**A:** Trace ID generation varies by entry point:

| Entry Point | Trace ID Source |
|-------------|-----------------|
| HTTP Request | Extracted from `X-Trace-Id` header (gateway) or generated by `TraceContextFilter` |
| Scheduled Job | Generated via `TraceContextHolder.currentOrGenerate()` |
| MQ Listener | Extracted from message headers or generated |
| @Async Method | Inherited from parent thread via `MdcTaskDecorator` |

**Integration with SkyWalking:**
If SkyWalking agent is attached, trace ID uses SkyWalking's trace context. Otherwise, UUID is generated.

---

### Q12: How do I trace requests across multiple services?

**A:** Trace context propagates automatically via filters/interceptors:

**HTTP Calls:**
1. **Outbound**: `TraceContextInterceptor` (Feign) or `RestTemplateInterceptor` adds trace ID to headers
2. **Inbound**: `TraceContextFilter` extracts trace ID from headers

**Message Queue:**
1. **Producer**: Add trace ID to message headers
2. **Consumer**: `RocketMQMessageListenerDecorator` extracts trace ID from headers

**Example:**
```java
// Service A (patra-registry)
@Slf4j
@RestController
public class ProvController {
    @GetMapping("/prov/{id}")
    public Prov get(@PathVariable Long id) {
        log.info("Fetching prov: id={}", id);  // [traceId=abc123]
        return service.get(id);
    }
}

// Service B (patra-ingest) - called by Service A
@Slf4j
@FeignClient(name = "patra-registry")
public interface RegistryClient {
    @GetMapping("/api/v1/prov/{id}")
    Prov getProvenance(@PathVariable Long id);
}

// Service B logs will have SAME trace ID
log.info("Calling registry: id={}", id);  // [traceId=abc123]
```

**Search across services:**
```bash
grep "traceId=abc123" patra-*/logs/application.log
```

---

### Q13: What if trace ID is missing from my logs?

**A:** Diagnose and fix:

**Diagnosis:**
```bash
# Check if TraceContextFilter is registered
curl -X GET http://localhost:8080/actuator/beans | jq '.contexts[].beans | with_entries(select(.key | contains("TraceContextFilter")))'
```

**Common Causes & Solutions:**

1. **Missing starter dependency:**
```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-logging</artifactId>
</dependency>
```

2. **Scheduled job (no upstream request):**
```java
@Scheduled(cron = "0 0 * * * ?")
public void job() {
    DistributedTraceContext ctx = traceContextHolder.currentOrGenerate();
    traceContextHolder.populateMDC(ctx);
    try {
        log.info("Job started");
    } finally {
        traceContextHolder.clearMDC();
    }
}
```

**See also**: `docs/logging/troubleshooting.md` - Issue 1

---

## MDC (Mapped Diagnostic Context)

### Q14: What is MDC and when should I use it?

**A:** MDC (Mapped Diagnostic Context) adds custom fields to logs:

**Purpose:**
- Enrich logs with business context (userId, operation, batchId)
- Automatic inclusion in all log statements
- Thread-local storage (different threads have different MDC)

**When to use:**
- **Adapter layer**: Add operation, userId, request metadata
- **Batch processing**: Add batchId, correlationId
- **Async operations**: Propagate context to async threads

**Example:**
```java
@Slf4j
@RestController
public class MyController {

    @Autowired
    private LogContextEnricher enricher;

    @PostMapping("/api/resource")
    public ResponseEntity<Resource> create(@RequestBody CreateRequest req) {
        enricher.enrich("operation", "CREATE_RESOURCE");
        enricher.enrich("userId", req.getUserId());

        try {
            log.info("Creating resource");  // Automatically includes operation and userId
            // ... business logic

        } finally {
            enricher.clearEnriched();  // CRITICAL
        }
    }
}
```

**Log output:**
```
2025-10-17T10:23:45.123+08:00 INFO [traceId=abc][correlationId=xyz][operation=CREATE_RESOURCE][userId=123] Creating resource
```

---

### Q15: Why are my MDC fields lost in async tasks?

**A:** MDC is thread-local and doesn't automatically propagate to async threads.

**Solution:** Configure async executor with `MdcTaskDecorator`:

```java
@Configuration
@EnableAsync
public class AsyncConfiguration {

    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);

        // CRITICAL: Propagate MDC to async threads
        executor.setTaskDecorator(new MdcTaskDecorator());

        executor.initialize();
        return executor;
    }
}
```

**See also**: `docs/logging/troubleshooting.md` - Issue 4

---

### Q16: Do I need to clean up MDC?

**A:** YES! Always clean up MDC in `finally` block to prevent memory leaks:

```java
@Slf4j
public class SafeMdcUsage {

    public void process() {
        MDC.put("customField", "value");

        try {
            // ... business logic (may throw exception)

        } finally {
            // CRITICAL: Always clean up, even if exception thrown
            MDC.remove("customField");
        }
    }
}
```

**Why cleanup is critical:**
1. **Memory leak**: MDC backed by ThreadLocal, stale data accumulates
2. **Data corruption**: Next request on same thread sees previous request's MDC
3. **Thread pool pollution**: Worker threads retain old MDC values

**Recommended:** Use `LogContextEnricher.clearEnriched()` for automatic cleanup:
```java
enricher.enrich("field1", "value1");
enricher.enrich("field2", "value2");

try {
    // ... business logic

} finally {
    enricher.clearEnriched();  // Cleans up ALL enriched fields
}
```

---

## Sanitization

### Q17: When should I sanitize logs?

**A:** Always sanitize:

1. **User input**: Any data from HTTP request body/headers/params
2. **PII**: Email, phone, name, address, SSN, credit card
3. **Secrets**: Passwords, tokens, API keys, certificates
4. **External data**: API responses, database query results
5. **Exception messages**: May contain sensitive data

**Example:**
```java
@Slf4j
@RestController
public class UserController {

    @Autowired
    private LogSanitizer sanitizer;

    @PostMapping("/users")
    public ResponseEntity<User> create(@RequestBody CreateUserRequest req) {
        // ALWAYS sanitize user input
        log.info("Creating user: email={}, name={}",
                sanitizer.sanitize(req.getEmail()),
                sanitizer.sanitize(req.getName()));

        // NEVER log passwords
        // ❌ log.debug("Request: {}", req);  // Contains password!
        // ✅ log.debug("Request: {}", sanitizer.sanitizeObject(req));

        // ... business logic
    }
}
```

---

### Q18: What does `LogSanitizer` sanitize by default?

**A:** `DefaultLogSanitizer` includes patterns for:

| Data Type | Pattern | Replacement |
|-----------|---------|-------------|
| Password | `password=.*` | `password=***` |
| Token | `token=.*`, `Bearer .*` | `token=***`, `Bearer ***` |
| API Key | `apiKey=.*` | `apiKey=***` |
| Email | `[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}` | `***@***.***` |
| Phone | `\d{3}-\d{3}-\d{4}` | `***-***-****` |
| SSN | `\d{3}-\d{2}-\d{4}` | `***-**-****` |
| Credit Card | `\d{4}[- ]?\d{4}[- ]?\d{4}[- ]?\d{4}` | `****-****-****-****` |
| Authorization Header | `Authorization: .*` | `Authorization: ***` |

**Custom patterns:**
```java
@Configuration
public class SanitizationConfig {

    @Bean
    public LogSanitizer logSanitizer() {
        DefaultLogSanitizer sanitizer = new DefaultLogSanitizer();
        sanitizer.addPattern("userId", "uid=[a-zA-Z0-9]+", "uid=***");
        return sanitizer;
    }
}
```

---

### Q19: How do I sanitize JSON before logging?

**A:** Use `sanitizer.sanitizeJson()`:

```java
@Slf4j
public class ApiClient {

    @Autowired
    private LogSanitizer sanitizer;

    public Response callApi(Request request) {
        String requestJson = objectMapper.writeValueAsString(request);

        // Sanitize JSON string
        log.debug("Request body: {}", sanitizer.sanitizeJson(requestJson));

        // ... API call

        String responseJson = response.getBody();
        log.debug("Response body: {}", sanitizer.sanitizeJson(responseJson));
    }
}
```

**For objects:**
```java
// Sanitize entire object (DTO/entity)
log.debug("User: {}", sanitizer.sanitizeObject(user));
```

---

## Configuration

### Q20: Where do I configure log levels?

**A:** Two places:

**1. Default (application.yml) - Static:**
```yaml
# In patra-{service}-boot/src/main/resources/application.yml
logging:
  level:
    root: INFO
    com.patra: DEBUG
```

**2. Nacos (Dynamic) - Overrides application.yml:**
```yaml
# Nacos: logging-common.yml (all services)
logging:
  level:
    root: INFO
    com.patra: INFO

# Nacos: logging-patra-registry.yml (service-specific)
logging:
  level:
    com.patra.registry: DEBUG
    com.patra.registry.adapter: TRACE
```

**Priority:** Nacos > application.yml

**Refresh:** Nacos changes apply within 60 seconds (automatic)

---

### Q21: Do I need to create logback-spring.xml for my service?

**A:** Usually NO. The logging starter provides default configuration.

**Use default (recommended):**
- No `logback-spring.xml` in your service
- Starter auto-configures with trace context, async appenders, rolling policy

**Create custom only if:**
- Need service-specific appenders (e.g., separate audit log file)
- Need different rolling policy
- Need custom log patterns for specific loggers

**Extend default:**
```xml
<!-- In patra-{service}-boot/src/main/resources/logback-spring.xml -->
<configuration>
    <!-- Include starter's default -->
    <include resource="logback-spring-base.xml" />

    <!-- Add service-specific customization -->
    <logger name="com.patra.myservice.audit" level="INFO">
        <appender-ref ref="AUDIT_FILE" />
    </logger>

    <appender name="AUDIT_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/audit.log</file>
        <!-- ... rolling policy -->
    </appender>
</configuration>
```

---

### Q22: How do I configure service and layer identifiers (FR-015)?

**A:** Configure in `application.yml`:

```yaml
# In patra-{service}-boot/src/main/resources/application.yml
spring:
  application:
    name: patra-registry  # REQUIRED for [service=patra-registry]

papertrace:
  logging:
    layer: adapter  # Optional: auto-detected if not specified
```

**Valid service names:**
- Format: `^[a-z][a-z0-9-]*$` (lowercase, hyphens, starts with letter)
- Examples: `patra-registry`, `patra-ingest`, `patra-gateway`

**Valid layer names:**
- `adapter` - Controllers, Jobs, Message Listeners
- `app` - Orchestrators, Use Case coordinators
- `domain` - Pure Java business logic
- `infra` - Repositories, External API clients

**Expected log output:**
```
2025-10-17T10:23:45.123+08:00 INFO [service=patra-registry][layer=adapter] c.p.r.a.ProvenanceController : Fetching provenance
```

**See also**: `docs/logging/troubleshooting.md` - Issues 13-15

---

## Performance

### Q23: Will logging impact my application's performance?

**A:** Minimal impact (<5%) when configured correctly:

**Starter uses:**
1. **Async appenders**: Logging happens on separate threads (non-blocking)
2. **Parameterized logging**: String formatting only if log level enabled
3. **Log sampling**: High-frequency DEBUG/TRACE logs sampled under load
4. **Efficient patterns**: Optimized regex for sanitization

**Best practices:**
```java
// ❌ WRONG: String concatenation (always executed)
log.debug("User: " + user.getName() + " age: " + user.getAge());

// ✅ CORRECT: Parameterized (deferred evaluation)
log.debug("User: {} age: {}", user.getName(), user.getAge());

// ✅ CORRECT: Guard expensive operations
if (log.isDebugEnabled()) {
    log.debug("Object: {}", expensiveSerialize(obj));
}
```

**See also**: `docs/logging/log-sampling-guide.md`

---

### Q24: What is log sampling and when is it active?

**A:** Log sampling prevents DEBUG/TRACE log flooding:

**How it works:**
- **Threshold**: Default 100 logs/second per logger
- **Sampling rate**: Default 10 (keep 1 out of 10 logs when threshold exceeded)
- **Scope**: Only DEBUG and TRACE levels (INFO/WARN/ERROR never sampled)

**Example:**
- Normal load (50 DEBUG logs/sec): All logs written
- High load (500 DEBUG logs/sec): Only ~50 logs written (10% sampled)
- All INFO/WARN/ERROR logs: Always written (never sampled)

**Configuration:**
```xml
<!-- In logback-spring.xml -->
<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <filter class="com.patra.starter.logging.filter.SamplingFilter">
        <thresholdLogsPerSecond>100</thresholdLogsPerSecond>
        <samplingRate>10</samplingRate>
    </filter>
    <encoder>...</encoder>
</appender>
```

**See also**: `docs/logging/log-sampling-guide.md`

---

## Service Identifiers (FR-015)

### Q25: Why do I need service and layer identifiers?

**A:** Service/layer identifiers enable:

1. **Service boundary identification**: Distinguish logs from different microservices
2. **Layer-specific filtering**: Search logs from specific architectural layer
3. **Cross-service tracing**: Understand request flow across services
4. **Performance analysis**: Identify bottlenecks by layer
5. **Architecture compliance**: Verify Hexagonal Architecture boundaries

**Example use cases:**

**Search logs from specific service:**
```bash
grep "\[service=patra-registry\]" logs/application.log
```

**Search logs from adapter layer (all services):**
```bash
grep "\[layer=adapter\]" */logs/application.log
```

**Trace request across services:**
```bash
grep "traceId=abc123" */logs/application.log | grep -E "\[service=.*\]\[layer=.*\]"
```

**Verify architecture:**
```bash
# Verify adapter layer doesn't call infra directly (should go through app)
grep "\[layer=adapter\]" logs/application.log | grep -v "\[layer=app\]"
```

---

### Q26: What is the canonical format for service identifiers (FR-015)?

**A:** Canonical format: `[service=<service-name>][layer=<layer-name>]`

**Components:**

**Service name:**
- Pattern: `^[a-z][a-z0-9-]*$`
- Rules: lowercase, alphanumeric with hyphens, starts with letter
- Examples: `patra-registry`, `patra-ingest`, `patra-gateway`

**Layer name:**
- Valid values: `adapter`, `app`, `domain`, `infra`
- Maps to Hexagonal Architecture layers

**Example log output:**
```
2025-10-17T10:23:45.123+08:00 INFO [patra-registry-boot] [http-nio-8080-exec-1] [traceId=abc123][correlationId=xyz789][service=patra-registry][layer=adapter] c.p.r.a.ProvenanceController : Fetching provenance: source=PubMed
```

**Anti-patterns (non-compliant):**
```
❌ [service=PatraRegistry][layer=controller]  # Wrong: camelCase, non-standard layer
❌ [service=registry][layer=web]              # Wrong: missing prefix, non-standard layer
❌ [service=patra_registry][layer=adapter]    # Wrong: underscores instead of hyphens
```

---

## Best Practices

### Q27: What are the top 5 logging best practices?

**A:** Essential practices:

1. **Use correct log level**: INFO for business events, DEBUG for details, WARN for recoverable issues, ERROR for failures
2. **Always sanitize sensitive data**: Use `LogSanitizer` for user input, PII, secrets
3. **Configure service identifiers (FR-015)**: Set `spring.application.name` and `papertrace.logging.layer`
4. **Clean up MDC**: Always use try-finally to prevent memory leaks
5. **Use parameterized logging**: Never concatenate strings in log statements

**Example:**
```java
@Slf4j
@RestController
public class BestPracticeController {

    @Autowired
    private LogSanitizer sanitizer;

    @Autowired
    private LogContextEnricher enricher;

    @PostMapping("/api/resource")
    public ResponseEntity<Resource> create(@RequestBody CreateRequest req) {
        // 1. Configure service identifier (FR-015) - in application.yml
        // spring.application.name: patra-myservice

        // 2. Clean up MDC - try-finally
        enricher.enrich("operation", "CREATE");

        try {
            // 3. Correct log level - INFO for business event
            // 4. Sanitize sensitive data
            log.info("Creating resource: name={}",
                    sanitizer.sanitize(req.getName()));

            // 5. Parameterized logging (not string concatenation)
            log.debug("Request details: id={}, type={}",
                    req.getId(), req.getType());

            // ... business logic

        } finally {
            enricher.clearEnriched();  // Always clean up
        }
    }
}
```

---

### Q28: Should I log every method entry/exit?

**A:** NO! Avoid excessive logging:

**Don't log:**
- ❌ Simple getters/setters
- ❌ Every method in hot paths (high-frequency)
- ❌ Obvious operations (e.g., "Starting to process", "Ending process")
- ❌ Trivial calculations or pure functions

**Do log:**
- ✅ Orchestrator entry/exit (application layer)
- ✅ External API calls (infrastructure layer)
- ✅ Database operations (infrastructure layer)
- ✅ State transitions (domain layer)
- ✅ Error paths and exception handling

**Example:**
```java
@Slf4j
@Service
public class ArticleService {

    // ❌ WRONG: Too verbose
    public Article getById(Long id) {
        log.debug("Entering getById");  // Unnecessary
        log.debug("Getting article: id={}", id);  // OK
        Article article = repository.findById(id);
        log.debug("Article found: id={}", id);  // Unnecessary
        log.debug("Exiting getById");  // Unnecessary
        return article;
    }

    // ✅ CORRECT: Concise and informative
    public Article getById(Long id) {
        log.debug("Fetching article: id={}", id);
        Article article = repository.findById(id);
        if (article == null) {
            log.warn("Article not found: id={}", id);  // Important
        }
        return article;
    }
}
```

---

### Q29: How do I test my logging implementation?

**A:** Testing strategies:

**1. Unit Tests - Verify log statements:**
```java
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class LoggingTest {

    @Test
    void shouldLogWithTraceContext(CapturedOutput output) {
        // Test that trace ID is included
        myService.process();

        assertThat(output.toString()).contains("[traceId=");
        assertThat(output.toString()).contains("[correlationId=");
        assertThat(output.toString()).contains("[service=patra-myservice]");
        assertThat(output.toString()).contains("[layer=app]");
    }
}
```

**2. Integration Tests - Verify trace propagation:**
```java
@SpringBootTest
@AutoConfigureMockMvc
class TraceContextIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldPropagateTraceContext() throws Exception {
        String testTraceId = "test-" + UUID.randomUUID();

        mockMvc.perform(get("/api/resource/{id}", 123)
                .header("X-Trace-Id", testTraceId))
                .andExpect(status().isOk());

        // Verify trace ID in MDC
        assertThat(MDC.get("traceId")).isEqualTo(testTraceId);
    }
}
```

**3. Manual Testing - Check log files:**
```bash
# Start application
mvn spring-boot:run

# Make request
curl -H "X-Trace-Id: test123" http://localhost:8080/api/resource/123

# Verify logs
grep "traceId=test123" logs/application.log
grep "\[service=patra-myservice\]\[layer=adapter\]" logs/application.log
```

---

### Q30: Where can I get help if I'm stuck?

**A:** Support resources:

**Documentation:**
1. **Quickstart**: `specs/001-logging-starter/quickstart.md`
2. **Troubleshooting**: `docs/logging/troubleshooting.md`
3. **This FAQ**: `docs/logging/faq.md`
4. **Examples**: `docs/logging/layer-specific-examples.md`
5. **Patterns**: `docs/logging/common-patterns.md`

**Live Support:**
1. **Slack**: #dev-logging channel
2. **Email**: logging-team@papertrace.com
3. **JIRA**: Create ticket in LOGGING project

**Code Examples:**
1. **Reference implementation**: `patra-registry` (pilot service)
2. **Tests**: `patra-spring-boot-starter-logging/src/test/java/`

**When asking for help, include:**
- Service name and version
- Log snippet showing the issue
- Configuration files (application.yml, logback-spring.xml)
- Steps already tried
- Expected vs. actual behavior

---

## Summary

### Quick Reference Card

| Question | Answer |
|----------|--------|
| Logger in domain layer? | Plain `Logger` (NO `@Slf4j`) |
| Logger in other layers? | `@Slf4j` |
| Change log level? | Update Nacos, wait 60s |
| Trace ID missing? | Check starter dependency, generate for batch jobs |
| MDC in async? | Configure `MdcTaskDecorator` |
| Sanitize logs? | Use `LogSanitizer` for user input, PII, secrets |
| Service identifier (FR-015)? | Configure `spring.application.name` and `papertrace.logging.layer` |
| Log levels? | INFO for events, DEBUG for details, WARN for issues, ERROR for failures |
| Performance impact? | <5% with async appenders and parameterized logging |
| Get help? | #dev-logging Slack, `docs/logging/` documentation |

---

**Still have questions?** Contact the logging team via #dev-logging Slack channel or check additional documentation in `docs/logging/`.
