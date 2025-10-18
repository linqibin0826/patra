# Logging System Troubleshooting Guide

**Feature**: Enhanced Logging System | **Phase**: 6 - User Story 4 | **Date**: 2025-10-17

## Overview

This guide provides systematic troubleshooting procedures for common logging issues in the Papertrace platform. Each issue includes symptoms, root causes, diagnostic steps, and solutions.

---

## Table of Contents

1. [Trace Context Issues](#trace-context-issues)
2. [MDC Issues](#mdc-issues)
3. [Log Level Issues](#log-level-issues)
4. [Sanitization Issues](#sanitization-issues)
5. [Performance Issues](#performance-issues)
6. [Configuration Issues](#configuration-issues)
7. [Service Identifier Issues (FR-015)](#service-identifier-issues-fr-015)

---

## Trace Context Issues

### Issue 1: Trace ID Not Appearing in Logs

**Symptoms:**
- Logs missing `[traceId]` field
- Cannot correlate requests across services
- Trace search returns no results

**Root Causes:**
1. `TraceContextFilter` not registered (missing starter dependency)
2. SkyWalking agent not attached
3. Request not going through servlet filter (e.g., scheduled job, MQ listener)
4. MDC cleared before logging

**Diagnostic Steps:**

```bash
# 1. Check if logging starter is in dependencies
grep -r "patra-spring-boot-starter-logging" pom.xml

# 2. Verify TraceContextFilter is registered
curl -X GET http://localhost:8080/actuator/beans | jq '.contexts[].beans | with_entries(select(.key | contains("TraceContextFilter")))'

# 3. Check if SkyWalking agent is active
curl -X GET http://localhost:8080/actuator/health | jq '.components.skywalking'

# 4. Check logs for filter execution
grep "TraceContextFilter" logs/application.log
```

**Solutions:**

**Solution 1**: Add logging starter dependency

```xml
<!-- In patra-{service}-boot/pom.xml -->
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-logging</artifactId>
</dependency>
```

**Solution 2**: For scheduled jobs, manually generate trace context

```java
@Slf4j
@Component
public class ScheduledJob {

    @Autowired
    private TraceContextHolder traceContextHolder;

    @Scheduled(cron = "0 0 * * * ?")
    public void scheduledTask() {
        // Generate trace context for scheduled job
        DistributedTraceContext context = traceContextHolder.currentOrGenerate();
        traceContextHolder.populateMDC(context);

        try {
            log.info("Scheduled task started");
            // ... task logic
            log.info("Scheduled task completed");

        } finally {
            // CRITICAL: Clean up MDC
            traceContextHolder.clearMDC();
        }
    }
}
```

**Solution 3**: For message listeners, extract trace context from headers

```java
@Slf4j
@Component
@RocketMQMessageListener(topic = "events", consumerGroup = "consumer")
public class EventListener implements RocketMQListener<Event> {

    @Autowired
    private TraceContextHolder traceContextHolder;

    @Override
    public void onMessage(Event event) {
        // Extract trace context from message
        String traceId = event.getTraceId();
        String correlationId = event.getCorrelationId();

        DistributedTraceContext context = DistributedTraceContext.withCorrelation(
                traceId != null ? traceId : UUID.randomUUID().toString(),
                correlationId
        );
        traceContextHolder.populateMDC(context);

        try {
            log.info("Processing event");
            // ... processing logic

        } finally {
            traceContextHolder.clearMDC();
        }
    }
}
```

---

### Issue 2: Trace ID Changes Mid-Request

**Symptoms:**
- Different trace IDs in same request chain
- Cannot correlate logs across layers
- Trace search returns incomplete results

**Root Causes:**
1. MDC cleared too early
2. Async operation without `MdcTaskDecorator`
3. New trace context generated unnecessarily

**Diagnostic Steps:**

```bash
# Search for trace ID changes in a single request
grep -A 5 -B 5 "traceId=abc123" logs/application.log | grep -E "traceId=(?!abc123)"

# Check if MdcTaskDecorator is configured
grep -r "MdcTaskDecorator" src/main/java/
```

**Solutions:**

**Solution 1**: Configure async executor with `MdcTaskDecorator`

```java
@Configuration
@EnableAsync
public class AsyncConfiguration {

    @Bean(name = "taskExecutor")
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);

        // CRITICAL: Add MdcTaskDecorator for MDC propagation
        executor.setTaskDecorator(new MdcTaskDecorator());

        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
```

**Solution 2**: Don't clear MDC prematurely

```java
@Slf4j
@RestController
public class MyController {

    @GetMapping("/api/resource")
    public ResponseEntity<Resource> get() {
        // ❌ WRONG: Don't clear MDC here
        // traceContextHolder.clearMDC();

        try {
            // ... business logic

        } finally {
            // ✅ CORRECT: Clean up only custom MDC fields
            enricher.clearEnriched();
            // DON'T clear traceId/correlationId - filter will handle it
        }
    }
}
```

---

### Issue 3: Correlation ID Lost in Async Operations

**Symptoms:**
- Async task logs missing correlation ID
- Cannot group related async operations
- Batch processing loses batch ID

**Root Causes:**
1. Async executor not configured with `MdcTaskDecorator`
2. Correlation ID not propagated to async context
3. MDC cleared before async task starts

**Diagnostic Steps:**

```bash
# Check if async logs have correlation ID
grep "@Async" src/main/java/ -A 20 | grep -E "log\.(info|debug|warn|error)"

# Search for correlation ID in async logs
grep "correlationId=" logs/application.log | grep "async-"
```

**Solutions:**

**Solution 1**: Propagate correlation ID explicitly for complex async scenarios

```java
@Slf4j
@Service
public class AsyncService {

    @Async("taskExecutor")
    public CompletableFuture<Result> processAsync(Request request) {
        // MDC automatically propagated by MdcTaskDecorator
        String correlationId = MDC.get("correlationId");

        log.info("Async processing started: correlationId={}", correlationId);

        try {
            Result result = performWork(request);
            log.info("Async processing completed: correlationId={}", correlationId);
            return CompletableFuture.completedFuture(result);

        } catch (Exception e) {
            log.error("Async processing failed: correlationId={}", correlationId, e);
            throw e;
        }
    }
}
```

---

## MDC Issues

### Issue 4: MDC Fields Lost in Async Tasks

**Symptoms:**
- Custom MDC fields (userId, operation, etc.) missing in async logs
- Inconsistent MDC state across threads
- Memory leaks (MDC not cleaned up)

**Root Causes:**
1. Thread pool not configured with `MdcTaskDecorator`
2. MDC not cleaned up in finally block
3. Using raw `@Async` without custom executor

**Diagnostic Steps:**

```bash
# Check MDC configuration
grep -r "setTaskDecorator" src/main/java/

# Check for MDC cleanup
grep -r "MDC.remove\|clearEnriched\|clearMDC" src/main/java/
```

**Solutions:**

**Solution 1**: Always use `MdcTaskDecorator` for thread pools

```java
@Bean(name = "batchExecutor")
public ThreadPoolTaskExecutor batchExecutor() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setTaskDecorator(new MdcTaskDecorator());  // ← CRITICAL
    executor.initialize();
    return executor;
}
```

**Solution 2**: Always clean up MDC in finally blocks

```java
@Slf4j
@RestController
public class MyController {

    @PostMapping("/api/resource")
    public ResponseEntity<Resource> create(@RequestBody CreateRequest request) {
        enricher.enrich("operation", "CREATE_RESOURCE");
        enricher.enrich("userId", request.getUserId());

        try {
            // ... business logic

        } finally {
            // CRITICAL: Always clean up
            enricher.clearEnriched();
        }
    }
}
```

---

### Issue 5: MDC Memory Leak

**Symptoms:**
- Increasing memory usage over time
- OutOfMemoryError after prolonged operation
- MDC contains stale data from previous requests

**Root Causes:**
1. MDC not cleaned up in finally block
2. Exception thrown before MDC cleanup
3. Async task doesn't clean up MDC

**Diagnostic Steps:**

```bash
# Check for missing finally blocks
grep -B 5 "MDC.put\|enricher.enrich" src/main/java/ | grep -v "finally"

# Check memory usage trends
jstat -gc <pid> 1000 10
```

**Solutions:**

**Solution 1**: Always use try-finally for MDC cleanup

```java
@Slf4j
public class SafeMdcUsage {

    public void process() {
        MDC.put("customField", "value");

        try {
            // ... business logic that may throw exception

        } finally {
            // ALWAYS clean up, even if exception thrown
            MDC.remove("customField");
        }
    }
}
```

**Solution 2**: Use `LogContextEnricher` for automatic cleanup

```java
@Slf4j
@RestController
public class MyController {

    @Autowired
    private LogContextEnricher enricher;

    @PostMapping("/api/resource")
    public ResponseEntity<Resource> create(@RequestBody CreateRequest request) {
        // Enricher tracks fields for cleanup
        enricher.enrich("operation", "CREATE");
        enricher.enrich("userId", request.getUserId());

        try {
            // ... business logic

        } finally {
            // Cleans up ALL enriched fields
            enricher.clearEnriched();
        }
    }
}
```

---

## Log Level Issues

### Issue 6: Dynamic Log Level Not Taking Effect

**Symptoms:**
- DEBUG logs not appearing after Nacos update
- Log level change requires restart
- Inconsistent log levels across instances

**Root Causes:**
1. Nacos listener not registered
2. Configuration refresh delay (>60 seconds)
3. Logger name mismatch in configuration

**Diagnostic Steps:**

```bash
# Check Nacos configuration
curl -X GET "http://nacos-server:8848/nacos/v1/cs/configs?dataId=logging-patra-registry.yml&group=DEFAULT_GROUP"

# Check current log level
curl -X GET http://localhost:8080/actuator/loggers/com.patra.registry

# Check Nacos listener registration
grep "NacosContextRefresher" logs/application.log
```

**Solutions:**

**Solution 1**: Verify Nacos configuration format

```yaml
# Nacos: logging-patra-registry.yml
logging:
  level:
    root: INFO
    com.patra: DEBUG                    # Package-level
    com.patra.registry.adapter: TRACE   # More specific
```

**Solution 2**: Wait for refresh interval (default 60 seconds)

```bash
# Force configuration refresh via Actuator
curl -X POST http://localhost:8080/actuator/refresh
```

**Solution 3**: Verify logger name matches package

```java
// ❌ WRONG: Logger name doesn't match class package
@Slf4j  // Creates logger with class name
public class MyController {
    // Nacos: logging.level.com.patra.myapp: DEBUG
    // Actual logger: com.patra.myapp.adapter.MyController
}

// ✅ CORRECT: Configure at package level
// Nacos: logging.level.com.patra.myapp: DEBUG
```

---

### Issue 7: Excessive DEBUG Logs in Production

**Symptoms:**
- High log volume
- Storage filling up quickly
- Performance degradation
- Difficult to find important logs

**Root Causes:**
1. DEBUG level accidentally enabled in production
2. Per-class DEBUG override not removed
3. Log level inherited from parent logger

**Diagnostic Steps:**

```bash
# Check current log levels
curl -X GET http://localhost:8080/actuator/loggers | jq '.loggers | with_entries(select(.value.effectiveLevel == "DEBUG"))'

# Check log volume
ls -lh logs/application.log
tail -n 1000 logs/application.log | awk '{print $5}' | sort | uniq -c

# Find classes producing most DEBUG logs
grep "DEBUG" logs/application.log | awk '{print $6}' | sort | uniq -c | sort -nr | head -20
```

**Solutions:**

**Solution 1**: Revert to INFO level in Nacos

```yaml
# Nacos: logging-common.yml
logging:
  level:
    root: INFO
    com.patra: INFO  # Change from DEBUG
```

**Solution 2**: Add log sampling for high-frequency loggers

```xml
<!-- In logback-spring.xml -->
<logger name="com.patra.ingest.batch.ItemProcessor" level="DEBUG">
    <appender-ref ref="CONSOLE_WITH_SAMPLING" />
</logger>

<appender name="CONSOLE_WITH_SAMPLING" class="ch.qos.logback.core.ConsoleAppender">
    <filter class="com.patra.starter.logging.filter.SamplingFilter">
        <thresholdLogsPerSecond>100</thresholdLogsPerSecond>
        <samplingRate>10</samplingRate>
    </filter>
    <encoder>
        <pattern>${LOG_PATTERN}</pattern>
    </encoder>
</appender>
```

---

## Sanitization Issues

### Issue 8: Sensitive Data Appearing in Logs

**Symptoms:**
- Passwords, tokens, PII visible in logs
- Compliance violations
- Security audit failures

**Root Causes:**
1. Forgot to call `sanitizer.sanitize()` before logging
2. Logging entire object instead of sanitized version
3. Sensitive data in exception messages

**Diagnostic Steps:**

```bash
# Scan for potential sensitive data patterns
grep -iE "password|token|ssn|credit.*card|api.*key" logs/application.log

# Check for unsanitized logging
grep -r "log\.(info|debug|warn|error).*request\." src/main/java/ | grep -v "sanitize"
```

**Solutions:**

**Solution 1**: Always sanitize user input and external data

```java
@Slf4j
@RestController
public class UserController {

    @Autowired
    private LogSanitizer sanitizer;

    @PostMapping("/users")
    public ResponseEntity<User> create(@RequestBody CreateUserRequest request) {
        // ❌ WRONG: Logs entire object (may contain password)
        // log.info("Creating user: {}", request);

        // ✅ CORRECT: Sanitize before logging
        log.info("Creating user: email={}, name={}",
                sanitizer.sanitize(request.getEmail()),
                sanitizer.sanitize(request.getName()));

        // ✅ CORRECT: Sanitize entire object
        log.debug("Create request: {}", sanitizer.sanitizeObject(request));

        // ... business logic
    }
}
```

**Solution 2**: Configure `DefaultLogSanitizer` patterns

```java
@Configuration
public class SanitizationConfig {

    @Bean
    public LogSanitizer logSanitizer() {
        DefaultLogSanitizer sanitizer = new DefaultLogSanitizer();

        // Add custom patterns for your domain
        sanitizer.addPattern("userId", "\\buid=[a-zA-Z0-9]+", "uid=***");
        sanitizer.addPattern("apiKey", "apiKey=[a-zA-Z0-9]+", "apiKey=***");

        return sanitizer;
    }
}
```

---

### Issue 9: Over-Sanitization Hiding Useful Information

**Symptoms:**
- Logs lack context for debugging
- All values replaced with `***`
- Cannot distinguish between different sanitized values

**Root Causes:**
1. Too aggressive sanitization patterns
2. Sanitizing non-sensitive data
3. Using global sanitization for all logs

**Diagnostic Steps:**

```bash
# Check sanitization patterns
grep -r "addPattern\|sanitize" src/main/java/

# Count sanitized values
grep "\*\*\*" logs/application.log | wc -l
```

**Solutions:**

**Solution 1**: Selective sanitization

```java
@Slf4j
public class ApiClient {

    @Autowired
    private LogSanitizer sanitizer;

    public Response callApi(Request request) {
        // ✅ CORRECT: Sanitize only sensitive fields
        log.info("Calling API: url={}, userId={}",
                request.getUrl(),  // Not sensitive, don't sanitize
                sanitizer.sanitize(request.getUserId()));  // Sensitive, sanitize

        // ❌ WRONG: Over-sanitization loses context
        // log.info("Calling API: url={}, userId={}",
        //         sanitizer.sanitize(request.getUrl()),
        //         sanitizer.sanitize(request.getUserId()));
    }
}
```

**Solution 2**: Partial sanitization for identifiers

```java
public class SmartSanitizer {

    /**
     * Sanitize email while preserving domain for debugging.
     * Example: john.doe@example.com → j***@example.com
     */
    public String sanitizeEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }

        String[] parts = email.split("@");
        String username = parts[0];
        String domain = parts[1];

        String sanitizedUsername = username.charAt(0) + "***";
        return sanitizedUsername + "@" + domain;
    }
}
```

---

## Performance Issues

### Issue 10: High Logging Overhead

**Symptoms:**
- Application slowdown with DEBUG logs enabled
- High CPU usage from logging
- Increased latency (>5% impact)

**Root Causes:**
1. Synchronous appenders blocking threads
2. String concatenation in log statements
3. Excessive logging in hot paths
4. Large objects logged without sanitization

**Diagnostic Steps:**

```bash
# Profile logging overhead
jstack <pid> | grep -A 10 "Appender\|FileOutputStream"

# Check for string concatenation in logs
grep -r "log\.(info|debug|warn|error).*\+" src/main/java/

# Measure throughput with/without DEBUG
ab -n 10000 -c 100 http://localhost:8080/api/endpoint
```

**Solutions:**

**Solution 1**: Use async appenders (already configured in starter)

```xml
<!-- In logback-spring.xml -->
<appender name="ASYNC_FILE" class="ch.qos.logback.classic.AsyncAppender">
    <queueSize>512</queueSize>
    <discardingThreshold>0</discardingThreshold>
    <neverBlock>false</neverBlock>  <!-- Block on ERROR/WARN -->
    <appender-ref ref="FILE" />
</appender>
```

**Solution 2**: Use parameterized logging (FR-012)

```java
@Slf4j
public class PerformanceExample {

    public void logExample(User user) {
        // ❌ WRONG: String concatenation (always executed)
        log.debug("User: " + user.getName() + " age: " + user.getAge());

        // ✅ CORRECT: Parameterized logging (deferred evaluation)
        log.debug("User: {} age: {}", user.getName(), user.getAge());
    }
}
```

**Solution 3**: Guard expensive log statements

```java
@Slf4j
public class ExpensiveLogging {

    public void logExample(ComplexObject obj) {
        // ❌ WRONG: Expensive serialization always executed
        log.debug("Object: {}", expensiveToString(obj));

        // ✅ CORRECT: Guard with log level check
        if (log.isDebugEnabled()) {
            log.debug("Object: {}", expensiveToString(obj));
        }
    }
}
```

---

### Issue 11: Log File Growing Too Large

**Symptoms:**
- Single log file exceeds 1GB
- Disk space exhausted
- Log rotation not working

**Root Causes:**
1. Rolling policy not configured
2. Max file size too large
3. Old logs not archived or deleted

**Diagnostic Steps:**

```bash
# Check log file sizes
du -sh logs/*

# Check rolling policy configuration
grep -A 10 "RollingFileAppender" logback-spring.xml
```

**Solutions:**

**Solution 1**: Configure rolling policy (already in starter)

```xml
<!-- In logback-spring.xml -->
<appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>logs/application.log</file>

    <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
        <fileNamePattern>logs/application.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
        <maxFileSize>100MB</maxFileSize>
        <maxHistory>30</maxHistory>  <!-- Keep 30 days -->
        <totalSizeCap>10GB</totalSizeCap>  <!-- Max total size -->
    </rollingPolicy>

    <encoder>
        <pattern>${LOG_PATTERN}</pattern>
    </encoder>
</appender>
```

---

## Configuration Issues

### Issue 12: Logback Configuration Not Loaded

**Symptoms:**
- Default log format (not including trace context)
- Configuration changes not taking effect
- Multiple logback configs conflict

**Root Causes:**
1. `logback-spring.xml` in wrong location
2. Legacy `logback.xml` overriding `logback-spring.xml`
3. Starter not providing default configuration

**Diagnostic Steps:**

```bash
# Find all logback configs
find . -name "logback*.xml"

# Check if starter is loaded
grep "LoggingAutoConfiguration" logs/application.log

# Check Spring Boot's detected logback file
grep "Logback configuration" logs/application.log
```

**Solutions:**

**Solution 1**: Remove legacy logback.xml

```bash
# Delete or rename legacy config
cd patra-{service}-boot/src/main/resources/
mv logback.xml logback.xml.bak
```

**Solution 2**: Let starter provide default configuration

```bash
# If no custom config needed, don't create logback-spring.xml
# Starter will auto-configure with default template
```

**Solution 3**: Extend starter's default config

```xml
<!-- In patra-{service}-boot/src/main/resources/logback-spring.xml -->
<configuration>
    <!-- Include starter's default configuration -->
    <include resource="logback-spring-base.xml" />

    <!-- Add service-specific customization -->
    <logger name="com.patra.myservice.batch" level="DEBUG" />
</configuration>
```

---

## Service Identifier Issues (FR-015)

### Issue 13: Service Identifier Not Appearing in Logs

**Symptoms:**
- Logs missing `[service=X][layer=Y]` identifier
- Cannot distinguish logs from different services/layers
- Service boundary identification fails

**Root Causes:**
1. `spring.application.name` not configured
2. `papertrace.logging.layer` not specified and auto-detection failed
3. Logback pattern doesn't include service/layer placeholders

**Diagnostic Steps:**

```bash
# Check application.yml configuration
grep "spring.application.name" src/main/resources/application.yml

# Check logging configuration
grep "papertrace.logging.layer" src/main/resources/application.yml

# Verify log pattern includes service/layer
grep "service=" logs/application.log | head -1
```

**Solutions:**

**Solution 1**: Configure service name in application.yml

```yaml
# In patra-{service}-boot/src/main/resources/application.yml
spring:
  application:
    name: patra-registry  # REQUIRED for [service=patra-registry]

papertrace:
  logging:
    layer: adapter  # Optional: auto-detected from package if not specified
```

**Solution 2**: Verify logback pattern includes service/layer

```xml
<!-- In logback-spring.xml -->
<property name="LOG_PATTERN"
    value="%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} %-5level [%thread] [traceId=%X{traceId:-}][correlationId=%X{correlationId:-}][service=${spring.application.name:-unknown}][layer=${papertrace.logging.layer:-unknown}] %logger{36} : %msg%n"/>
```

**Expected Log Output (FR-015 compliant):**
```
2025-10-17T10:23:45.123+08:00 INFO [http-nio-8080-exec-1] [traceId=abc123][correlationId=xyz789][service=patra-registry][layer=adapter] c.p.r.a.ProvenanceController : Fetching provenance: source=PubMed
```

---

### Issue 14: Inconsistent Service Naming

**Symptoms:**
- Service names don't match naming convention
- Logs show `PatraRegistry`, `registry`, `patra_registry` (inconsistent)
- Service boundary detection fails

**Root Causes:**
1. Non-standard service naming (not lowercase, hyphens)
2. `spring.application.name` doesn't match module name
3. Manual service identifier in code (not using configuration)

**Diagnostic Steps:**

```bash
# Check for inconsistent service names
grep -E "\[service=" logs/application.log | awk -F'service=' '{print $2}' | awk -F']' '{print $1}' | sort -u

# Verify naming convention compliance
grep "spring.application.name" */src/main/resources/application.yml
```

**Solutions:**

**Solution 1**: Follow naming convention (FR-015)

```yaml
# ✅ CORRECT: lowercase, hyphens, starts with letter
spring:
  application:
    name: patra-registry

# ❌ WRONG: camelCase
spring:
  application:
    name: PatraRegistry

# ❌ WRONG: underscores
spring:
  application:
    name: patra_registry

# ❌ WRONG: missing prefix
spring:
  application:
    name: registry
```

**Valid Service Names:**
- `patra-registry`
- `patra-ingest`
- `patra-gateway`
- Format: `^[a-z][a-z0-9-]*$`

---

### Issue 15: Layer Identifier Wrong or Missing

**Symptoms:**
- Logs show `[layer=unknown]`
- Layer identifier doesn't match actual layer (e.g., `[layer=controller]` instead of `[layer=adapter]`)

**Root Causes:**
1. `papertrace.logging.layer` not configured
2. Auto-detection failed (package structure doesn't match convention)
3. Invalid layer name (not one of: adapter, app, domain, infra)

**Diagnostic Steps:**

```bash
# Check layer configuration
grep "papertrace.logging.layer" src/main/resources/application.yml

# Verify layer names in logs
grep -E "\[layer=" logs/application.log | awk -F'layer=' '{print $2}' | awk -F']' '{print $1}' | sort -u
```

**Solutions:**

**Solution 1**: Configure layer explicitly in application.yml

```yaml
# In patra-{service}-boot/src/main/resources/application.yml
papertrace:
  logging:
    layer: adapter  # Valid: adapter, app, domain, infra
```

**Solution 2**: Ensure package structure matches convention for auto-detection

```
com.patra.{service}.adapter     → [layer=adapter]
com.patra.{service}.app         → [layer=app]
com.patra.{service}.domain      → [layer=domain]
com.patra.{service}.infra       → [layer=infra]
```

**Valid Layer Names (FR-015):**
- `adapter` - Controllers, Jobs, Message Listeners
- `app` - Orchestrators, Use Case coordinators
- `domain` - Pure Java business logic
- `infra` - Repositories, External API clients

---

## Quick Reference

### Diagnostic Commands

```bash
# Check trace context in logs
grep -E "\[traceId=" logs/application.log | head -10

# Verify service/layer identifiers (FR-015)
grep -E "\[service=.*\]\[layer=" logs/application.log | head -1

# Check log level distribution
grep -oE "(INFO|DEBUG|WARN|ERROR|TRACE)" logs/application.log | sort | uniq -c

# Find classes with most logs
awk '{print $6}' logs/application.log | sort | uniq -c | sort -nr | head -20

# Check MDC fields
grep -oE "\[.*?\]" logs/application.log | sort -u

# Monitor log rate
tail -f logs/application.log | pv -l -i 1 -r > /dev/null
```

### Common Fixes

| Issue | Quick Fix |
|-------|-----------|
| No trace ID | Add logging starter dependency |
| MDC lost in async | Configure `MdcTaskDecorator` |
| Log level not changing | Wait 60s or refresh via Actuator |
| Sensitive data in logs | Use `LogSanitizer` before logging |
| High log volume | Revert to INFO level in Nacos |
| No service identifier (FR-015) | Configure `spring.application.name` |
| Missing layer identifier (FR-015) | Configure `papertrace.logging.layer` |

---

## Related Documentation

- [Trace Context Troubleshooting](trace-context-troubleshooting.md) - Detailed trace context debugging
- [Troubleshooting Log Levels](troubleshooting-log-levels.md) - Log level specific issues
- [Log Sampling Guide](log-sampling-guide.md) - Sampling configuration and debugging
- [Quickstart Guide](../../specs/001-logging-starter/quickstart.md) - Migration checklist

---

## Getting Help

**Still stuck?** Contact the logging team:

1. **Slack**: #dev-logging channel
2. **Documentation**: `specs/001-logging-starter/`
3. **Examples**: Check migrated services (`patra-registry`)
4. **Support**: Create ticket in JIRA (project: LOGGING)

**When asking for help, include:**
- Service name and version
- Log snippet showing the issue
- Configuration files (application.yml, logback-spring.xml)
- Diagnostic command outputs
- Steps already tried
