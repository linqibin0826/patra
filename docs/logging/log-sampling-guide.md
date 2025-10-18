# Log Sampling Guide

**Feature**: Enhanced Logging System | **Phase**: 6 - User Story 4 | **Date**: 2025-10-17

## Overview

This guide explains how to configure and use log sampling to prevent log flooding in high-throughput scenarios while maintaining critical log visibility.

## Purpose

The `SamplingFilter` prevents DEBUG and TRACE log flooding by intelligently sampling high-frequency logs when they exceed configurable thresholds. This edge case handling ensures system stability in production without losing critical diagnostics.

**Key Points:**
- INFO, WARN, ERROR logs are **NEVER** sampled (always logged)
- Only DEBUG and TRACE logs are subject to sampling
- Sampling kicks in automatically when log rate exceeds threshold
- No impact on low-frequency logging scenarios

---

## When to Use Log Sampling

### ✅ Good Use Cases

1. **High-throughput batch processing** - Logging every record in a 10,000-item batch
2. **API rate limiting detection** - DEBUG logs for each rate limit check
3. **Polling loops** - TRACE logs in frequent health check loops
4. **Performance monitoring** - Detailed timing logs at DEBUG level
5. **Development diagnostics** - Verbose TRACE logs that may flood in production

### ❌ Bad Use Cases

1. **Critical business events** - Use INFO level instead (never sampled)
2. **Error scenarios** - Use WARN/ERROR levels (never sampled)
3. **Security events** - Use WARN level (never sampled)
4. **Compliance logs** - Use INFO level for complete audit trail

---

## Configuration

### Basic Configuration (Logback XML)

Add the `SamplingFilter` to your appenders in `logback-spring.xml`:

```xml
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <!-- Add sampling filter -->
        <filter class="com.patra.starter.logging.filter.SamplingFilter">
            <thresholdLogsPerSecond>100</thresholdLogsPerSecond>
            <samplingRate>10</samplingRate>
            <windowDuration>PT1S</windowDuration>
        </filter>

        <encoder>
            <pattern>%d{yyyy-MM-dd'T'HH:mm:ss.SSSXXX} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE" />
    </root>
</configuration>
```

### Configuration Properties

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `thresholdLogsPerSecond` | integer | 100 | Maximum logs/second before sampling activates |
| `samplingRate` | integer | 10 | Keep 1 out of every N logs (10 = 10% retention) |
| `windowDuration` | duration | PT1S | Time window for rate calculation (ISO-8601 format) |

### Configuration Tuning by Environment

**Development Environment:**
```xml
<!-- Log everything - no sampling -->
<thresholdLogsPerSecond>10000</thresholdLogsPerSecond>
<samplingRate>1</samplingRate>
```

**Staging Environment:**
```xml
<!-- Moderate sampling for realistic testing -->
<thresholdLogsPerSecond>200</thresholdLogsPerSecond>
<samplingRate>5</samplingRate>
```

**Production Environment:**
```xml
<!-- Aggressive sampling to protect system stability -->
<thresholdLogsPerSecond>100</thresholdLogsPerSecond>
<samplingRate>10</samplingRate>
```

---

## Behavior Examples

### Scenario 1: Normal Load (Below Threshold)

**Config**: Threshold = 100 logs/sec, Sampling Rate = 10

```java
// Application generates 50 DEBUG logs per second
for (int i = 0; i < 50; i++) {
    log.debug("Processing record {}", i);
}
```

**Result**: All 50 logs written (below threshold, no sampling)

### Scenario 2: High Load (Above Threshold)

**Config**: Threshold = 100 logs/sec, Sampling Rate = 10

```java
// Application generates 500 DEBUG logs per second
for (int i = 0; i < 500; i++) {
    log.debug("Processing record {}", i);
}
```

**Result**: Only ~50 logs written (10% of 500, sampled to stay near threshold)

### Scenario 3: Mixed Log Levels

**Config**: Threshold = 100 logs/sec, Sampling Rate = 10

```java
// Application generates 500 mixed logs per second
for (int i = 0; i < 500; i++) {
    if (i % 50 == 0) {
        log.info("Checkpoint: processed {} records", i); // NEVER sampled
    }
    if (error) {
        log.error("Failed to process record {}", i); // NEVER sampled
    }
    log.debug("Processing record {}", i); // May be sampled
}
```

**Result**:
- All 10 INFO logs written (100% retention)
- All ERROR logs written (100% retention)
- ~49 DEBUG logs written (10% of 490, sampled)

---

## Implementation Patterns

### Pattern 1: Batch Processing with Checkpoints

