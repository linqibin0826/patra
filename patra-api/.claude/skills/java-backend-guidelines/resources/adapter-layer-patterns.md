# Adapter Layer Patterns

**Purpose**: Driving adapters receive external triggers (HTTP, Jobs, MQ) and delegate to Application layer orchestrators.

---

## Table of Contents

1. [Overview](#overview)
2. [XXL-Job Pattern](#xxl-job-pattern)
3. [Key Principles](#key-principles)
4. [Anti-Patterns](#anti-patterns)
5. [Best Practices](#best-practices)

---

## Overview

### Adapter Layer Responsibilities

The **Adapter Layer** (Driving Adapters) handles external triggers and translates them to Application layer calls.

**✅ Adapter Layer SHOULD:**
- Receive external requests (HTTP, Job, MQ)
- Parse/validate input parameters
- Delegate to Use Case orchestrators
- Map domain results to adapter-specific responses
- Handle adapter-specific error reporting

**❌ Adapter Layer SHOULD NOT:**
- Contain business logic
- Access domain repositories directly
- Call infrastructure layer directly
- Implement retry logic (delegate to orchestrators)

---

## XXL-Job Pattern

### Overview

Papertrace uses **XXL-Job** for distributed scheduled tasks. The adapter layer provides job entry points.

### Pattern: Template Method (Abstract Base Job)

**Problem**: Multiple jobs share common logic (param parsing, error handling, metrics)

**Solution**: Extract common logic into abstract base class

**File**: `patra-ingest/patra-ingest-adapter/src/main/java/com/patra/ingest/adapter/scheduler/job/AbstractProvenanceScheduleJob.java`

```java
@Slf4j
public abstract class AbstractProvenanceScheduleJob {

  @Autowired private PlanIngestionUseCase planIngestionUseCase;
  @Autowired private ObjectMapper objectMapper;

  // ✅ Template method: subclasses provide provenance/operation
  protected abstract ProvenanceCode getProvenanceCode();
  protected abstract OperationCode getOperationCode();

  /**
   * Common job execution flow: parse params → call orchestrator → report result
   */
  protected void executeScheduleJob(String paramStr) {
    long startTime = System.currentTimeMillis();

    try {
      // ✅ Parse XXL-Job JSON parameters
      PlanIngestionCommand command = parseJobParam(paramStr);

      // ✅ Delegate to orchestrator
      PlanIngestionResult result = planIngestionUseCase.ingestPlan(command);

      // ✅ Report success to XXL-Job
      handleJobSuccess(result, startTime);
    } catch (Exception e) {
      // ✅ Report failure to XXL-Job
      handleJobFailure(e, startTime);
      throw e;  // Let XXL-Job handle retry
    }
  }

  private PlanIngestionCommand parseJobParam(String paramStr) {
    if (CharSequenceUtil.isBlank(paramStr)) {
      return buildDefaultCommand();
    }

    Map<String, Object> rawParams = objectMapper.readValue(paramStr, new TypeReference<>() {});
    ProvenanceScheduleJobParam jobParam = objectMapper.convertValue(rawParams, ProvenanceScheduleJobParam.class);

    return new PlanIngestionCommand(
        getProvenanceCode(),
        getOperationCode(),
        jobParam.step(),
        TriggerType.SCHEDULE,
        Scheduler.XXL,
        String.valueOf(XxlJobHelper.getJobId()),
        // ... other fields
    );
  }

  private void handleJobSuccess(PlanIngestionResult result, long startTime) {
    long elapsed = System.currentTimeMillis() - startTime;

    log.info(
        "Job completed: provenance={} operation={} planId={} costMs={}",
        getProvenanceCode(), getOperationCode(), result.planId(), elapsed);

    // ✅ Report to XXL-Job console
    XxlJobHelper.handleSuccess(
        String.format("Plan created: planId=%d, slices=%d", result.planId(), result.sliceCount()));
  }

  private void handleJobFailure(Exception e, long startTime) {
    long elapsed = System.currentTimeMillis() - startTime;

    log.error(
        "Job failed: provenance={} operation={} error={} costMs={}",
        getProvenanceCode(), getOperationCode(), e.getMessage(), elapsed, e);

    // ✅ Report to XXL-Job console
    XxlJobHelper.handleFail("Job failed: " + e.getMessage());
  }
}
```

### Concrete Job Implementation

**File**: `patra-ingest/patra-ingest-adapter/src/main/java/com/patra/ingest/adapter/scheduler/job/PubmedHarvestJob.java`

```java
@Slf4j
@Component
public class PubmedHarvestJob extends AbstractProvenanceScheduleJob {

  // ✅ Declare fixed provenance
  @Override
  protected ProvenanceCode getProvenanceCode() {
    return ProvenanceCode.PUBMED;
  }

  // ✅ Declare fixed operation
  @Override
  protected OperationCode getOperationCode() {
    return OperationCode.HARVEST;
  }

  /**
   * XXL-Job entrypoint: fetch param and delegate to base class
   */
  @XxlJob("pubmedHarvest")
  public void run() {
    String jobParam = XxlJobHelper.getJobParam();
    log.debug("PubMed harvest job triggered with param: {}", jobParam);

    // ✅ Delegate to template method
    executeScheduleJob(jobParam);
  }
}
```

**Benefits:**
- ✅ Each concrete job is ~20 lines (minimal boilerplate)
- ✅ Consistent error handling across all jobs
- ✅ Easy to add new provenance/operation jobs
- ✅ Testable (inject mock orchestrator in base class)

---

## Key Principles

### 1. Thin Adapter, Delegate to Orchestrator

```java
// ✅ GOOD: Adapter delegates immediately
@XxlJob("outboxRelay")
public void execute() {
  OutboxRelayJobParam param = parseParam(XxlJobHelper.getJobParam());

  OutboxRelayCommand command = buildCommand(param, Instant.now());

  // ✅ Delegate to orchestrator
  RelayReport report = relayUseCase.relay(command);

  // ✅ Report statistics
  XxlJobHelper.handleSuccess(formatReport(report));
}
```

```java
// ❌ BAD: Business logic in adapter
@XxlJob("outboxRelay")
public void execute() {
  List<OutboxMessage> messages = outboxRepository.fetchPending(...);  // ❌ Direct repo access

  for (OutboxMessage msg : messages) {
    if (msg.getRetryCount() < 3) {  // ❌ Business rule in adapter
      publisher.publish(msg);
    }
  }
}
```

### 2. Parameter Parsing and Validation

```java
// ✅ GOOD: Parse in adapter, validate in domain
private OutboxRelayCommand buildCommand(OutboxRelayJobParam param, Instant now) {
  return new OutboxRelayCommand(
      resolveChannel(param.channel()),
      now,
      param.batchSize(),
      parseDuration(param.leaseDuration()),
      param.maxAttempts(),
      parseDuration(param.initialBackoff()),
      buildLeaseOwner()  // ✅ Adapter-specific (host+jobId+threadId)
  );
}

private String buildLeaseOwner() {
  String host = NetUtil.getLocalHostName();
  return host + '-' + XxlJobHelper.getJobId() + '-'
      + Thread.currentThread().threadId() + '-' + IdUtil.fastSimpleUUID();
}
```

### 3. Error Handling and Reporting

```java
// ✅ GOOD: Report errors to scheduler
try {
  RelayReport report = relayUseCase.relay(command);
  XxlJobHelper.handleSuccess(formatSuccessMessage(report));
} catch (OutboxRelayExecutionException ex) {
  log.error("Relay execution failed: {}", ex.getMessage(), ex);
  XxlJobHelper.handleFail("Relay failed: " + ex.getMessage());
  throw ex;  // ✅ Let XXL-Job retry policy decide
}
```

```java
// ❌ BAD: Swallow exceptions
try {
  relayUseCase.relay(command);
} catch (Exception ex) {
  log.error("Error: {}", ex.getMessage());
  // ❌ No reporting to scheduler, job appears successful!
}
```

### 4. Idempotent Job Execution

```java
// ✅ GOOD: Generate unique lease owner per execution
private String buildLeaseOwner() {
  // host-jobId-threadId-uuid ensures uniqueness
  String host = NetUtil.getLocalHostName();
  return host + '-' + XxlJobHelper.getJobId() + '-'
      + Thread.currentThread().threadId() + '-' + IdUtil.fastSimpleUUID();
}
```

**Why:**
- Multiple job instances might run concurrently
- Lease owner identifies which instance owns a message
- UUID prevents collision if job restarts quickly

---

## Anti-Patterns

### ❌ Business Logic in Adapter

```java
// ❌ BAD: Adapter contains business rules
@XxlJob("taskExecution")
public void execute() {
  List<Task> tasks = taskRepository.findReady();

  for (Task task : tasks) {
    // ❌ Business logic: retry calculation, status validation
    if (task.getRetryCount() < 3 && task.getStatus() == TaskStatus.PENDING) {
      taskExecutor.execute(task);
      task.setStatus(TaskStatus.RUNNING);
      taskRepository.save(task);
    }
  }
}
```

```java
// ✅ GOOD: Delegate to orchestrator
@XxlJob("taskExecution")
public void execute() {
  TaskExecutionCommand command = parseCommand(XxlJobHelper.getJobParam());

  // ✅ Orchestrator handles all business logic
  TaskExecutionResult result = taskExecutionUseCase.execute(command);

  XxlJobHelper.handleSuccess(formatResult(result));
}
```

### ❌ Direct Repository Access

```java
// ❌ BAD: Adapter bypasses Application layer
@XxlJob("cleanupOldData")
public void execute() {
  Instant cutoff = Instant.now().minus(Duration.ofDays(90));

  // ❌ Direct access to Infrastructure layer
  outboxRepository.deleteOlderThan(cutoff);
  taskRepository.deleteOlderThan(cutoff);
}
```

```java
// ✅ GOOD: Use cleanup orchestrator
@XxlJob("cleanupOldData")
public void execute() {
  CleanupCommand command = new CleanupCommand(
      Instant.now().minus(Duration.ofDays(90)));

  // ✅ Orchestrator coordinates cleanup across aggregates
  CleanupResult result = cleanupUseCase.cleanup(command);

  XxlJobHelper.handleSuccess(
      String.format("Cleaned %d outbox, %d tasks",
          result.outboxDeleted(), result.tasksDeleted()));
}
```

### ❌ Missing Error Reporting

```java
// ❌ BAD: No error reporting to scheduler
@XxlJob("importData")
public void execute() {
  try {
    importUseCase.importData(...);
  } catch (Exception ex) {
    log.error("Import failed", ex);
    // ❌ XXL-Job thinks job succeeded!
  }
}
```

```java
// ✅ GOOD: Report errors properly
@XxlJob("importData")
public void execute() {
  try {
    ImportResult result = importUseCase.importData(...);
    XxlJobHelper.handleSuccess("Imported: " + result.count());
  } catch (Exception ex) {
    log.error("Import failed", ex);
    XxlJobHelper.handleFail("Import failed: " + ex.getMessage());
    throw ex;  // ✅ Let XXL-Job retry
  }
}
```

---

## Best Practices

### ✅ DO

| Practice | Reason |
|----------|--------|
| **Delegate to orchestrators** | Keep adapters thin, business logic in Application layer |
| **Parse parameters in adapter** | Adapter-specific format (JSON, env vars, etc.) |
| **Report to scheduler** | Use `XxlJobHelper.handleSuccess/Fail()` for visibility |
| **Use template pattern** | Extract common logic into abstract base class |
| **Generate unique identifiers** | Lease owner, trace ID for distributed coordination |
| **Log job lifecycle** | Start, success, failure with timing metrics |

### ❌ DON'T

| Anti-pattern | Problem |
|--------------|---------|
| **Business logic in adapter** | Violates layered architecture, hard to test |
| **Direct repository access** | Bypasses transaction boundaries and business rules |
| **Swallow exceptions** | Scheduler can't detect failures, no retry |
| **Hardcode configuration** | Use Nacos/env vars for flexibility |
| **Skip parameter validation** | Fail fast on invalid input |

### Configuration Best Practices

```java
// ✅ GOOD: Externalize configuration
@Component
@RequiredArgsConstructor
public class OutboxRelayJob {

  private final OutboxRelayProperties properties;  // ✅ From Nacos
  private final OutboxRelayUseCase relayUseCase;

  @XxlJob("outboxRelay")
  public void execute() {
    // ✅ Check feature toggle
    if (!properties.isEnabled()) {
      log.info("Outbox relay disabled, skip execution");
      XxlJobHelper.handleSuccess("Relay disabled");
      return;
    }

    // ✅ Use configured values
    OutboxRelayCommand command = new OutboxRelayCommand(
        /* channel */ null,  // All channels
        Instant.now(),
        properties.getBatchSize(),        // ✅ From config
        properties.getLeaseDuration(),    // ✅ From config
        properties.getMaxAttempts(),      // ✅ From config
        properties.getInitialBackoff(),   // ✅ From config
        buildLeaseOwner()
    );

    RelayReport report = relayUseCase.relay(command);
    XxlJobHelper.handleSuccess(formatReport(report));
  }
}
```

### Testing Jobs

```java
// ✅ GOOD: Test base class with mock orchestrator
@ExtendWith(MockitoExtension.class)
class AbstractProvenanceScheduleJobTest {

  @Mock private PlanIngestionUseCase mockUseCase;
  @Mock private ObjectMapper mockMapper;

  private TestJob job;

  @BeforeEach
  void setUp() {
    job = new TestJob();
    ReflectionTestUtils.setField(job, "planIngestionUseCase", mockUseCase);
    ReflectionTestUtils.setField(job, "objectMapper", mockMapper);
  }

  @Test
  void should_delegate_to_orchestrator() {
    // Given
    String param = "{\"windowFrom\":\"2024-01-01T00:00:00Z\"}";
    PlanIngestionResult expectedResult = new PlanIngestionResult(123L, 10);
    when(mockUseCase.ingestPlan(any())).thenReturn(expectedResult);

    // When
    job.executeScheduleJob(param);

    // Then
    verify(mockUseCase).ingestPlan(argThat(cmd ->
        cmd.provenanceCode() == ProvenanceCode.PUBMED &&
        cmd.operationCode() == OperationCode.HARVEST
    ));
  }

  @Test
  void should_handle_orchestrator_exception() {
    // Given
    when(mockUseCase.ingestPlan(any())).thenThrow(new RuntimeException("Test error"));

    // When/Then
    assertThrows(RuntimeException.class, () -> job.executeScheduleJob("{}"));
  }

  // Test job for testing abstract base
  private static class TestJob extends AbstractProvenanceScheduleJob {
    @Override protected ProvenanceCode getProvenanceCode() { return ProvenanceCode.PUBMED; }
    @Override protected OperationCode getOperationCode() { return OperationCode.HARVEST; }
  }
}
```

---

**Related Files:**
- [orchestrator-coordinator-patterns.md](orchestrator-coordinator-patterns.md) - Application layer orchestration
- [architecture-overview.md](architecture-overview.md) - Hexagonal Architecture overview
- [outbox-pattern.md](outbox-pattern.md) - OutboxRelayJob implementation details

---

**📝 Status**: ✅ **COMPLETE** - Comprehensive guide to Adapter Layer patterns from patra-ingest with XXL-Job examples.
