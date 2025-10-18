# Batch Processing Logging Guide

**Version**: 1.0.0 | **Last Updated**: 2025-01-17 | **Status**: Phase 6 Complete (T074)

This guide provides patterns and best practices for logging batch processing operations in Papertrace microservices.

---

## Overview

Batch processing requires careful logging to balance observability with performance. This guide implements **FR-010 (Batch Processing Logging)** requirements:

- **Summary at INFO level**: High-level progress and results
- **Details at DEBUG level**: Individual item processing
- **Failures at WARN/ERROR level**: Item-level and batch-level errors

---

## Core Principles

### 1. Log Levels by Scope

| Level | Use For | Example |
|-------|---------|---------|
| **INFO** | Batch start, completion, checkpoints | Batch START, COMPLETE, CHECKPOINT |
| **DEBUG** | Progress updates, item-level details | Individual item processing |
| **WARN** | Recoverable item failures | Validation error on single item |
| **ERROR** | Batch-level failures | Database connection lost, out of memory |

### 2. Correlation ID for Batch Tracking

**Always** use a correlation ID to track all logs for a single batch:

```java
String correlationId = "batch-" + UUID.randomUUID();
// OR use business-meaningful ID:
String correlationId = "batch-ingest-" + LocalDate.now() + "-" + batchNumber;
```

Benefits:
- Search logs by correlation ID to see complete batch history
- Distinguish between concurrent batches
- Track batch across multiple services (distributed batches)

### 3. Sampling for High-Volume Batches

For batches processing >1000 items, log progress **periodically** rather than per-item:

- **Every 100 items** for batches of 1,000-10,000 items
- **Every 5%** for batches of >10,000 items
- **Time-based**: Every 10 seconds for long-running batches

---

## Using BatchProcessingLogger

### Basic Usage

```java
import com.patra.common.logging.BatchProcessingLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DataIngestOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DataIngestOrchestrator.class);
    private final BatchProcessingLogger batchLogger = new BatchProcessingLogger(log);

    public void processBatch(List<Article> articles) {
        String correlationId = "batch-" + UUID.randomUUID();
        long startTime = System.currentTimeMillis();

        // 1. Log batch start (INFO)
        batchLogger.logStart(correlationId, "ArticleIngest", articles.size());

        int successCount = 0;
        int failureCount = 0;

        for (int i = 0; i < articles.size(); i++) {
            Article article = articles.get(i);

            try {
                processArticle(article);
                successCount++;

                // 2. Log progress periodically (DEBUG)
                if ((i + 1) % 100 == 0) {  // Every 100 items
                    batchLogger.logProgress(correlationId, i + 1, articles.size());
                }
            } catch (Exception e) {
                failureCount++;

                // 3. Log individual item failure (WARN)
                batchLogger.logItemFailure(correlationId, article.getId(), e);
            }
        }

        // 4. Log batch completion with stats (INFO)
        Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
        batchLogger.logComplete(correlationId, successCount, failureCount, duration);
    }
}
```

**Log Output:**
```
INFO  Batch START [correlationId=batch-abc-123] operation=ArticleIngest totalItems=500
DEBUG Batch PROGRESS [correlationId=batch-abc-123] processed=100/500 (20.0%)
DEBUG Batch PROGRESS [correlationId=batch-abc-123] processed=200/500 (40.0%)
WARN  Batch ITEM FAILURE [correlationId=batch-abc-123] itemId=article-456: Validation error: Missing required field 'title'
DEBUG Batch PROGRESS [correlationId=batch-abc-123] processed=300/500 (60.0%)
DEBUG Batch PROGRESS [correlationId=batch-abc-123] processed=400/500 (80.0%)
DEBUG Batch PROGRESS [correlationId=batch-abc-123] processed=500/500 (100.0%)
INFO  Batch COMPLETE [correlationId=batch-abc-123] success=495 failure=5 duration=45s throughput=11.1/s
```

---

## Patterns by Batch Size

### Small Batches (<100 items)

**Pattern**: Log start and completion only