```java
@Slf4j
public class BatchProcessor {

    public void processBatch(List<Record> records) {
        log.info("Starting batch processing: {} records", records.size()); // Always logged

        int processed = 0;
        for (Record record : records) {
            // Detailed DEBUG logging (may be sampled in high-throughput)
            log.debug("Processing record: id={}, type={}", record.getId(), record.getType());

            processRecord(record);
            processed++;

            // Periodic INFO checkpoints (always logged)
            if (processed % 100 == 0) {
                log.info("Batch progress: {}/{} records processed", processed, records.size());
            }
        }

        log.info("Batch processing completed: {} records", processed); // Always logged
    }
}
```

**Benefits:**
- INFO checkpoints always logged for monitoring
- DEBUG details available for troubleshooting (sampled under high load)
- No risk of log flooding

### Pattern 2: High-Frequency Polling Loop

```java
@Slf4j
public class HealthCheckPoller {

    public void pollHealth() {
        while (running) {
            // TRACE logging (heavily sampled in production)
            log.trace("Executing health check poll");

            HealthStatus status = checkHealth();

            // Only log state changes at INFO level (always logged)
            if (!status.equals(previousStatus)) {
                log.info("Health status changed: {} -> {}", previousStatus, status);
                previousStatus = status;
            }

            Thread.sleep(1000); // Poll every second
        }
    }
}
```

**Benefits:**
- TRACE polls sampled to prevent flooding
- State changes always captured at INFO level
- Minimal log volume, maximum signal

### Pattern 3: Performance Monitoring

```java
@Slf4j
public class PerformanceMonitor {

    public void monitorOperation(Operation op) {
        long startTime = System.currentTimeMillis();

        // DEBUG timing (may be sampled)
        log.debug("Operation started: type={}, id={}", op.getType(), op.getId());

        try {
            op.execute();
            long duration = System.currentTimeMillis() - startTime;

            // Log slow operations at WARN (always logged)
            if (duration > SLOW_THRESHOLD_MS) {
                log.warn("Slow operation detected: type={}, id={}, duration={}ms",
                    op.getType(), op.getId(), duration);
            } else {
                // Normal performance at DEBUG (may be sampled)
                log.debug("Operation completed: type={}, id={}, duration={}ms",
                    op.getType(), op.getId(), duration);
            }
        } catch (Exception e) {
            // Errors always logged at ERROR (never sampled)
            log.error("Operation failed: type={}, id={}", op.getType(), op.getId(), e);
        }
    }
}
```

**Benefits:**
- Slow operations and errors always logged
- Normal operations sampled under high load
- Balanced between observability and log volume

---

## Monitoring and Validation

### Check Sampling Status in Logs

When sampling is active, you can detect it by:

1. **Log rate analysis**:
```bash
# Count DEBUG logs per second
grep 'DEBUG' application.log | awk '{print $1}' | uniq -c
```

2. **Sequence gap detection**:
```bash
# Look for gaps in sequential logs
grep 'Processing record' application.log | awk '{print $NF}'
```

### Expected Patterns

**Before sampling threshold:**
```
2025-10-17T10:00:00.123+08:00 DEBUG Processing record 0
2025-10-17T10:00:00.124+08:00 DEBUG Processing record 1
2025-10-17T10:00:00.125+08:00 DEBUG Processing record 2
...
```

**After sampling threshold (sampling rate = 10):**
```
2025-10-17T10:00:05.123+08:00 DEBUG Processing record 0
2025-10-17T10:00:05.133+08:00 DEBUG Processing record 10
2025-10-17T10:00:05.143+08:00 DEBUG Processing record 20
...
```

---

## Troubleshooting

### Problem 1: Not Seeing Expected DEBUG Logs

**Symptoms:**
- DEBUG logs appear sporadically
- Missing detailed diagnostic information

**Diagnosis:**
```bash
# Check if sampling is active
grep -i "sampling" logback-spring.xml

# Check log rate
grep "DEBUG" application.log | wc -l
```

**Solutions:**

1. **Increase threshold** (allow more logs before sampling):
```xml
<thresholdLogsPerSecond>500</thresholdLogsPerSecond>
```

2. **Reduce sampling rate** (keep more logs):
```xml
<samplingRate>5</samplingRate>  <!-- Keep 20% instead of 10% -->
```

3. **Disable sampling temporarily** (development only):
```xml
<thresholdLogsPerSecond>999999</thresholdLogsPerSecond>
```

### Problem 2: Still Experiencing Log Flooding

**Symptoms:**
- Excessive log volume even with sampling
- Performance degradation

