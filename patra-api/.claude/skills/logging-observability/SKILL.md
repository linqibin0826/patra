---
name: logging-observability
description: Comprehensive logging, tracing, and error handling patterns for Papertrace using SLF4J, Micrometer, and Spring Boot observability. Use when adding error handling, logging, performance tracking, or debugging issues.
---

# Papertrace Logging & Observability Skill

## Purpose

This skill enforces comprehensive error tracking, logging, and performance monitoring across all Papertrace microservices using SLF4J, Micrometer, and Spring Boot 3.5 observability features.

---

## When to Use This Skill

- Adding error handling to any code
- Creating new controllers or orchestrators
- Logging application events
- Tracking performance metrics
- Debugging production issues
- Adding tracing spans
- Monitoring database performance

---

## 🚨 CRITICAL RULES

1. **ALL ERRORS MUST BE LOGGED** - Never swallow exceptions silently
2. **USE SLF4J** - No `System.out.println()` or `printStackTrace()`
3. **STRUCTURED LOGGING** - Include context (userId, traceId, entityId)
4. **APPROPRIATE LOG LEVELS** - error/warn/info/debug/trace
5. **NO SENSITIVE DATA** - Never log passwords, tokens, or PII

---

## Logging Framework Stack

### Papertrace Observability Stack

```yaml
Logging:
  - SLF4J 2.0.x (Logging facade)
  - Logback 1.5.x (Implementation)
  - Spring Boot Starter Logging (Auto-configured)

Tracing:
  - Micrometer Tracing (Abstraction)
  - OpenTelemetry (Implementation)
  - Spring Boot Actuator (Metrics)

Monitoring:
  - Micrometer Metrics
  - Prometheus (Metrics collection)
  - Grafana (Visualization)
```

---

## 1. Basic Logging Patterns

### 1.1 Controller Logging

```java
package com.patra.registry.adapter.rest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@Slf4j  // ← Lombok generates logger
@RestController
@RequestMapping("/api/v1/provenances")
@RequiredArgsConstructor
public class ProvenanceController {

    private final ProvenanceManagementUseCase useCase;

    @PostMapping
    public ResponseEntity<ProvenanceResponse> create(
        @Valid @RequestBody CreateProvenanceCommand command
    ) {
        log.info("Creating provenance: code={}", command.provenanceCode());

        try {
            ProvenanceResult result = useCase.create(command);
            log.info("Provenance created successfully: id={}", result.id());
            return ResponseEntity.ok(ProvenanceResponse.from(result));

        } catch (ProvenanceException e) {
            log.error("Failed to create provenance: code={}, error={}",
                command.provenanceCode(), e.getMessage(), e);
            throw e;  // Let @ControllerAdvice handle
        }
    }
}
```

**Key Points**:
- ✅ Use `@Slf4j` (Lombok)
- ✅ Log method entry with key parameters
- ✅ Log successful outcomes
- ✅ Log errors with exception stack trace
- ✅ Include business identifiers (code, id)

---

### 1.2 Orchestrator Logging

```java
package com.patra.ingest.app.usecase.plan;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PlanIngestionOrchestrator implements PlanIngestionUseCase {

    private final PlanPersistenceCoordinator persistenceCoordinator;

    @Override
    public PlanIngestionResult ingest(PlanIngestionCommand command) {
        log.debug("Starting plan ingestion: provenance={}, window=[{}, {}]",
            command.provenanceCode(),
            command.startTime(),
            command.endTime());

        try {
            // Phase 1: Validate
            log.debug("Phase 1: Validating command");
            // ...

            // Phase 2: Generate slices
            log.debug("Phase 2: Generating slices");
            List<Slice> slices = // ...

            log.info("Generated {} slices for plan", slices.size());

            // Phase 3: Persist
            BatchPlan plan = persistenceCoordinator.save(/* ... */);

            log.info("Plan ingestion completed: planId={}, sliceCount={}",
                plan.id(), slices.size());

            return new PlanIngestionResult(plan.id(), slices.size());

        } catch (Exception e) {
            log.error("Plan ingestion failed: provenance={}, error={}",
                command.provenanceCode(), e.getMessage(), e);
            throw e;
        }
    }
}
```

**Logging Levels**:
- `debug`: Detailed flow (Phases 1, 2, 3)
- `info`: Important business events (slices generated, plan completed)
- `error`: Failures with full stack trace

---

### 1.3 Domain Event Logging