```java
batchLogger.logStart(correlationId, "SmallBatch", items.size());

// Process items...

batchLogger.logComplete(correlationId, successCount, failureCount, duration);
```

**Rationale**: Overhead of progress logging exceeds benefit for small batches

---

### Medium Batches (100-1,000 items)

**Pattern**: Log progress every 100 items

```java
for (int i = 0; i < items.size(); i++) {
    processItem(items.get(i));

    if ((i + 1) % 100 == 0) {
        batchLogger.logProgress(correlationId, i + 1, items.size());
    }
}
```

---

### Large Batches (1,000-10,000 items)

**Pattern**: Log progress every 5% or every 10 seconds

```java
int progressInterval = Math.max(items.size() / 20, 100); // 5% or min 100
long lastProgressLog = System.currentTimeMillis();

for (int i = 0; i < items.size(); i++) {
    processItem(items.get(i));

    long now = System.currentTimeMillis();
    boolean intervalReached = (i + 1) % progressInterval == 0;
    boolean timeReached = (now - lastProgressLog) > 10_000; // 10 seconds

    if (intervalReached || timeReached) {
        batchLogger.logProgress(correlationId, i + 1, items.size());
        lastProgressLog = now;
    }
}
```

---

### Very Large Batches (>10,000 items)

**Pattern**: Use checkpoints for milestones

```java
batchLogger.logStart(correlationId, "MassiveImport", items.size());

int quarter = items.size() / 4;
long startTime = System.currentTimeMillis();

for (int i = 0; i < items.size(); i++) {
    processItem(items.get(i));

    // Checkpoint at 25%, 50%, 75%
    if ((i + 1) == quarter) {
        Duration elapsed = Duration.ofMillis(System.currentTimeMillis() - startTime);
        batchLogger.logCheckpoint(correlationId, "25% complete", i + 1, elapsed);
    } else if ((i + 1) == quarter * 2) {
        Duration elapsed = Duration.ofMillis(System.currentTimeMillis() - startTime);
        batchLogger.logCheckpoint(correlationId, "50% complete (halfway)", i + 1, elapsed);
    } else if ((i + 1) == quarter * 3) {
        Duration elapsed = Duration.ofMillis(System.currentTimeMillis() - startTime);
        batchLogger.logCheckpoint(correlationId, "75% complete", i + 1, elapsed);
    }
}
```

---

## Handling Failures

### Item-Level Failures (Recoverable)

**Pattern**: Log at WARN, continue processing

```java
try {
    processItem(item);
    successCount++;
} catch (ValidationException e) {
    // Expected failure - bad data
    batchLogger.logItemFailure(correlationId, item.getId(), e);
    failureCount++;
    // Continue with next item
}
```

**Use When**:
- Validation errors (bad data quality)
- Business rule violations
- Individual item transformation failures
- Partial data issues

---

### Batch-Level Failures (Unrecoverable)

**Pattern**: Log at ERROR, stop processing

```java
try {
    for (Item item : items) {
        processItem(item);
        processedCount++;
    }
} catch (SQLException e) {
    // Catastrophic failure - database down
    batchLogger.logBatchFailure(correlationId, "DataImport", processedCount, e);
    throw e; // Stop processing
}
```

**Use When**:
- Database connection lost
- Out of memory errors
- External service unavailable
- File system errors

---

## Retry Patterns

### With Exponential Backoff

```java
int maxAttempts = 3;
int attemptNumber = 0;

while (attemptNumber < maxAttempts) {
    attemptNumber++;

    try {
        processItem(item);
        successCount++;
        break; // Success, exit retry loop

    } catch (TransientException e) {
        if (attemptNumber < maxAttempts) {
            batchLogger.logRetry(correlationId, item.getId(), attemptNumber, maxAttempts, e);
            Thread.sleep((long) Math.pow(2, attemptNumber) * 1000); // Exponential backoff
        } else {
            batchLogger.logItemFailure(correlationId, item.getId(), e);
            failureCount++;
        }
    }
}
```

---

## Performance Considerations

### 1. Avoid Logging Inside Tight Loops