**Diagnosis:**
```bash
# Check log level distribution
grep -E "INFO|WARN|ERROR|DEBUG|TRACE" application.log | \
    awk '{print $3}' | sort | uniq -c
```

**Solutions:**

1. **Increase sampling aggressiveness**:
```xml
<thresholdLogsPerSecond>50</thresholdLogsPerSecond>
<samplingRate>20</samplingRate>  <!-- Keep only 5% -->
```

2. **Check if business logic logs at wrong level**:
```java
// BAD: Critical events at DEBUG (subject to sampling)
log.debug("Payment processed: amount={}", amount);

// GOOD: Critical events at INFO (never sampled)
log.info("Payment processed: amount={}", amount);
```

3. **Review logger granularity** - Add sampling only to specific loggers:
```xml
<logger name="com.patra.ingest.batch" level="DEBUG">
    <appender-ref ref="CONSOLE_WITH_SAMPLING" />
</logger>
```

### Problem 3: Sampling Not Working

**Symptoms:**
- All logs still written despite high rate
- No observable sampling behavior

**Diagnosis:**
1. Check filter is registered:
```xml
<!-- Verify <filter> is inside <appender>, not <root> -->
```

2. Check log level configuration:
```xml
<!-- Sampling only affects DEBUG/TRACE -->
<logger name="com.patra" level="DEBUG" />
```

**Solutions:**
1. Verify filter placement in XML hierarchy
2. Ensure logger level is DEBUG or TRACE
3. Check for duplicate logback configurations

---

## Performance Considerations

### Overhead Measurements

| Scenario | Overhead | Impact |
|----------|----------|--------|
| Below threshold | <0.1ms per log | Negligible |
| Above threshold | <0.5ms per log | Low |
| Cleanup operations | <10ms per 5 minutes | Minimal |

### Best Practices

1. **Apply to appenders, not root logger** - Allows per-appender sampling
2. **Use realistic thresholds** - Based on actual production log rates
3. **Monitor log volume metrics** - Track sampling effectiveness
4. **Test under load** - Validate sampling behavior with realistic traffic
5. **Document sampling decisions** - Explain why specific rates chosen

---

## Integration with Logging Starter

The `SamplingFilter` is included in `patra-spring-boot-starter-logging` but **not enabled by default**.

### Enable Sampling

Add filter to your service's `logback-spring.xml`:

```xml
<!-- In patra-{service}-boot/src/main/resources/logback-spring.xml -->
<include resource="logback-spring-base.xml" />

<appender name="ASYNC_CONSOLE" class="ch.qos.logback.classic.AsyncAppender">
    <filter class="com.patra.starter.logging.filter.SamplingFilter">
        <thresholdLogsPerSecond>${SAMPLING_THRESHOLD:100}</thresholdLogsPerSecond>
        <samplingRate>${SAMPLING_RATE:10}</samplingRate>
    </filter>
    <appender-ref ref="CONSOLE" />
</appender>
```

### Configure via Nacos

Add sampling properties to `logging-patra-{service}.yml`:

```yaml
# Nacos: logging-patra-ingest.yml
sampling:
  threshold-logs-per-second: 200  # High-throughput service
  sampling-rate: 5                # Keep 20% of logs
```

Reference in logback:

```xml
<thresholdLogsPerSecond>${sampling.threshold-logs-per-second}</thresholdLogsPerSecond>
<samplingRate>${sampling.sampling-rate}</samplingRate>
```

---

## Summary

**Key Takeaways:**
1. Sampling protects system stability without losing critical logs
2. INFO/WARN/ERROR logs are NEVER sampled
3. Configure threshold and rate based on service characteristics
4. Monitor log volume to validate effectiveness
5. Use INFO checkpoints for critical business events

**Decision Matrix:**

| Log Level | When to Use | Sampled? |
|-----------|-------------|----------|
| ERROR | Failures requiring immediate attention | NO |
| WARN | Recoverable issues, retries, anomalies | NO |
| INFO | Key business events, milestones | NO |
| DEBUG | Detailed diagnostics, may be high-frequency | YES |
| TRACE | Very detailed diagnostics, likely high-frequency | YES |

---

**Related Documentation:**
- [Log Level Guidelines](log-level-guidelines.md) - Semantic usage of log levels
- [Log Level Examples](log-level-examples.md) - Layer-specific examples
- [Troubleshooting Log Levels](troubleshooting-log-levels.md) - Common issues and solutions

**Implementation Reference:**
- `SamplingFilter`: [patra-spring-boot-starter-logging/src/main/java/com/patra/starter/logging/filter/SamplingFilter.java](../../patra-spring-boot-starter-logging/src/main/java/com/patra/starter/logging/filter/SamplingFilter.java)