```java
package com.patra.ingest.domain.event;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class PlanCreatedEventHandler {

    @EventListener
    public void onPlanCreated(PlanCreatedEvent event) {
        log.info("PlanCreatedEvent received: planId={}, provenanceCode={}, sliceCount={}",
            event.planId(), event.provenanceCode(), event.sliceCount());

        try {
            // Handle event
            log.debug("Processing plan created event: planId={}", event.planId());
            // ...
            log.info("Plan created event processed successfully: planId={}", event.planId());

        } catch (Exception e) {
            log.error("Failed to process PlanCreatedEvent: planId={}, error={}",
                event.planId(), e.getMessage(), e);
            // Decide: re-throw or handle gracefully
        }
    }
}
```

---

## 2. Error Handling Patterns

### 2.1 Global Exception Handler

```java
package com.patra.registry.adapter.rest.advice;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ProvenanceNotFoundException.class)
    public ProblemDetail handleProvenanceNotFound(ProvenanceNotFoundException ex) {
        log.warn("Provenance not found: provenanceCode={}", ex.getProvenanceCode());

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND,
            ex.getMessage()
        );
        problem.setTitle("Provenance Not Found");
        problem.setProperty("provenanceCode", ex.getProvenanceCode());
        return problem;
    }

    @ExceptionHandler(ProvenanceValidationException.class)
    public ProblemDetail handleValidationError(ProvenanceValidationException ex) {
        log.error("Provenance validation failed: errors={}", ex.getErrors(), ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Validation failed"
        );
        problem.setTitle("Validation Error");
        problem.setProperty("errors", ex.getErrors());
        return problem;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred"
        );
        problem.setTitle("Internal Server Error");
        return problem;
    }
}
```

**Key Points**:
- ✅ Use `@RestControllerAdvice` for global handling
- ✅ Log at appropriate level (warn vs error)
- ✅ Return `ProblemDetail` (RFC 7807)
- ✅ Include business context in logs
- ❌ Don't expose internal details to client

---

### 2.2 Domain Exception Logging

```java
package com.patra.ingest.domain.exception;

import lombok.Getter;

@Getter
public class PlanValidationException extends RuntimeException {

    private final PlanId planId;
    private final String validationError;

    public PlanValidationException(PlanId planId, String validationError) {
        super(String.format("Plan validation failed: planId=%s, error=%s",
            planId, validationError));
        this.planId = planId;
        this.validationError = validationError;
    }

    public PlanValidationException(PlanId planId, String validationError, Throwable cause) {
        super(String.format("Plan validation failed: planId=%s, error=%s",
            planId, validationError), cause);
        this.planId = planId;
        this.validationError = validationError;
    }
}
```

**Usage**:
```java
if (plan.startTime().isAfter(plan.endTime())) {
    throw new PlanValidationException(
        plan.id(),
        "Start time must be before end time"
    );
}
```

**Logged as**:
```
ERROR c.p.i.a.u.PlanIngestionOrchestrator - Plan validation failed: planId=plan-123, error=Start time must be before end time
```

---

## 3. Structured Logging with MDC

### 3.1 Mapped Diagnostic Context (MDC)

```java
package com.patra.common.logging;

import org.slf4j.MDC;

public class LoggingContext {

    private static final String TRACE_ID = "traceId";
    private static final String USER_ID = "userId";
    private static final String PLAN_ID = "planId";
    private static final String PROVENANCE_CODE = "provenanceCode";

    public static void setTraceId(String traceId) {
        MDC.put(TRACE_ID, traceId);
    }

    public static void setUserId(String userId) {
        MDC.put(USER_ID, userId);
    }

    public static void setPlanId(String planId) {
        MDC.put(PLAN_ID, planId);
    }

    public static void setProvenanceCode(String provenanceCode) {
        MDC.put(PROVENANCE_CODE, provenanceCode);
    }

    public static void clear() {
        MDC.clear();
    }
}
```

**Usage in Controller**:
```java
@PostMapping
public ResponseEntity<PlanResponse> createPlan(
    @Valid @RequestBody CreatePlanCommand command,
    @AuthenticationPrincipal User user
) {
    LoggingContext.setUserId(user.getId());
    LoggingContext.setProvenanceCode(command.provenanceCode().value());

    try {
        PlanResult result = useCase.createPlan(command);
        LoggingContext.setPlanId(result.planId().value());

        log.info("Plan created successfully");  // ← Includes MDC context
        return ResponseEntity.ok(PlanResponse.from(result));

    } finally {
        LoggingContext.clear();  // ← Clean up MDC
    }
}
```

