# Plan/Task Workflow and Lifecycle

## Overview

This document describes the complete lifecycle of Plans and Tasks in Papertrace, from creation through execution to completion. Understanding these workflows is critical for implementing features correctly and debugging issues.

---

## Plan Lifecycle

### State Diagram

```
         ┌─────────┐
         │ CREATED │ (initial state)
         └────┬────┘
              │
      markAsRunning()
              │
         ┌────▼────┐
    ┌───┤ RUNNING │───┐
    │   └─────────┘   │
    │                 │
cancel()      allTasksSucceeded()
    │                 │
┌───▼────┐       ┌────▼──────┐
│CANCELLED│      │ COMPLETED │
└────────┘       └───────────┘
    │                 │
    │    anyTaskExhausted()
    │                 │
    │            ┌────▼────┐
    └───────────→│ FAILED  │
                 └─────────┘
```

### States

| State | Description | Can Transition To |
|-------|-------------|-------------------|
| `CREATED` | Plan initialized, Slices generated | RUNNING, CANCELLED |
| `RUNNING` | Tasks are executing | COMPLETED, FAILED, CANCELLED |
| `COMPLETED` | All Tasks succeeded | (terminal) |
| `FAILED` | One or more Tasks exhausted retries | (terminal) |
| `CANCELLED` | User cancelled execution | (terminal) |

---

## Plan Creation Workflow

### Input

```java
public record CreatePlanCommand(
    ProvenanceCode provenanceCode,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String operationType,  // "harvest", "update", "backfill"
    String createdBy
) { }
```

### Workflow Steps

**Step 1: Validate Input**

```java
public class PlanCreationOrchestrator {
    public PlanCreationResult createPlan(CreatePlanCommand command) {
        // 1.1 Validate time window
        if (command.startTime().isAfter(command.endTime())) {
            throw new PlanValidationException("Start time must be before end time");
        }

        Duration window = Duration.between(
            command.startTime(),
            command.endTime()
        );

        // 1.2 Load Provenance configuration
        ProvenanceConfiguration config = configRepository.loadConfiguration(
            command.provenanceCode(),
            command.operationType(),
            Instant.now()
        ).orElseThrow(() -> new ConfigurationNotFoundException(...));

        // 1.3 Check max window size
        if (window.compareTo(config.windowOffset().maxWindowSize()) > 0) {
            throw new PlanValidationException(
                "Window too large. Max: " + config.windowOffset().maxWindowSize()
            );
        }
```

**Step 2: Generate Slices**

```java
        // 2.1 Calculate slice boundaries
        List<Slice> slices = slicingService.generateSlices(
            command.startTime(),
            command.endTime(),
            config.windowOffset()
        );

        // 2.2 Estimate record count for each Slice
        for (Slice slice : slices) {
            int estimated = estimationService.estimateRecordCount(
                command.provenanceCode(),
                slice.startTime(),
                slice.endTime()
            );
            slice.setEstimatedRecordCount(estimated);
        }
```

**Step 3: Create Plan Entity**

```java
        // 3.1 Create Plan aggregate
        BatchPlan plan = new BatchPlan(
            PlanId.generate(),
            command.provenanceCode(),
            command.startTime(),
            command.endTime(),
            PlanStatus.CREATED,
            command.createdBy(),
            slices,
            Instant.now(),
            Instant.now()
        );

        // 3.2 Persist Plan and Slices
        planRepository.save(plan);
```

**Step 4: Emit Domain Event**

```java
        // 4.1 Emit PlanCreatedEvent
        eventPublisher.publish(new PlanCreatedEvent(
            plan.id(),
            plan.provenanceCode(),
            plan.startTime(),
            plan.endTime(),
            slices.size()
        ));

        return new PlanCreationResult(plan.id(), slices.size());
    }
}
```

### Output

```java
public record PlanCreationResult(
    PlanId planId,
    int sliceCount
) { }
```

---

## Slice Generation Algorithm

### Input

- `startTime`: Plan start time
- `endTime`: Plan end time
- `WindowOffsetConfig`: Slice duration configuration

### Algorithm