**Bad** ❌:
```java
for (Item item : items) {
    log.debug("Processing item: {}", item.getId()); // Every item!
    processItem(item);
}
```

**Good** ✅:
```java
for (int i = 0; i < items.size(); i++) {
    processItem(items.get(i));

    // Only log every 100 items
    if ((i + 1) % 100 == 0 && log.isDebugEnabled()) {
        batchLogger.logProgress(correlationId, i + 1, items.size());
    }
}
```

### 2. Use Guard Clauses for DEBUG Logging

```java
if (log.isDebugEnabled()) {
    // Expensive string formatting only when DEBUG enabled
    log.debug("Batch processing details: {}", buildDetailedReport(items));
}
```

### 3. Aggregate Statistics, Don't Log Per-Item

**Bad** ❌:
```java
items.forEach(item -> log.info("Item {} processed successfully", item.getId()));
```

**Good** ✅:
```java
// Process all items first
items.forEach(this::processItem);

// Log aggregated statistics
log.info("Batch complete: {} items processed in {}ms", items.size(), duration);
```

---

## Integration with Trace Context

Batch logging automatically includes trace context via MDC:

```java
// Set correlation ID in MDC
MDC.put("correlationId", correlationId);

try {
    batchLogger.logStart(correlationId, "BatchJob", items.size());
    // Process items...
    batchLogger.logComplete(correlationId, successCount, failureCount, duration);
} finally {
    MDC.remove("correlationId");
}
```

**Log Output** (with trace context):
```
INFO  [traceId=xyz-789][correlationId=batch-abc-123][service=patra-ingest][layer=app] Batch START operation=ArticleIngest totalItems=500
```

---

## Scheduled Job Integration (XXL-Job)

```java
@XxlJob("articleIngestJob")
public void executeArticleIngest() {
    String correlationId = "batch-article-" + LocalDate.now();

    // XxlJobTraceContextDecorator sets trace context automatically
    batchLogger.logStart(correlationId, "ArticleIngestJob", totalCount);

    try {
        // Batch processing...
        batchLogger.logComplete(correlationId, successCount, failureCount, duration);
    } catch (Exception e) {
        batchLogger.logBatchFailure(correlationId, "ArticleIngestJob", processedCount, e);
        throw e;
    }
}
```

---

## Monitoring & Alerting

### Key Metrics to Track

Based on batch logs, monitor:

1. **Throughput**: Items/second from COMPLETE logs
2. **Failure Rate**: failure/(success+failure) ratio
3. **Duration Trends**: Track if batches slow down over time
4. **Item Failure Patterns**: Group ITEM FAILURE logs by error type

### Alert Conditions

- **High Failure Rate**: failure > 10% of total items
- **Slow Processing**: throughput < expected baseline
- **Batch Failures**: Any BATCH FAILURE logs (ERROR level)
- **Stuck Batches**: No CHECKPOINT logs for >30 minutes

---

## Best Practices Summary

1. ✅ **Always use correlation IDs** for batch tracking
2. ✅ **Log start and completion at INFO** for all batches
3. ✅ **Sample progress logs** for large batches (don't log every item)
4. ✅ **Log item failures at WARN**, batch failures at ERROR
5. ✅ **Include throughput in completion logs** for performance monitoring
6. ✅ **Use checkpoints for long-running batches** (>10 minutes)
7. ✅ **Guard DEBUG logs** with `log.isDebugEnabled()`
8. ✅ **Aggregate statistics** instead of per-item logging
9. ✅ **Propagate correlation ID to downstream services** (distributed batches)
10. ✅ **Set correlation ID in MDC** for automatic inclusion in all logs

---

## Related Documentation

- [Common Patterns Guide](./common-patterns.md) - General logging patterns
- [Trace Context Troubleshooting](./trace-context-troubleshooting.md) - Debugging trace propagation
- [Log Level Guidelines](./log-level-guidelines.md) - When to use each log level

---

**Last Updated**: 2025-01-17 | **Phase**: 6 (User Story 4 - Consistent Logging) | **Task**: T074