**Log Output** (with MDC):
```
2025-01-15 10:23:45.123 INFO [patra-ingest] [traceId=abc123, userId=user-456, provenanceCode=PUBMED, planId=plan-789] c.p.i.a.r.PlanController - Plan created successfully
```

---

### 3.2 Logback Configuration (logback-spring.xml)

```xml
<configuration>
    <springProperty scope="context" name="applicationName" source="spring.application.name"/>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>
                %d{yyyy-MM-dd HH:mm:ss.SSS} %5p [${applicationName}] [traceId=%X{traceId:-}, userId=%X{userId:-}, planId=%X{planId:-}] %logger{36} - %msg%n
            </pattern>
        </encoder>
    </appender>

    <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/${applicationName}.log</file>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/${applicationName}.%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>30</maxHistory>
        </rollingPolicy>
        <encoder>
            <pattern>
                %d{yyyy-MM-dd HH:mm:ss.SSS} %5p [${applicationName}] [traceId=%X{traceId:-}, userId=%X{userId:-}] %logger{36} - %msg%n
            </pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
        <appender-ref ref="FILE"/>
    </root>

    <!-- Papertrace packages -->
    <logger name="com.patra" level="DEBUG"/>

    <!-- Third-party libraries (reduce noise) -->
    <logger name="org.springframework" level="INFO"/>
    <logger name="com.baomidou.mybatisplus" level="WARN"/>
</configuration>
```

---

## 4. Performance Monitoring with Micrometer

### 4.1 Custom Metrics

```java
package com.patra.ingest.infra.monitoring;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class PlanMetrics {

    private final Counter plansCreated;
    private final Counter plansFailed;
    private final Timer planCreationTime;

    public PlanMetrics(MeterRegistry registry) {
        this.plansCreated = Counter.builder("plans.created")
            .description("Number of plans created")
            .tag("service", "patra-ingest")
            .register(registry);

        this.plansFailed = Counter.builder("plans.failed")
            .description("Number of plan creation failures")
            .tag("service", "patra-ingest")
            .register(registry);

        this.planCreationTime = Timer.builder("plans.creation.time")
            .description("Plan creation duration")
            .tag("service", "patra-ingest")
            .register(registry);
    }

    public void incrementPlansCreated(String provenanceCode) {
        plansCreated.increment();
    }

    public void incrementPlansFailed(String provenanceCode) {
        plansFailed.increment();
    }

    public <T> T recordCreationTime(String provenanceCode, Timer.Sample sample) {
        return sample.stop(planCreationTime);
    }
}
```

**Usage**:
```java
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator {

    private final PlanMetrics metrics;
    private final MeterRegistry registry;

    public PlanIngestionResult ingest(PlanIngestionCommand command) {
        Timer.Sample sample = Timer.start(registry);

        try {
            // Create plan
            PlanResult result = // ...

            metrics.incrementPlansCreated(command.provenanceCode().value());
            sample.stop(metrics.getPlanCreationTime());

            return result;

        } catch (Exception e) {
            metrics.incrementPlansFailed(command.provenanceCode().value());
            throw e;
        }
    }
}
```

---

### 4.2 Database Query Monitoring

```java
package com.patra.registry.infra.persistence;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DatabasePerformanceMonitor {

    private final Timer queryTimer;

    public DatabasePerformanceMonitor(MeterRegistry registry) {
        this.queryTimer = Timer.builder("database.query.time")
            .description("Database query execution time")
            .tag("service", "patra-registry")
            .register(registry);
    }

    public <T> T trackQuery(String operation, String entity, Supplier<T> query) {
        Timer.Sample sample = Timer.start();

        try {
            T result = query.get();

            long durationMs = sample.stop(queryTimer);
            if (durationMs > 100) {  // Slow query threshold
                log.warn("Slow query detected: operation={}, entity={}, durationMs={}",
                    operation, entity, durationMs);
            }

            return result;

        } catch (Exception e) {
            log.error("Database query failed: operation={}, entity={}, error={}",
                operation, entity, e.getMessage(), e);
            throw e;
        }
    }
}
```