```java
public class SlicingService {
    public List<Slice> generateSlices(
        LocalDateTime startTime,
        LocalDateTime endTime,
        WindowOffsetConfig windowConfig
    ) {
        List<Slice> slices = new ArrayList<>();
        LocalDateTime current = startTime;
        int sliceNumber = 1;

        while (current.isBefore(endTime)) {
            // Calculate slice end time
            LocalDateTime sliceEnd = current.plus(windowConfig.sliceDuration());

            // Adjust if exceeds Plan end time
            if (sliceEnd.isAfter(endTime)) {
                sliceEnd = endTime;
            }

            // Create Slice
            slices.add(new Slice(
                SliceId.generate(),
                null,  // planId set later
                current,
                sliceEnd,
                0,  // estimatedRecordCount set later
                SliceStatus.PENDING,
                sliceNumber++
            ));

            current = sliceEnd;
        }

        return slices;
    }
}
```

### Example

**Input:**
- startTime: 2024-01-01 00:00
- endTime: 2024-12-31 23:59
- sliceDuration: 30 days (monthly)

**Output:**
```
Slice 1:  2024-01-01 00:00 to 2024-01-31 00:00 (30 days)
Slice 2:  2024-01-31 00:00 to 2024-03-01 00:00 (30 days)
Slice 3:  2024-03-01 00:00 to 2024-03-31 00:00 (30 days)
...
Slice 12: 2024-11-30 00:00 to 2024-12-31 23:59 (31 days, adjusted)
```

---

## Task Generation Workflow

### Trigger

**Event**: `PlanCreatedEvent` received

**Event Handler**:
```java
@Component
public class PlanCreatedEventHandler {
    @EventListener
    public void onPlanCreated(PlanCreatedEvent event) {
        taskGenerationOrchestrator.generateTasksForPlan(event.planId());
    }
}
```

### Workflow Steps

**Step 1: Load Plan and Slices**

```java
public class TaskGenerationOrchestrator {
    public void generateTasksForPlan(PlanId planId) {
        // 1.1 Load Plan
        BatchPlan plan = planRepository.findById(planId)
            .orElseThrow(() -> new PlanNotFoundException(planId));

        // 1.2 Load configuration
        ProvenanceConfiguration config = configRepository.loadConfiguration(
            plan.provenanceCode(),
            "harvest",  // Assume harvest operation
            Instant.now()
        ).orElseThrow();
```

**Step 2: Generate Tasks for Each Slice**

```java
        List<BatchTask> allTasks = new ArrayList<>();

        for (Slice slice : plan.slices()) {
            List<BatchTask> sliceTasks = generateTasksForSlice(
                plan,
                slice,
                config
            );
            allTasks.addAll(sliceTasks);
        }

        // Save all Tasks
        taskRepository.saveAll(allTasks);
    }

    private List<BatchTask> generateTasksForSlice(
        BatchPlan plan,
        Slice slice,
        ProvenanceConfiguration config
    ) {
        List<BatchTask> tasks = new ArrayList<>();

        int estimatedCount = slice.estimatedRecordCount();
        int pageSize = config.pagination().pageSize();

        // Calculate number of Tasks (pages) needed
        int taskCount = (int) Math.ceil((double) estimatedCount / pageSize);
        int maxPages = config.pagination().maxPages();
        taskCount = Math.min(taskCount, maxPages);  // Respect max pages

        // Generate Tasks
        for (int page = 0; page < taskCount; page++) {
            Map<String, Object> params = buildApiParams(
                slice.startTime(),
                slice.endTime(),
                page,
                pageSize,
                config.pagination().strategy()
            );

            String businessKey = generateBusinessKey(
                plan.provenanceCode(),
                params
            );

            tasks.add(new BatchTask(
                TaskId.generate(),
                plan.id(),
                slice.id(),
                plan.provenanceCode(),
                businessKey,
                params,
                TaskStatus.PENDING,
                0,  // retryCount
                null,  // recordsFetched
                null,  // errorMessage
                Instant.now(),
                Instant.now()
            ));
        }

        return tasks;
    }
}
```

**Step 3: Build API Parameters**

