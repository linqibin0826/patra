# Temporal Slicing Strategies

## Overview

Temporal slicing is a core pattern in Papertrace that breaks large time windows into manageable chunks for parallel processing. This enables efficient harvesting of literature data from external APIs (PubMed, EPMC, Crossref) while respecting rate limits and avoiding timeouts.

**Core Problem**: Harvesting all literature from 2024-01-01 to 2024-12-31 in one API call would:
- Exceed API result limits (max 10,000 results per call)
- Cause timeouts on large datasets
- Make retry difficult if failure occurs
- Prevent parallel processing

**Solution**: Divide the window into smaller slices (days, weeks, months) and process each slice as an independent Task.

---

## Slicing Concepts

### Key Terms

| Term | Definition | Example |
|------|------------|---------|
| **Time Window** | The overall period to harvest | `2024-01-01` to `2024-12-31` |
| **Slice** | A subdivision of the time window | `2024-01-01` to `2024-01-07` |
| **Granularity** | The size of each slice | `DAY`, `WEEK`, `MONTH` |
| **Slice Unit** | The step size for slicing | `7 days`, `1 month` |
| **Slice Count** | Total number of slices generated | `52 slices` (weekly for 1 year) |

### Slice Representation

```java
public record Slice(
    SliceId id,
    BatchPlanId planId,
    LocalDate startDate,
    LocalDate endDate,
    SliceStatus status,
    int estimatedCount,
    int actualCount
) {
    public Duration duration() {
        return Duration.between(
            startDate.atStartOfDay(),
            endDate.atStartOfDay()
        );
    }

    public boolean isComplete() {
        return status == SliceStatus.COMPLETED;
    }
}
```

---

## Slicing Granularities

### 1. Daily Slicing

**Use Case**: High-volume sources or when precise control needed

**Configuration**:
```java
SlicingStrategy strategy = SlicingStrategy.builder()
    .granularity(SliceGranularity.DAY)
    .sliceUnit(1)  // 1 day per slice
    .build();
```

**Example**:
```
Input Window: 2024-01-01 to 2024-01-31

Generated Slices (31 slices):
- Slice 1: 2024-01-01 to 2024-01-01
- Slice 2: 2024-01-02 to 2024-01-02
- Slice 3: 2024-01-03 to 2024-01-03
...
- Slice 31: 2024-01-31 to 2024-01-31
```

**Pros**:
- Maximum parallelism
- Fine-grained retry control
- Easy to re-process failed days

**Cons**:
- Many tasks (high overhead)
- May exceed task queue limits
- API call overhead per slice

**Best For**: PubMed daily updates, high-frequency harvesting

---

### 2. Weekly Slicing

**Use Case**: Balanced approach for most scenarios

**Configuration**:
```java
SlicingStrategy strategy = SlicingStrategy.builder()
    .granularity(SliceGranularity.WEEK)
    .sliceUnit(1)  // 1 week per slice
    .build();
```

**Example**:
```
Input Window: 2024-01-01 to 2024-12-31

Generated Slices (52 slices):
- Slice 1: 2024-01-01 to 2024-01-07
- Slice 2: 2024-01-08 to 2024-01-14
- Slice 3: 2024-01-15 to 2024-01-21
...
- Slice 52: 2024-12-23 to 2024-12-31 (partial week)
```

**Pros**:
- Good balance of parallelism and overhead
- Manageable task count
- Works well with most API rate limits

**Cons**:
- May still have too many slices for multi-year windows

**Best For**: Monthly harvests, backfill operations, general use

---

### 3. Monthly Slicing

**Use Case**: Large time windows, low-frequency harvesting

**Configuration**:
```java
SlicingStrategy strategy = SlicingStrategy.builder()
    .granularity(SliceGranularity.MONTH)
    .sliceUnit(1)  // 1 month per slice
    .build();
```

**Example**:
```
Input Window: 2020-01-01 to 2024-12-31

Generated Slices (60 slices):
- Slice 1: 2020-01-01 to 2020-01-31
- Slice 2: 2020-02-01 to 2020-02-29 (leap year)
- Slice 3: 2020-03-01 to 2020-03-31
...
- Slice 60: 2024-12-01 to 2024-12-31
```

