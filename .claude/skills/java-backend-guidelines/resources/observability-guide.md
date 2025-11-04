# 可观测性指南

**目的**: Patra 中的 SLF4J 日志模式、错误处理和可观测性最佳实践。

---

## 目录

1. [Overview](#overview)
2. [Logging Patterns](#logging-patterns)
3. [Log Levels](#log-levels)
4. [Error Handling](#error-handling)
5. [MDC for Distributed Tracing](#mdc-for-distributed-tracing)
6. [Performance Metrics with Micrometer](#performance-metrics-with-micrometer)
7. [Testing Logging](#testing-logging)
8. [Best Practices](#best-practices)

---

## 概览

### Observability Stack

```
┌─────────────────────────────────────────┐
│  Patra Observability Stack         │
├─────────────────────────────────────────┤
│  Logging:                               │
│    - SLF4J 2.0.x (Facade)               │
│    - Logback 1.5.x (Implementation)     │
│    - @Slf4j (Lombok)                    │
│                                         │
│  Tracing:                               │
│    - Micrometer Tracing                 │
│    - OpenTelemetry                      │
│    - Spring Boot Actuator               │
│                                         │
│  Monitoring:                            │
│    - Micrometer Metrics                 │
│    - Prometheus                         │
└─────────────────────────────────────────┘
```

### Core Principles

1. ✅ **Use SLF4J** - Never `System.out.println()`
2. ✅ **Parameterized messages** - Use `{}` placeholders for performance
3. ✅ **Appropriate log levels** - error/warn/info/debug/trace
4. ✅ **Include context** - entityId, traceId, userId, channel
5. ✅ **Log errors with stacktraces** - Use `, e` at the end
6. ❌ **NO sensitive data** - Never log passwords, tokens, PII

---

## Logging Patterns

### 1. Orchestrator Logging

**Pattern**: Log orchestration lifecycle with key context

**File**: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/relay/OutboxRelayOrchestrator.java`

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class OutboxRelayOrchestrator implements OutboxRelayUseCase {

  private final OutboxRelayExecutor executor;

  @Override
  @Transactional(propagation = Propagation.REQUIRES_NEW, timeout = 60)
  public RelayReport relay(OutboxRelayCommand instruction) {
    Instant startTime = Instant.now();
    String channelDesc = instruction.channel() != null ? instruction.channel().channel() : "ALL_CHANNELS";

    // ✅ Log entry with context
    if (log.isDebugEnabled()) {
      log.debug(
          "Starting outbox relay: channel={} batchSize={} leaseOwner={}",
          channelDesc,
          instruction.batchSize(),
          instruction.leaseOwner());
    }

    try {
      RelayPlan plan = planBuilder.buildFrom(instruction);
      RelayBatchResult result = executor.execute(plan);

      // ✅ Log success with key metrics
      log.info(
          "Outbox relay completed: channel={} processed={} published={} retried={} failed={} leaseMissed={} durationMs={}",
          channelDesc,
          result.totalMessages(),
          result.published(),
          result.retried(),
          result.failed(),
          result.leaseMissed(),
          Duration.between(startTime, Instant.now()).toMillis());

      return RelayReport.fromBatchResult(result, startTime);

    } catch (Exception e) {
      // ✅ Log error with stacktrace
      log.error("Outbox relay failed: channel={} error={}", channelDesc, e.getMessage(), e);
      throw new OutboxRelayExecutionException("Relay execution failed", e);
    }
  }
}
```

**Key Patterns**:
- ✅ Use `@Slf4j` Lombok annotation
- ✅ Log entry with `log.debug()` (use `isDebugEnabled()` check for expensive operations)
- ✅ Log success with key metrics (processed, published, failed, duration)
- ✅ Log errors with message + stacktrace (`, e` at the end)
- ✅ Include context: `channel`, `batchSize`, `leaseOwner`

### 2. Coordinator Logging

**Pattern**: Log coordinator-specific concerns with detailed context

**File**: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/usecase/relay/coordinator/RelayPublishCoordinator.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class RelayPublishCoordinator {

  public RelayResult publish(OutboxMessage message, RelayPlan plan) {
    // ✅ Debug-level for individual message processing
    if (log.isDebugEnabled()) {
      log.debug(
          "Publishing outbox message: id={} channel={} partition={} attempt={}",
          message.getId(),
          message.getChannel(),
          message.getPartitionKey(),
          message.getRetryCount() + 1);
    }

    try {
      // Publish logic...
      mqPublisher.publish(message);

      // ✅ Info-level for successful publish
      log.info(
          "Outbox message published: id={} channel={} partition={} attempt={}",
          message.getId(),
          message.getChannel(),
          message.getPartitionKey(),
          message.getRetryCount() + 1);

      return RelayResult.success(message.getRetryCount() + 1);

    } catch (TransientPublishException e) {
      // ✅ Warn-level for transient errors (will retry)
      log.warn(
          "Outbox message publish failed (transient): id={} channel={} attempt={} error={}, will retry at {}",
          message.getId(),
          message.getChannel(),
          message.getRetryCount() + 1,
          e.getErrorCode(),
          nextRetryAt);

      return RelayResult.deferred(
          message.getRetryCount() + 1, nextRetryAt, e.getErrorCode(), e.getMessage());

    } catch (FatalPublishException e) {
      // ✅ Error-level for permanent failures
      log.error(
          "Outbox message publish failed (fatal): id={} channel={} attempt={} error={}",
          message.getId(),
          message.getChannel(),
          message.getRetryCount() + 1,
          e.getErrorCode(),
          e);

      return RelayResult.failed(
          message.getRetryCount() + 1, e.getErrorCode(), e.getMessage());
    }
  }
}
```

**Key Patterns**:
- ✅ Use `log.isDebugEnabled()` check before expensive debug logging
- ✅ **Debug**: Individual message processing details
- ✅ **Info**: Successful operations
- ✅ **Warn**: Transient errors (retryable)
- ✅ **Error**: Permanent failures with stacktrace

### 3. Event Handler Logging

**Pattern**: Log event handling with idempotency checks

**File**: `patra-ingest/patra-ingest-app/src/main/java/com/patra/ingest/app/eventhandler/TaskCompletedEventHandler.java`

```java
@Slf4j
@Component
@RequiredArgsConstructor
public class TaskCompletedEventHandler {

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void handle(TaskCompletedEvent event) {
    try {
      // ✅ Debug: Event received
      log.debug("Handling TaskCompletedEvent taskId={} sliceId={}", event.taskId(), event.sliceId());

      TaskAggregate task = taskRepository.findBySliceId(event.sliceId())
          .orElseThrow(() -> new IllegalStateException("Task not found"));

      SliceStatus newStatus = SliceStatusCalculator.calculate(task.getStatus());
      PlanSliceAggregate slice = sliceRepository.findById(event.sliceId())
          .orElseThrow(() -> new IllegalStateException("Slice not found"));

      SliceStatus oldStatus = slice.getStatus();

      // ✅ Idempotency: log skip
      if (oldStatus == newStatus) {
        log.debug("Slice status unchanged, skip update sliceId={}", event.sliceId());
        return;
      }

      slice.updateStatus(newStatus);
      sliceRepository.save(slice);

      // ✅ Info: Important state change
      log.info(
          "Slice status updated sliceId={} planId={} {} -> {}",
          event.sliceId(),
          event.planId(),
          oldStatus,
          newStatus);

      eventPublisher.publishEvent(SliceStatusChangedEvent.of(
          event.sliceId(), event.planId(), oldStatus.getCode(), newStatus.getCode()));

    } catch (OptimisticLockingFailureException e) {
      // ✅ Warn: Expected concurrency conflict
      log.warn("Optimistic lock conflict, skip sliceId={}", event.sliceId());

    } catch (Exception e) {
      // ✅ Error: Unexpected failure
      log.error(
          "Failed to handle TaskCompletedEvent taskId={} sliceId={}",
          event.taskId(),
          event.sliceId(),
          e);
    }
  }
}
```

**Key Patterns**:
- ✅ **Debug**: Event received, idempotency skip
- ✅ **Info**: Important state changes (status transitions)
- ✅ **Warn**: Expected failures (optimistic lock)
- ✅ **Error**: Unexpected failures with stacktrace

### 4. Scheduled Job Logging

**Pattern**: Log job lifecycle with timing metrics

**File**: `patra-ingest/patra-ingest-adapter/src/main/java/com/patra/ingest/adapter/scheduler/job/AbstractProvenanceScheduleJob.java`

```java
@Slf4j
public abstract class AbstractProvenanceScheduleJob {

  protected void executeScheduleJob(String paramStr) {
    long startTime = System.currentTimeMillis();

    // ✅ Info: Job started
    log.info(
        "Job started: provenance={} operation={} param={}",
        getProvenanceCode(),
        getOperationCode(),
        paramStr);

    try {
      PlanIngestionCommand command = parseJobParam(paramStr);
      PlanIngestionResult result = planIngestionUseCase.ingestPlan(command);

      long elapsed = System.currentTimeMillis() - startTime;

      // ✅ Info: Job completed with metrics
      log.info(
          "Job completed: provenance={} operation={} planId={} slices={} costMs={}",
          getProvenanceCode(),
          getOperationCode(),
          result.planId(),
          result.sliceCount(),
          elapsed);

      XxlJobHelper.handleSuccess(
          String.format("Plan created: planId=%d, slices=%d", result.planId(), result.sliceCount()));

    } catch (Exception e) {
      long elapsed = System.currentTimeMillis() - startTime;

      // ✅ Error: Job failed with metrics
      log.error(
          "Job failed: provenance={} operation={} error={} costMs={}",
          getProvenanceCode(),
          getOperationCode(),
          e.getMessage(),
          elapsed,
          e);

      XxlJobHelper.handleFail("Job failed: " + e.getMessage());
      throw e;
    }
  }
}
```

**Key Patterns**:
- ✅ Log job start with parameters
- ✅ Log job completion with metrics (`costMs`)
- ✅ Log job failure with timing and stacktrace
- ✅ Report to XXL-Job scheduler (`XxlJobHelper`)

---

## Log Levels

### Level Guidelines

| Level | Use Case | Example |
|-------|----------|---------|
| **ERROR** | Permanent failures, unexpected exceptions | Failed to publish message, database constraint violation |
| **WARN** | Transient errors, expected failures, degraded state | Optimistic lock conflict, transient publish error, lease miss |
| **INFO** | Important business events, lifecycle changes | Plan created, message published, status changed |
| **DEBUG** | Detailed processing flow, idempotency skips | Event received, message processing, status unchanged |
| **TRACE** | Very detailed debugging (rarely used) | Variable values, loop iterations |

### Level Decision Tree

```
┌─────────────────────────────────────────────────┐
│  Is this an exception/error?                   │
└───────────┬─────────────────────────────────────┘
            │
      ┌─────▼─────┐
      │    YES    │
      └─────┬─────┘
            │
    ┌───────▼────────┐
    │ Will retry?    │
    ├────────┬───────┤
    │ YES    │  NO   │
    │ WARN   │ ERROR │
    └────────┴───────┘

      ┌─────▼─────┐
      │    NO     │
      └─────┬─────┘
            │
    ┌───────▼────────┐
    │ Business event │
    │ important?     │
    ├────────┬───────┤
    │ YES    │  NO   │
    │ INFO   │ DEBUG │
    └────────┴───────┘
```

### 示例

```java
// ✅ ERROR: Permanent failure
log.error("Failed to publish message: id={} channel={}", id, channel, e);

// ✅ WARN: Transient error (will retry)
log.warn("Lease acquisition failed, will retry: id={}", id);

// ✅ INFO: Important business event
log.info("Plan created: planId={} slices={}", planId, sliceCount);

// ✅ DEBUG: Detailed flow
log.debug("Processing message: id={} attempt={}", id, attemptNum);

// ❌ BAD: Using wrong level
log.error("User logged in");  // Should be INFO
log.info("Unexpected database error", e);  // Should be ERROR
```

---

## Error Handling

### 1. Controller Error Handling

```java
@RestController
@RequestMapping("/api/v1/provenances")
@RequiredArgsConstructor
@Slf4j
public class ProvenanceController {

  @PostMapping
  public ResponseEntity<ProvenanceResponse> create(@Valid @RequestBody CreateCommand command) {
    log.info("Creating provenance: code={}", command.provenanceCode());

    try {
      ProvenanceResult result = useCase.create(command);
      log.info("Provenance created: id={}", result.id());
      return ResponseEntity.ok(ProvenanceResponse.from(result));

    } catch (ProvenanceException e) {
      // ✅ Log error, let @ControllerAdvice handle response
      log.error("Failed to create provenance: code={}", command.provenanceCode(), e);
      throw e;
    }
  }
}
```

### 2. Global Exception Handler

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

  @ExceptionHandler(ProvenanceException.class)
  public ResponseEntity<ProblemDetail> handleProvenanceException(ProvenanceException e) {
    // ✅ Already logged in controller, just transform to ProblemDetail
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_REQUEST,
        e.getMessage());
    problem.setTitle("Provenance Error");
    return ResponseEntity.badRequest().body(problem);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleUnexpectedException(Exception e) {
    // ✅ Log unexpected errors
    log.error("Unexpected error", e);
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "An unexpected error occurred");
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problem);
  }
}
```

### 3. Defensive Logging in Catch Blocks

```java
// ✅ GOOD: Log and continue (non-critical failure)
try {
  slice.updateStatus(newStatus);
  sliceRepository.save(slice);
} catch (OptimisticLockingFailureException e) {
  log.warn("Optimistic lock conflict, skip sliceId={}", sliceId);
  return;  // Another thread handled it
} catch (Exception e) {
  log.error("Failed to update slice status sliceId={}", sliceId, e);
  // Don't throw - let other event handlers continue
}
```

```java
// ❌ BAD: Swallow exception without logging
try {
  slice.updateStatus(newStatus);
  sliceRepository.save(slice);
} catch (Exception e) {
  // ❌ Silent failure - impossible to debug!
}
```

---

## MDC for Distributed Tracing

### Mapped Diagnostic Context (MDC)

MDC allows you to add request-scoped context to all log messages automatically. This is essential for distributed tracing.

**Utility Class**:

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
    @AuthenticationPrincipal User user) {

  LoggingContext.setUserId(user.getId());
  LoggingContext.setProvenanceCode(command.provenanceCode().value());

  try {
    PlanResult result = useCase.createPlan(command);
    LoggingContext.setPlanId(result.planId().value());

    log.info("Plan created successfully");  // ← Includes MDC context automatically
    return ResponseEntity.ok(PlanResponse.from(result));

  } finally {
    LoggingContext.clear();  // ← Always clean up MDC
  }
}
```

**Log Output with MDC**:

```
2025-01-15 10:23:45.123 INFO [patra-ingest] [traceId=abc123, userId=user-456, provenanceCode=PUBMED, planId=plan-789] c.p.i.a.r.PlanController - Plan created successfully
```

**Logback Configuration** (`logback-spring.xml`):

```xml
<configuration>
  <springProperty scope="context" name="applicationName" source="spring.application.name"/>

  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>
        %d{yyyy-MM-dd HH:mm:ss.SSS} %5p [${applicationName}] [traceId=%X{traceId:-}, userId=%X{userId:-}] %logger{36} - %msg%n
      </pattern>
    </encoder>
  </appender>

  <root level="INFO">
    <appender-ref ref="CONSOLE"/>
  </root>

  <logger name="com.patra" level="DEBUG"/>
</configuration>
```

---

## Performance Metrics with Micrometer

### Custom Metrics

Track business metrics with Micrometer for observability.

**Metrics Component**:

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

  public void incrementPlansCreated() {
    plansCreated.increment();
  }

  public void incrementPlansFailed() {
    plansFailed.increment();
  }

  public Timer getPlanCreationTimer() {
    return planCreationTime;
  }
}
```

**Usage in Orchestrator**:

```java
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator {

  private final PlanMetrics metrics;
  private final MeterRegistry registry;

  public PlanIngestionResult ingest(PlanIngestionCommand command) {
    Timer.Sample sample = Timer.start(registry);

    try {
      // Create plan logic
      PlanResult result = createPlan(command);

      metrics.incrementPlansCreated();
      sample.stop(metrics.getPlanCreationTimer());

      return result;

    } catch (Exception e) {
      metrics.incrementPlansFailed();
      throw e;
    }
  }
}
```

**Metrics Output** (Prometheus format):

```
# HELP plans_created_total Number of plans created
# TYPE plans_created_total counter
plans_created_total{service="patra-ingest"} 1234.0

# HELP plans_creation_time_seconds Plan creation duration
# TYPE plans_creation_time_seconds summary
plans_creation_time_seconds_sum{service="patra-ingest"} 45.6
plans_creation_time_seconds_count{service="patra-ingest"} 1234
```

---

## Testing Logging

### Unit Test with ListAppender

Verify that your code logs correctly in unit tests.

**Test Example**:

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

## Best Practices

### ✅ DO

| Practice | Example |
|----------|---------|
| **Use @Slf4j** | `@Slf4j public class Foo {}` |
| **Parameterized messages** | `log.info("User {} logged in", userId)` |
| **Include context** | `log.error("Failed to save entity id={}", id, e)` |
| **Log stacktraces** | `log.error("Error message", exception)` |
| **Use appropriate levels** | ERROR for failures, INFO for business events |
| **Check isDebugEnabled()** | For expensive debug operations |
| **Log metrics** | `log.info("Job completed costMs={}", elapsed)` |

### ❌ DON'T

| Anti-pattern | Problem |
|--------------|---------|
| **System.out.println()** | Bypasses logging framework, no control |
| **String concatenation** | `log.info("User " + userId)` - poor performance |
| **Swallow exceptions** | `catch (Exception e) {}` - impossible to debug |
| **Log sensitive data** | `log.info("Password: {}", pwd)` - security risk |
| **Wrong log level** | `log.error("User logged in")` - noise in error logs |
| **Missing context** | `log.error("Save failed")` - no entity ID |
| **No stacktrace** | `log.error(e.getMessage())` - missing root cause |

### Parameterized Message Performance

```java
// ✅ GOOD: Parameterized (efficient)
log.debug("Processing message: id={} channel={}", id, channel);
// Only evaluates parameters if DEBUG is enabled

// ❌ BAD: String concatenation (inefficient)
log.debug("Processing message: id=" + id + " channel=" + channel);
// Always evaluates concatenation, even if DEBUG disabled
```

### isDebugEnabled() Check

```java
// ✅ GOOD: Check before expensive operation
if (log.isDebugEnabled()) {
  log.debug("Full message payload: {}", toJsonString(message));  // Expensive JSON serialization
}

// ❌ BAD: Expensive operation always runs
log.debug("Full message payload: {}", toJsonString(message));  // JSON serialized even if DEBUG disabled
```

### Context-Rich Logging

```java
// ✅ GOOD: Rich context
log.error(
    "Failed to publish outbox message: id={} channel={} partition={} attempt={} error={}",
    message.getId(),
    message.getChannel(),
    message.getPartitionKey(),
    message.getRetryCount(),
    e.getMessage(),
    e);

// ❌ BAD: No context
log.error("Publish failed", e);  // Which message? Which channel? Impossible to debug!
```

---

**相关文件：**
- [transaction-error-handling.md](transaction-error-handling.md) - @Transactional and ProblemDetail
- [event-driven-architecture.md](event-driven-architecture.md) - Event handler error patterns
- [outbox-pattern.md](outbox-pattern.md) - Outbox relay logging examples
- [adapter-layer-patterns.md](adapter-layer-patterns.md) - Job logging patterns

---

**📝 Status**: ✅ **已完成** - Comprehensive observability guide with real patterns from patra-ingest.