```java
    private Map<String, Object> buildApiParams(
        LocalDateTime startTime,
        LocalDateTime endTime,
        int page,
        int pageSize,
        PaginationStrategy strategy
    ) {
        Map<String, Object> params = new HashMap<>();

        // Add time window parameters
        params.put("mindate", startTime.format(DateTimeFormatter.ISO_DATE));
        params.put("maxdate", endTime.format(DateTimeFormatter.ISO_DATE));

        // Add pagination parameters
        switch (strategy) {
            case OFFSET:
                params.put("retstart", page * pageSize);
                params.put("retmax", pageSize);
                break;
            case PAGE_NUMBER:
                params.put("page", page + 1);  // 1-indexed
                params.put("pageSize", pageSize);
                break;
            case CURSOR:
                // Cursor set later from previous response
                params.put("limit", pageSize);
                break;
        }

        return params;
    }
```

**Step 4: Generate Business Key**

```java
    private String generateBusinessKey(
        ProvenanceCode provenanceCode,
        Map<String, Object> params
    ) {
        // Sort params for consistent hashing
        List<String> sortedKeys = new ArrayList<>(params.keySet());
        Collections.sort(sortedKeys);

        StringBuilder sb = new StringBuilder();
        sb.append(provenanceCode.value());

        for (String key : sortedKeys) {
            sb.append("_").append(key).append("=").append(params.get(key));
        }

        return DigestUtils.md5Hex(sb.toString());
    }
```

### Example Output

**Slice**:
- startTime: 2024-01-01
- endTime: 2024-01-31
- estimatedRecordCount: 5000
- pageSize: 1000

**Generated Tasks**:
```
Task 1:
  businessKey: md5("PUBMED_mindate=2024-01-01_maxdate=2024-01-31_retstart=0_retmax=1000")
  params: {mindate=2024-01-01, maxdate=2024-01-31, retstart=0, retmax=1000}

Task 2:
  businessKey: md5("PUBMED_mindate=2024-01-01_maxdate=2024-01-31_retstart=1000_retmax=1000")
  params: {mindate=2024-01-01, maxdate=2024-01-31, retstart=1000, retmax=1000}

...

Task 5:
  businessKey: md5("PUBMED_mindate=2024-01-01_maxdate=2024-01-31_retstart=4000_retmax=1000")
  params: {mindate=2024-01-01, maxdate=2024-01-31, retstart=4000, retmax=1000}
```

---

## Task Execution Workflow

### Task State Diagram

```
         ┌─────────┐
         │ PENDING │ (queued)
         └────┬────┘
              │
       pickForExecution()
              │
         ┌────▼────┐
         │ RUNNING │
         └────┬────┘
              │
              ├────── success ──────→ ┌───────────┐
              │                        │ SUCCEEDED │
              │                        └───────────┘
              │
              └────── failure ──────→ ┌────────┐
                                       │ FAILED │
                                       └───┬────┘
                                           │
                                   retry < maxRetries?
                                           │
                          ┌────────────────┼────────────────┐
                          │ YES                             │ NO
                          ▼                                 ▼
                    ┌─────────┐                      ┌───────────┐
                    │ PENDING │                      │ EXHAUSTED │
                    └─────────┘                      └───────────┘
                    (retry)                          (terminal)
```

### Workflow Steps

**Step 1: Pick Task for Execution**

```java
public class TaskExecutionOrchestrator {
    @Scheduled(fixedDelay = 1000)  // Every 1 second
    public void executePendingTasks() {
        // 1.1 Find PENDING Tasks
        List<BatchTask> pendingTasks = taskRepository.findByStatus(
            TaskStatus.PENDING,
            PageRequest.of(0, 50)  // Batch size from config
        );

        // 1.2 Execute in parallel
        pendingTasks.parallelStream()
            .forEach(this::executeTask);
    }

    private void executeTask(BatchTask task) {
        try {
            // 1.3 Check idempotency
            Optional<BatchTask> existing = taskRepository.findByBusinessKey(
                task.businessKey()
            );
            if (existing.isPresent() && existing.get().status() == TaskStatus.SUCCEEDED) {
                log.info("Task {} already succeeded, skipping", task.id());
                return;
            }

            // 1.4 Mark as RUNNING
            task = task.withStatus(TaskStatus.RUNNING);
            taskRepository.save(task);
```

**Step 2: Load Expressions and Render Parameters**