**Pros**:
- Fewer tasks (low overhead)
- Good for historical backfills
- Aligns with natural time boundaries

**Cons**:
- Less parallelism
- Large slices may hit API limits
- Variable slice sizes (28-31 days)

**Best For**: Historical backfills, multi-year windows, quarterly harvests

---

### 4. Custom Multi-Unit Slicing

**Use Case**: Custom step sizes (e.g., 2 weeks, 3 months)

**Configuration**:
```java
// 2-week slices
SlicingStrategy strategy = SlicingStrategy.builder()
    .granularity(SliceGranularity.WEEK)
    .sliceUnit(2)  // 2 weeks per slice
    .build();

// 3-month slices
SlicingStrategy strategy = SlicingStrategy.builder()
    .granularity(SliceGranularity.MONTH)
    .sliceUnit(3)  // 3 months per slice
    .build();
```

**Example (2-week slices)**:
```
Input Window: 2024-01-01 to 2024-12-31

Generated Slices (26 slices):
- Slice 1: 2024-01-01 to 2024-01-14
- Slice 2: 2024-01-15 to 2024-01-28
- Slice 3: 2024-01-29 to 2024-02-11
...
- Slice 26: 2024-12-16 to 2024-12-31
```

---

## Slice Generation Algorithm

### Core Logic

```java
public class SliceGenerator {

    public List<Slice> generate(
        BatchPlanId planId,
        LocalDate startDate,
        LocalDate endDate,
        SlicingStrategy strategy
    ) {
        List<Slice> slices = new ArrayList<>();
        LocalDate currentStart = startDate;
        int sliceIndex = 0;

        while (currentStart.isBefore(endDate) || currentStart.isEqual(endDate)) {
            LocalDate currentEnd = calculateSliceEnd(
                currentStart,
                endDate,
                strategy
            );

            Slice slice = new Slice(
                SliceId.generate(),
                planId,
                currentStart,
                currentEnd,
                SliceStatus.PENDING,
                0,  // Estimated count (populated later)
                0   // Actual count
            );

            slices.add(slice);

            // Move to next slice
            currentStart = currentEnd.plusDays(1);
            sliceIndex++;
        }

        return slices;
    }

    private LocalDate calculateSliceEnd(
        LocalDate sliceStart,
        LocalDate windowEnd,
        SlicingStrategy strategy
    ) {
        LocalDate calculatedEnd = switch (strategy.granularity()) {
            case DAY -> sliceStart.plusDays(strategy.sliceUnit());
            case WEEK -> sliceStart.plusWeeks(strategy.sliceUnit());
            case MONTH -> sliceStart.plusMonths(strategy.sliceUnit());
        };

        // Subtract 1 day because slices are inclusive on both ends
        calculatedEnd = calculatedEnd.minusDays(1);

        // Don't exceed window end
        return calculatedEnd.isAfter(windowEnd) ? windowEnd : calculatedEnd;
    }
}
```

### Edge Cases Handled

1. **Partial Last Slice**: Last slice may be shorter than slice unit
   ```
   Window: 2024-01-01 to 2024-01-10 (10 days)
   Strategy: Weekly (7 days)

   Slice 1: 2024-01-01 to 2024-01-07 (7 days)
   Slice 2: 2024-01-08 to 2024-01-10 (3 days) ← Partial
   ```

2. **Leap Year Handling**: Month slicing accounts for February 29
   ```
   Slice: 2024-02-01 to 2024-02-29 (29 days)
   Slice: 2023-02-01 to 2023-02-28 (28 days)
   ```

3. **Same Start/End Date**: Single-day window
   ```
   Window: 2024-01-15 to 2024-01-15
   Slice 1: 2024-01-15 to 2024-01-15 (1 slice)
   ```

4. **Month Boundary Alignment**: Monthly slices aligned to calendar months
   ```
   Window: 2024-01-15 to 2024-03-20

   Slice 1: 2024-01-15 to 2024-02-14 (1 month from start)
   Slice 2: 2024-02-15 to 2024-03-14 (1 month)
   Slice 3: 2024-03-15 to 2024-03-20 (partial)
   ```