**Usage**:
```java
@Repository
@RequiredArgsConstructor
public class ProvenanceRepositoryMpImpl implements ProvenancePort {

    private final RegProvenanceMapper mapper;
    private final DatabasePerformanceMonitor perfMonitor;

    @Override
    public Optional<Provenance> findById(ProvenanceId id) {
        return perfMonitor.trackQuery("findById", "Provenance", () -> {
            RegProvenanceDO dataObject = mapper.selectById(id.getValue());
            return Optional.ofNullable(dataObject)
                           .map(converter::toDomain);
        });
    }
}
```

---

## 5. Distributed Tracing

### 5.1 Micrometer Observation API

```java
package com.patra.ingest.app.usecase.task;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TaskExecutionOrchestrator {

    private final ObservationRegistry observationRegistry;

    public TaskExecutionResult execute(BatchTask task) {
        return Observation.createNotStarted("task.execution", observationRegistry)
            .lowCardinalityKeyValue("provenance", task.provenanceCode().value())
            .highCardinalityKeyValue("taskId", task.id().value())
            .observe(() -> {
                // Task execution logic
                return executeTask(task);
            });
    }
}
```

---

## 6. Log Level Guidelines

### When to Use Each Level

| Level | Use When | Example |
|-------|----------|---------|
| **ERROR** | Operation failed, requires attention | `log.error("Plan creation failed: planId={}", planId, e)` |
| **WARN** | Recoverable issue, degraded state | `log.warn("Slow query detected: {}ms", duration)` |
| **INFO** | Important business events | `log.info("Plan created: planId={}", planId)` |
| **DEBUG** | Detailed flow for debugging | `log.debug("Phase 1: Validating command")` |
| **TRACE** | Very detailed (method entry/exit) | `log.trace("Entering method: createPlan()")` |

---

## 7. Common Anti-Patterns

### ❌ DON'T

```java
// ❌ No System.out
System.out.println("Plan created");

// ❌ No printStackTrace
catch (Exception e) {
    e.printStackTrace();
}

// ❌ No silent catch
catch (Exception e) {
    // Silent - BAD!
}

// ❌ No sensitive data
log.info("User password: {}", password);  // ← Violation!

// ❌ No excessive logging in loops
for (Task task : tasks) {
    log.info("Processing task {}", task.id());  // ← 1000s of logs!
}
```

### ✅ DO

```java
// ✅ Use SLF4J
log.info("Plan created: planId={}", planId);

// ✅ Log with exception
catch (Exception e) {
    log.error("Operation failed", e);
    throw e;
}

// ✅ Sanitize sensitive data
log.info("User authenticated: userId={}", userId);  // ← OK

// ✅ Aggregate loop logging
log.info("Processing {} tasks", tasks.size());
tasks.forEach(this::processTask);
log.info("Completed processing {} tasks", tasks.size());
```

---

## 8. Testing Logging

### 8.1 Unit Test with Logback Test Appender

```java
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class PlanIngestionOrchestratorTest {

    @Test
    void shouldLogPlanCreation() {
        // Setup log capture
        Logger logger = (Logger) LoggerFactory.getLogger(PlanIngestionOrchestrator.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        // Execute
        orchestrator.ingest(command);

        // Verify logs
        List<ILoggingEvent> logs = appender.list;
        assertThat(logs).anyMatch(log ->
            log.getMessage().contains("Plan ingestion completed") &&
            log.getLevel() == Level.INFO
        );
    }
}
```

---

## 9. Configuration

### application.yml

```yaml
spring:
  application:
    name: patra-ingest

logging:
  level:
    root: INFO
    com.patra: DEBUG
    org.springframework: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss.SSS} %5p [%X{traceId:-}] %logger{36} - %msg%n"
  file:
    name: logs/patra-ingest.log

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,loggers
  metrics:
    tags:
      application: ${spring.application.name}
```

---

## Summary

**Key Practices**:
- ✅ Use `@Slf4j` (Lombok) for logging
- ✅ Log at appropriate levels (error/warn/info/debug)
- ✅ Include business context (userId, planId, provenanceCode)
- ✅ Use MDC for request-scoped context
- ✅ Monitor performance with Micrometer
- ✅ Never log sensitive data
- ✅ Use `@ControllerAdvice` for global error handling

**Tools**:
- **SLF4J + Logback**: Logging framework
- **Micrometer**: Metrics and observability
- **Spring Boot Actuator**: Monitoring endpoints
- **MDC**: Request-scoped context

**See Also**:
- [transaction-error-handling.md](../java-backend-guidelines/resources/transaction-error-handling.md) for transaction error patterns
- [testing-guide.md](../java-backend-guidelines/resources/testing-guide.md) for testing logging