```java
            // 2.1 Load Expressions from registry
            List<Expression> expressions = expressionRepository.findByProvenance(
                task.provenanceCode()
            );

            // 2.2 Render API URL
            String apiUrl = expressionRenderer.renderUrl(
                task.params(),
                expressions
            );
```

**Step 3: Execute HTTP Call**

```java
            // 3.1 Load HTTP configuration
            HttpConfig httpConfig = configRepository.loadConfiguration(
                task.provenanceCode(),
                "harvest",
                Instant.now()
            ).map(ProvenanceConfiguration::http)
             .orElseThrow();

            // 3.2 Make HTTP call
            HttpResponse<String> response = httpClient.send(
                HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(httpConfig.readTimeoutDuration())
                    .headers(httpConfig.headers().entrySet().stream()
                        .flatMap(e -> Stream.of(e.getKey(), e.getValue()))
                        .toArray(String[]::new))
                    .GET()
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
```

**Step 4: Handle Response**

```java
            // 4.1 Check HTTP status
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                // Success path
                handleSuccess(task, response.body());
            } else {
                // Error path
                handleError(task, response.statusCode(), response.body());
            }

        } catch (Exception e) {
            handleException(task, e);
        }
    }
```

**Step 5: Success Handler**

```java
    private void handleSuccess(BatchTask task, String responseBody) {
        // 5.1 Parse response
        JsonNode json = objectMapper.readTree(responseBody);
        int recordCount = json.path("esearchresult").path("count").asInt();

        // 5.2 Extract record IDs
        List<String> recordIds = StreamSupport.stream(
            json.path("esearchresult").path("idlist").spliterator(),
            false
        ).map(JsonNode::asText)
         .toList();

        // 5.3 Send to storage service
        storageClient.storeRecords(new StoreRecordsRequest(
            task.provenanceCode(),
            task.planId(),
            recordIds
        ));

        // 5.4 Update Task status
        task = task.withStatus(TaskStatus.SUCCEEDED)
                   .withRecordsFetched(recordCount)
                   .withUpdatedAt(Instant.now());
        taskRepository.save(task);

        log.info("Task {} succeeded, fetched {} records", task.id(), recordCount);
    }
```

**Step 6: Error Handler**

```java
    private void handleError(BatchTask task, int statusCode, String responseBody) {
        // 6.1 Load retry configuration
        RetryConfig retryConfig = configRepository.loadConfiguration(
            task.provenanceCode(),
            "harvest",
            Instant.now()
        ).map(ProvenanceConfiguration::retry)
         .orElseThrow();

        // 6.2 Check if retriable
        boolean isRetriable = retryConfig.retriableHttpCodes().contains(statusCode);

        if (isRetriable && task.retryCount() < retryConfig.maxRetries()) {
            // 6.3 Schedule retry
            task = task.withStatus(TaskStatus.PENDING)
                       .withRetryCount(task.retryCount() + 1)
                       .withErrorMessage("HTTP " + statusCode + ": " + responseBody)
                       .withUpdatedAt(Instant.now());

            Duration delay = retryConfig.calculateDelay(task.retryCount());
            scheduleRetry(task, delay);

            log.warn("Task {} failed (HTTP {}), retrying in {} (attempt {}/{})",
                task.id(), statusCode, delay, task.retryCount(), retryConfig.maxRetries());
        } else {
            // 6.4 Exhaust retries
            task = task.withStatus(TaskStatus.EXHAUSTED)
                       .withErrorMessage("HTTP " + statusCode + ": " + responseBody)
                       .withUpdatedAt(Instant.now());
            taskRepository.save(task);

            log.error("Task {} exhausted retries after {} attempts",
                task.id(), task.retryCount());
        }
    }
```

---

## Plan Completion Check

### Trigger

**Event**: `TaskStatusChangedEvent` (any Task status change)

**Event Handler**:
```java
@Component
public class TaskStatusChangedEventHandler {
    @EventListener
    public void onTaskStatusChanged(TaskStatusChangedEvent event) {
        planCompletionChecker.checkPlanCompletion(event.planId());
    }
}
```

### Workflow