---

## Slice Estimation

### Purpose

Estimate the number of results each slice will return to optimize task allocation and detect anomalies.

### Estimation Strategies

#### 1. Uniform Distribution (Simplest)

**Assumption**: Results are evenly distributed across time.

```java
public class UniformSliceEstimator {

    public void estimate(
        List<Slice> slices,
        int totalEstimatedResults,
        Duration totalDuration
    ) {
        for (Slice slice : slices) {
            Duration sliceDuration = slice.duration();
            double proportion = (double) sliceDuration.toDays() / totalDuration.toDays();
            int estimate = (int) (totalEstimatedResults * proportion);
            slice.setEstimatedCount(estimate);
        }
    }
}
```

**Example**:
```
Total Window: 365 days
Total Estimated Results: 36,500 (100 per day)

Weekly Slices (7 days each):
- Slice 1 estimate: 36,500 * (7/365) = 700
- Slice 2 estimate: 36,500 * (7/365) = 700
...
```

#### 2. Historical-Based Estimation (More Accurate)

**Assumption**: Past patterns predict future results.

```java
public class HistoricalSliceEstimator {
    private final SliceHistoryPort historyPort;

    public void estimate(List<Slice> slices, ProvenanceCode provenanceCode) {
        for (Slice slice : slices) {
            // Find historical data for same date range
            Optional<SliceHistory> history = historyPort.findByDateRange(
                provenanceCode,
                slice.startDate(),
                slice.endDate()
            );

            int estimate = history.map(SliceHistory::actualCount)
                .orElse(calculateFallbackEstimate(slice));

            slice.setEstimatedCount(estimate);
        }
    }
}
```

#### 3. API-Based Estimation (Most Accurate)

**Assumption**: Query the API's count endpoint before harvesting.

```java
public class ApiBasedSliceEstimator {
    private final ExpressionRenderPort expressionRenderPort;
    private final ExternalApiPort externalApiPort;

    public void estimate(Slice slice, ProvenanceCode provenanceCode) {
        // Render count query
        Map<String, Object> params = expressionRenderPort.renderCountQuery(
            provenanceCode,
            slice.startDate(),
            slice.endDate()
        );

        // Call API count endpoint
        int actualCount = externalApiPort.getCount(params);
        slice.setEstimatedCount(actualCount);
    }
}
```

**Example (PubMed)**:
```
Query: esearch.fcgi?term=2024/01/01:2024/01/07[pdat]&retmode=json&retmax=0

Response:
{
  "esearchresult": {
    "count": "1250"  ← Use this as estimate
  }
}
```

---

## Choosing Slicing Strategy

### Decision Matrix

| Scenario | Recommended Granularity | Slice Unit | Reasoning |
|----------|------------------------|------------|-----------|
| **Daily updates (current month)** | DAY | 1 | Fine-grained, fast retry |
| **Monthly backfill (1 year)** | WEEK | 1 | Balanced parallelism |
| **Historical backfill (5+ years)** | MONTH | 1 or 3 | Fewer tasks, manageable overhead |
| **High-volume source (>100k/year)** | DAY or WEEK | 1 | Avoid API limits |
| **Low-volume source (<10k/year)** | MONTH | 1 | Minimize overhead |
| **Rate-limited API** | WEEK or MONTH | 1 | Respect rate limits |

### Configuration Example

```java
public class SlicingStrategyFactory {

    public static SlicingStrategy forProvenance(
        ProvenanceCode provenanceCode,
        Duration windowDuration,
        int estimatedResults
    ) {
        // High-volume sources: daily or weekly
        if (estimatedResults > 100_000) {
            return SlicingStrategy.builder()
                .granularity(SliceGranularity.DAY)
                .sliceUnit(1)
                .build();
        }

        // Large time windows: monthly
        if (windowDuration.toDays() > 365 * 3) {
            return SlicingStrategy.builder()
                .granularity(SliceGranularity.MONTH)
                .sliceUnit(1)
                .build();
        }

        // Default: weekly
        return SlicingStrategy.builder()
            .granularity(SliceGranularity.WEEK)
            .sliceUnit(1)
            .build();
    }
}
```