```java
public class PlanCompletionChecker {
    public void checkPlanCompletion(PlanId planId) {
        // 1. Load Plan
        BatchPlan plan = planRepository.findById(planId)
            .orElseThrow();

        if (plan.status() != PlanStatus.RUNNING) {
            return;  // Only check RUNNING plans
        }

        // 2. Load all Tasks for Plan
        List<BatchTask> tasks = taskRepository.findByPlanId(planId);

        // 3. Check Task statuses
        long succeeded = tasks.stream()
            .filter(t -> t.status() == TaskStatus.SUCCEEDED)
            .count();
        long failed = tasks.stream()
            .filter(t -> t.status() == TaskStatus.FAILED)
            .count();
        long exhausted = tasks.stream()
            .filter(t -> t.status() == TaskStatus.EXHAUSTED)
            .count();
        long pending = tasks.stream()
            .filter(t -> t.status() == TaskStatus.PENDING)
            .count();
        long running = tasks.stream()
            .filter(t -> t.status() == TaskStatus.RUNNING)
            .count();

        // 4. Determine Plan status
        if (succeeded == tasks.size()) {
            // All Tasks succeeded
            plan.markAsCompleted();
            planRepository.save(plan);
            eventPublisher.publish(new PlanCompletedEvent(planId));

        } else if (exhausted > 0 && (pending + running) == 0) {
            // Some Tasks exhausted, no more pending/running
            plan.markAsFailed();
            planRepository.save(plan);
            eventPublisher.publish(new PlanFailedEvent(planId, exhausted));
        }
        // Else: Still running, do nothing
    }
}
```

---

## Retry Mechanism

### Exponential Backoff Example

**Configuration:**
```java
RetryConfig retryConfig = new RetryConfig(
    5,                            // maxRetries
    BackoffStrategy.EXPONENTIAL,
    Duration.ofSeconds(5),        // initialDelay
    Duration.ofSeconds(60),       // maxDelay
    List.of(429, 503, 504),
    ...
);
```

**Retry Schedule:**

| Attempt | Delay Calculation | Actual Delay | Total Wait Time |
|---------|-------------------|--------------|-----------------|
| 1st retry | 5s * 2^0 = 5s | 5s | 5s |
| 2nd retry | 5s * 2^1 = 10s | 10s | 15s |
| 3rd retry | 5s * 2^2 = 20s | 20s | 35s |
| 4th retry | 5s * 2^3 = 40s | 40s | 75s |
| 5th retry | 5s * 2^4 = 80s → capped | 60s | 135s |
| Exhausted | - | - | 135s total |

---

## Idempotency in Action

### Scenario: Duplicate Task Submission

**Initial Task:**
```
Task A:
  businessKey: md5("PUBMED_mindate=2024-01-01_maxdate=2024-01-31_retstart=0_retmax=1000")
  status: SUCCEEDED
  recordsFetched: 1000
```

**Duplicate Submission (e.g., retry after network failure):**
```
Task B:
  businessKey: md5("PUBMED_mindate=2024-01-01_maxdate=2024-01-31_retstart=0_retmax=1000")
  status: PENDING
```

**Execution Logic:**
```java
// Task B picked for execution
Optional<BatchTask> existing = taskRepository.findByBusinessKey(
    "md5(...)"  // Same business key
);

if (existing.isPresent() && existing.get().status() == TaskStatus.SUCCEEDED) {
    log.info("Task {} already succeeded, skipping", taskB.id());
    return;  // Skip execution
}
```

**Result**: Task B skipped, no duplicate API call made.

---

## Summary

**Key Workflows:**

1. **Plan Creation**: Validate → Generate Slices → Persist → Emit Event
2. **Task Generation**: Listen to PlanCreatedEvent → Generate Tasks for each Slice
3. **Task Execution**: Pick PENDING → Render params → HTTP call → Handle response
4. **Retry Logic**: Calculate delay → Re-queue as PENDING → Execute again
5. **Plan Completion**: Monitor Task statuses → Mark Plan COMPLETED/FAILED

**Design Patterns:**

- **Event-Driven**: PlanCreatedEvent triggers Task generation
- **Idempotency**: Business key prevents duplicate work
- **Retry with Backoff**: Exponential delays for transient failures
- **State Machine**: Clear state transitions for Plans and Tasks

**See Also:**
- [business-concepts.md](business-concepts.md) for Plan/Task definitions
- [provenance-config-system.md](provenance-config-system.md) for configuration details