---

## Slice Lifecycle

### State Transitions

```
PENDING → RUNNING → COMPLETED
    ↓         ↓
    └────→ FAILED ──→ RETRYING → RUNNING → COMPLETED
              ↓                      ↓
              └──────────────────→ ABANDONED
```

### State Definitions

| State | Description | Next Actions |
|-------|-------------|--------------|
| **PENDING** | Slice created, not yet processed | Start processing |
| **RUNNING** | Tasks for this slice are executing | Monitor progress |
| **COMPLETED** | All tasks succeeded, results harvested | Mark slice complete |
| **FAILED** | Task(s) failed, retry count < max | Retry with backoff |
| **RETRYING** | Retry in progress | Monitor retry |
| **ABANDONED** | Exceeded max retries | Manual intervention |

### Slice State Management

```java
@Slf4j
public class SliceStateManager {

    public void transitionTo(Slice slice, SliceStatus newStatus) {
        SliceStatus oldStatus = slice.status();

        // Validate transition
        if (!isValidTransition(oldStatus, newStatus)) {
            throw new IllegalStateTransitionException(
                String.format("Cannot transition from %s to %s", oldStatus, newStatus)
            );
        }

        slice.setStatus(newStatus);
        log.info("Slice {} transitioned: {} -> {}",
            slice.id(), oldStatus, newStatus);

        // Emit domain event
        DomainEventPublisher.publish(new SliceStateChangedEvent(
            slice.id(),
            oldStatus,
            newStatus,
            Instant.now()
        ));
    }

    private boolean isValidTransition(SliceStatus from, SliceStatus to) {
        return switch (from) {
            case PENDING -> to == SliceStatus.RUNNING || to == SliceStatus.FAILED;
            case RUNNING -> to == SliceStatus.COMPLETED || to == SliceStatus.FAILED;
            case FAILED -> to == SliceStatus.RETRYING || to == SliceStatus.ABANDONED;
            case RETRYING -> to == SliceStatus.RUNNING || to == SliceStatus.ABANDONED;
            case COMPLETED, ABANDONED -> false;  // Terminal states
        };
    }
}
```

---

## Parallel Slice Processing

### Concurrency Control

**Problem**: Processing 52 slices in parallel may overwhelm the system.

**Solution**: Use a task queue with concurrency limits.

```java
@Configuration
public class SliceProcessingConfig {

    @Bean
    public ThreadPoolTaskExecutor sliceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);      // 5 concurrent slices
        executor.setMaxPoolSize(10);      // Max 10 slices
        executor.setQueueCapacity(100);   // Queue up to 100 slices
        executor.setThreadNamePrefix("slice-processor-");
        executor.initialize();
        return executor;
    }
}
```

### Slice Processing Orchestrator

```java
@Service
@RequiredArgsConstructor
public class SliceProcessingOrchestrator {
    private final SliceExecutor sliceExecutor;
    private final TaskCreationCoordinator taskCreationCoordinator;
    private final SliceStateManager sliceStateManager;

    public void processSlices(BatchPlanId planId, List<Slice> slices) {
        for (Slice slice : slices) {
            sliceExecutor.submit(() -> processSlice(planId, slice));
        }
    }

    @Async("sliceExecutor")
    private void processSlice(BatchPlanId planId, Slice slice) {
        try {
            sliceStateManager.transitionTo(slice, SliceStatus.RUNNING);

            // Create tasks for this slice
            List<BatchTask> tasks = taskCreationCoordinator.createTasksForSlice(
                planId,
                slice
            );

            // Wait for all tasks to complete
            waitForTaskCompletion(tasks);

            sliceStateManager.transitionTo(slice, SliceStatus.COMPLETED);

        } catch (Exception e) {
            log.error("Slice processing failed: sliceId={}", slice.id(), e);
            sliceStateManager.transitionTo(slice, SliceStatus.FAILED);
        }
    }
}
```

---

## Troubleshooting Slicing Issues

### Issue 1: Too Many Slices Generated

**Symptoms**:
- Plan generates 1000+ slices
- Task queue overflows
- High database load

**Root Cause**: Granularity too fine for the time window.

**Solution**:
```java
// Before (365 daily slices for 1 year)
SlicingStrategy.builder()
    .granularity(SliceGranularity.DAY)
    .sliceUnit(1)
    .build();

// After (52 weekly slices)
SlicingStrategy.builder()
    .granularity(SliceGranularity.WEEK)
    .sliceUnit(1)
    .build();
```

---

### Issue 2: Slice Estimates Way Off

**Symptoms**:
- Estimated 100 results, actual 10,000
- Tasks fail with "too many results" error

**Root Cause**: Uniform distribution doesn't match real data.

**Solution**: Use historical-based or API-based estimation.

```java
// Use API count before harvesting
ApiBasedSliceEstimator estimator = new ApiBasedSliceEstimator();
estimator.estimate(slice, provenanceCode);

if (slice.estimatedCount() > 10_000) {
    log.warn("Slice {} has high estimate, consider re-slicing", slice.id());
    // Option: Further subdivide this slice
    List<Slice> subSlices = subdivideSlice(slice, SliceGranularity.DAY);
}
```

---

### Issue 3: Slices Complete Out of Order

**Symptoms**:
- Slice 10 completes before Slice 1
- Results processed non-chronologically

**Root Cause**: This is expected with parallel processing.

**Solution**: If order matters, add dependencies.

```java
public class OrderedSliceProcessor {

    public void processInOrder(List<Slice> slices) {
        for (Slice slice : slices) {
            // Process synchronously (no parallelism)
            processSliceSync(slice);
        }
    }
}
```

Or use a completion barrier:
```java
CountDownLatch latch = new CountDownLatch(slices.size());
for (Slice slice : slices) {
    executor.submit(() -> {
        processSlice(slice);
        latch.countDown();
    });
}
latch.await();  // Wait for all slices to complete
```

---

## Best Practices

### 1. Start Conservative, Optimize Later

**Initial Strategy**: Use monthly or weekly slicing.

**Optimization**: If slices complete too quickly, increase granularity.

### 2. Monitor Slice Performance

**Metrics to Track**:
- Average slice duration
- Slice failure rate
- Actual vs estimated counts

**Use Micrometer**:
```java
@Component
public class SliceMetrics {
    private final MeterRegistry registry;

    public void recordSliceCompletion(Slice slice, Duration duration) {
        registry.timer("slice.processing.time",
            "provenance", slice.provenanceCode(),
            "granularity", slice.granularity()
        ).record(duration);

        registry.gauge("slice.estimate.accuracy",
            (double) slice.actualCount() / slice.estimatedCount()
        );
    }
}
```

### 3. Handle Partial Failures Gracefully

**Problem**: 51 slices succeed, 1 fails.

**Solution**: Mark Plan as PARTIALLY_COMPLETED.

```java
public class PlanCompletionChecker {

    public PlanStatus checkCompletion(List<Slice> slices) {
        long completed = slices.stream()
            .filter(Slice::isComplete)
            .count();

        if (completed == slices.size()) {
            return PlanStatus.COMPLETED;
        } else if (completed > 0) {
            return PlanStatus.PARTIALLY_COMPLETED;
        } else {
            return PlanStatus.FAILED;
        }
    }
}
```

---

## Summary

**Temporal Slicing Benefits**:
- ✅ Parallel processing (faster harvesting)
- ✅ Fine-grained retry control
- ✅ Avoid API result limits
- ✅ Better progress tracking

**Key Decisions**:
1. **Granularity**: Day/Week/Month based on volume and window size
2. **Estimation**: Use API-based for accuracy
3. **Concurrency**: Limit parallel slices to avoid overload
4. **Monitoring**: Track slice performance metrics

**See Also**:
- [plan-task-workflow.md](plan-task-workflow.md) - How slices create tasks
- [business-concepts.md](business-concepts.md) - Slice domain model
- [common-patterns.md](common-patterns.md) - Slice generation patterns
