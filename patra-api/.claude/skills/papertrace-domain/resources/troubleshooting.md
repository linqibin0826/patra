# Troubleshooting Guide

## Overview

This guide helps diagnose and fix common issues in Papertrace. Each issue includes symptoms, root causes, diagnostic steps, and solutions.

---

## Issue 1: Plan Stuck in RUNNING Status

### Symptoms
- Plan created hours/days ago
- Status still RUNNING
- Some Tasks SUCCEEDED, others PENDING/FAILED
- No new Task progress

### Root Causes

**Cause 1**: Tasks exhausted but Plan completion check didn't run

**Cause 2**: Event handler failed silently

**Cause 3**: Scheduled executor stopped

### Diagnostic Steps

**Step 1: Check Task Distribution**
```sql
SELECT
    status,
    COUNT(*) as count
FROM batch_task
WHERE plan_id = <plan_id>
GROUP BY status;
```

**Expected**:
```
SUCCEEDED: 80
PENDING: 0
RUNNING: 0
FAILED: 0
EXHAUSTED: 20
```

**Problem**: If EXHAUSTED > 0 and (PENDING + RUNNING) = 0, Plan should be FAILED.

**Step 2: Check Event Handler Logs**
```bash
grep "PlanCreatedEvent" application.log | grep <plan_id>
grep "TaskStatusChangedEvent" application.log | grep <plan_id>
```

**Expected**:
```
[2024-07-15 10:00:00] PlanCreatedEvent published for plan-123
[2024-07-15 10:00:01] PlanCreatedEventHandler processing plan-123
[2024-07-15 10:05:30] TaskStatusChangedEvent for plan-123 (task-456 → SUCCEEDED)
```

**Problem**: If no TaskStatusChangedEvent logs, event publishing broken.

**Step 3**: Check Scheduled Executor
```bash
# Check if PlanCompletionChecker is running
grep "PlanCompletionChecker" application.log --since="1 hour ago"
```

### Solutions

**Solution 1: Manually Trigger Completion Check**
```java
@Autowired
private PlanCompletionChecker completionChecker;

completionChecker.checkPlanCompletion(PlanId.of("<plan_id>"));
```

**Solution 2: Fix Event Handler (if @Async failed)**
```java
// Check AsyncConfig
@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.setRejectedExecutionHandler(new CallerRunsPolicy());  // ← Add this!
        executor.initialize();
        return executor;
    }
}
```

**Solution 3: Restart Scheduled Tasks**
```bash
# Restart application
kubectl rollout restart deployment/patra-ingest

# Or trigger endpoint (if exists)
curl -X POST http://localhost:8082/actuator/scheduledtasks/restart
```

---

## Issue 2: Tasks Fail with "Expression Not Found"

### Symptoms
- Tasks fail immediately with error: `ExpressionNotFoundException`
- Error message: `"No expression for publicationDate:range"`

### Root Cause

Missing Expression in patra-registry for Provenance + field + capability combination.

### Diagnostic Steps

**Step 1: Check Expression Exists**
```sql
SELECT
    provenance_code,
    expr_field,
    capability,
    render_rule,
    is_active
FROM reg_expression
WHERE provenance_code = 'PUBMED'
  AND expr_field = 'publicationDate'
  AND capability = 'range';
```

**Expected**: At least 1 row with `is_active = true`.

**Problem**: 0 rows or `is_active = false`.

**Step 2: Check Configuration Loading**
```java
List<Expression> expressions = expressionRepository.findByProvenance(
    ProvenanceCode.PUBMED
);
System.out.println("Loaded expressions: " + expressions.size());
expressions.forEach(expr ->
    System.out.println(expr.exprField() + ":" + expr.capability())
);
```

### Solutions

**Solution 1: Create Missing Expression**
```sql
INSERT INTO reg_expression (
    provenance_code,
    expr_field,
    capability,
    render_rule,
    is_active,
    priority,
    effective_from
) VALUES (
    'PUBMED',
    'publicationDate',
    'range',
    'mindate={start}&maxdate={end}',
    true,
    1,
    NOW()
);
```

**Solution 2: Activate Inactive Expression**
```sql
UPDATE reg_expression
SET is_active = true
WHERE provenance_code = 'PUBMED'
  AND expr_field = 'publicationDate'
  AND capability = 'range';
```

**Solution 3: Check Expression Cache**
```java
// If using cache, clear it
@Autowired
private CacheManager cacheManager;

cacheManager.getCache("expressions").clear();
```

---

## Issue 3: Plan Generates 0 Slices

### Symptoms
- Plan created successfully
- `slice_count = 0`
- No Tasks generated
- No error message

### Root Causes

**Cause 1**: `startTime >= endTime`

**Cause 2**: `sliceDuration` larger than Plan window

**Cause 3**: Invalid `WindowOffsetConfig`

### Diagnostic Steps

**Step 1: Check Plan Time Window**
```sql
SELECT
    id,
    start_time,
    end_time,
    TIMESTAMPDIFF(HOUR, start_time, end_time) as window_hours
FROM batch_plan
WHERE id = <plan_id>;
```

**Problem**: `window_hours <= 0` or `NULL`.

**Step 2: Check WindowOffsetConfig**
```sql
SELECT
    provenance_code,
    window_offset_json
FROM reg_provenance_config
WHERE provenance_code = 'PUBMED'
  AND operation_type = 'harvest'
  AND effective_from <= NOW()
  AND (effective_to IS NULL OR effective_to > NOW());
```

**Expected**:
```json
{
  "sliceDuration": "P30D",
  "lookbackPeriod": "P365D",
  "maxWindowSize": "P180D"
}
```

**Problem**: `sliceDuration = "P365D"` (larger than Plan window).

### Solutions

**Solution 1: Fix Plan Time Window**
```java
// Re-create Plan with valid times
CreatePlanCommand command = new CreatePlanCommand(
    ProvenanceCode.PUBMED,
    LocalDateTime.of(2024, 1, 1, 0, 0),   // startTime
    LocalDateTime.of(2024, 12, 31, 23, 59), // endTime (AFTER startTime!)
    "harvest",
    "user123"
);
```

**Solution 2: Adjust WindowOffsetConfig**
```sql
UPDATE reg_provenance_config
SET window_offset_json = JSON_SET(
    window_offset_json,
    '$.sliceDuration',
    'P7D'  -- Change from 30 days to 7 days
)
WHERE provenance_code = 'PUBMED';
```

---

## Issue 4: Tasks Retry Infinitely

### Symptoms
- Same Task retries 100+ times
- `retry_count` keeps increasing
- Task never reaches EXHAUSTED status

### Root Cause

`maxRetries` not configured or set to very high value (e.g., 999).

### Diagnostic Steps

**Step 1: Check Task Retry Count**
```sql
SELECT
    id,
    business_key,
    retry_count,
    status,
    error_message
FROM batch_task
WHERE retry_count > 10
ORDER BY retry_count DESC
LIMIT 10;
```

**Step 2: Check RetryConfig**
```sql
SELECT
    provenance_code,
    retry_json
FROM reg_provenance_config
WHERE provenance_code = 'PUBMED';
```

**Expected**:
```json
{
  "maxRetries": 3,
  "backoffStrategy": "EXPONENTIAL",
  "initialDelay": "PT5S",
  "maxDelay": "PT60S"
}
```

**Problem**: `maxRetries = 999` or missing.

### Solutions

**Solution 1: Set Reasonable maxRetries**
```sql
UPDATE reg_provenance_config
SET retry_json = JSON_SET(
    retry_json,
    '$.maxRetries',
    3  -- Reasonable limit
)
WHERE provenance_code = 'PUBMED';
```

**Solution 2: Manually Exhaust Task**
```sql
UPDATE batch_task
SET status = 'EXHAUSTED',
    error_message = 'Manually exhausted due to infinite retries'
WHERE id = <task_id>;
```

**Solution 3: Add Retry Limit Validation**
```java
public record RetryConfig(
    int maxRetries,
    ...
) {
    public RetryConfig {
        if (maxRetries < 0 || maxRetries > 10) {
            throw new IllegalArgumentException("maxRetries must be between 0 and 10");
        }
    }
}
```

---

## Issue 5: Configuration Not Applied

### Symptoms
- Created new SOURCE-level config
- Tasks still use old GLOBAL config
- No error message

### Root Causes

**Cause 1**: Configuration cache not cleared

**Cause 2**: `effectiveFrom` in future

**Cause 3**: `operationType` mismatch

**Cause 4**: Temporal overlap (multiple configs effective)

### Diagnostic Steps

**Step 1: Check Effective Time**
```sql
SELECT
    id,
    provenance_code,
    operation_type,
    effective_from,
    effective_to,
    NOW() as current_time,
    CASE
        WHEN effective_from > NOW() THEN 'Future'
        WHEN effective_to IS NOT NULL AND effective_to < NOW() THEN 'Expired'
        ELSE 'Active'
    END as status
FROM reg_provenance_config
WHERE provenance_code = 'PUBMED';
```

**Problem**: New config has `status = 'Future'`.

**Step 2: Check Scope Resolution**
```java
@Autowired
private ConfigurationResolver configResolver;

ProvenanceConfiguration resolved = configResolver.resolve(
    ProvenanceCode.PUBMED,
    "harvest",
    Optional.empty()
);

System.out.println("Resolved config: " + resolved);
System.out.println("Retry limit: " + resolved.retry().maxRetries());
```

**Step 3**: Check for Overlapping Configs
```sql
SELECT
    id,
    operation_type,
    effective_from,
    effective_to
FROM reg_provenance_config
WHERE provenance_code = 'PUBMED'
  AND effective_from <= NOW()
  AND (effective_to IS NULL OR effective_to > NOW())
ORDER BY effective_from DESC;
```

**Problem**: Multiple rows returned (ambiguous).

### Solutions

**Solution 1: Fix Effective Time**
```sql
UPDATE reg_provenance_config
SET effective_from = NOW()  -- Make effective immediately
WHERE id = <config_id>;
```

**Solution 2: Expire Old Config**
```sql
UPDATE reg_provenance_config
SET effective_to = NOW()  -- Expire old config
WHERE id = <old_config_id>;
```

**Solution 3: Clear Configuration Cache**
```bash
# Call cache clear endpoint
curl -X POST http://localhost:8081/actuator/caches/configurations/clear

# Or restart service
kubectl rollout restart deployment/patra-registry
```

---

## Issue 6: Slice Estimation Way Off

### Symptoms
- Slice estimated 10,000 records
- Actually fetched 500,000 records
- Tasks timeout due to large responses

### Root Cause

`RecordEstimator` uses outdated statistics or wrong formula.

### Diagnostic Steps

**Step 1: Compare Estimate vs Actual**
```sql
SELECT
    s.id,
    s.estimated_record_count,
    SUM(t.records_fetched) as actual_count,
    (SUM(t.records_fetched) - s.estimated_record_count) as difference
FROM slice s
JOIN batch_task t ON t.slice_id = s.id
WHERE s.plan_id = <plan_id>
  AND t.status = 'SUCCEEDED'
GROUP BY s.id
HAVING ABS(difference) > s.estimated_record_count * 0.5;  -- 50% error
```

**Step 2: Check Estimator Logic**
```java
@Component
public class RecordEstimator {
    public int estimate(
        ProvenanceCode provenanceCode,
        LocalDateTime startTime,
        LocalDateTime endTime
    ) {
        // Current logic (maybe flawed):
        long days = ChronoUnit.DAYS.between(startTime, endTime);
        return (int) (days * 1000);  // Naive: 1000 records/day

        // Better: Query actual statistics
    }
}
```

### Solutions

**Solution 1: Use Actual Statistics**
```java
@Component
@RequiredArgsConstructor
public class ImprovedRecordEstimator {
    private final StatisticsRepository statsRepo;

    public int estimate(
        ProvenanceCode provenanceCode,
        LocalDateTime startTime,
        LocalDateTime endTime
    ) {
        // Query actual historical data
        Optional<Statistics> stats = statsRepo.findByProvenanceAndTimeRange(
            provenanceCode,
            startTime.minusMonths(1),  // Same period last month
            endTime.minusMonths(1)
        );

        if (stats.isPresent()) {
            return stats.get().recordCount();
        }

        // Fallback to conservative estimate
        long days = ChronoUnit.DAYS.between(startTime, endTime);
        return (int) (days * 500);  // Conservative: 500/day
    }
}
```

**Solution 2: Add Safety Margin**
```java
int baseEstimate = estimator.estimate(...);
int safeEstimate = (int) (baseEstimate * 1.5);  // 50% buffer
```

**Solution 3: Dynamic Task Splitting**
```java
// If Task fetches > 100k records, split into smaller Tasks
if (recordsFetched > 100_000) {
    splitTaskIntoSmaller(task);
}
```

---

## Issue 7: Rate Limit Errors

### Symptoms
- Tasks fail with HTTP 429 (Too Many Requests)
- Error message: `"API rate limit exceeded"`
- Retries also fail immediately

### Root Cause

**Cause 1**: `RateLimitConfig` too aggressive

**Cause 2**: Multiple instances sending requests

**Cause 3**: Provider lowered rate limits

### Diagnostic Steps

**Step 1: Check Rate Limit Config**
```sql
SELECT
    provenance_code,
    rate_limit_json
FROM reg_provenance_config
WHERE provenance_code = 'PUBMED';
```

**Expected**:
```json
{
  "requestsPerSecond": 10,
  "requestsPerMinute": 600,
  "burstSize": 20
}
```

**Problem**: `requestsPerSecond = 100` (too high).

**Step 2: Check Actual Request Rate**
```bash
# Count requests in last minute
grep "Calling PubMed API" application.log --since="1 minute ago" | wc -l
```

**Step 3: Check Provider Response**
```bash
# Look for rate limit headers in response
grep "X-RateLimit-Remaining" application.log | tail -1
```

### Solutions

**Solution 1: Lower Rate Limit**
```sql
UPDATE reg_provenance_config
SET rate_limit_json = JSON_SET(
    rate_limit_json,
    '$.requestsPerSecond',
    3  -- Conservative limit
)
WHERE provenance_code = 'PUBMED';
```

**Solution 2: Implement Distributed Rate Limiting**
```java
@Component
@RequiredArgsConstructor
public class DistributedRateLimiter {
    private final RedisTemplate<String, String> redis;

    public boolean allowRequest(ProvenanceCode provenanceCode) {
        String key = "rate-limit:" + provenanceCode.value();
        Long current = redis.opsForValue().increment(key);

        if (current == 1) {
            redis.expire(key, 1, TimeUnit.SECONDS);
        }

        return current <= 10;  // 10 requests/second across all instances
    }
}
```

**Solution 3: Add Retry After Delay**
```java
if (response.statusCode() == 429) {
    String retryAfter = response.headers().firstValue("Retry-After").orElse("60");
    Duration delay = Duration.ofSeconds(Long.parseLong(retryAfter));
    scheduleRetry(task, delay);
}
```

---

## Issue 8: Expression Rendering Produces Invalid URL

### Symptoms
- Task fails with HTTP 400 (Bad Request)
- Provider error: `"Invalid query parameter"`
- Expression seems correct

### Root Causes

**Cause 1**: Missing URL encoding

**Cause 2**: Special characters in values

**Cause 3**: Template syntax error

### Diagnostic Steps

**Step 1: Log Rendered URL**
```java
String url = expressionRenderer.render(...);
log.info("Rendered URL: {}", url);
```

**Example Output**:
```
Rendered URL: https://api.pubmed.gov?author=O'Brien&title=COVID-19
                                              ^ Problem: Unencoded quote
```

**Step 2: Test URL Manually**
```bash
curl "https://api.pubmed.gov?author=O'Brien&title=COVID-19"
# Response: 400 Bad Request
```

**Step 3: Check Expression Template**
```sql
SELECT render_rule
FROM reg_expression
WHERE provenance_code = 'PUBMED'
  AND expr_field = 'author';
```

### Solutions

**Solution 1: URL-Encode Values**
```java
public String render(Map<String, Object> values) {
    String result = renderRule;

    for (Map.Entry<String, Object> entry : values.entrySet()) {
        String placeholder = "{" + entry.getKey() + "}";
        String value = URLEncoder.encode(
            entry.getValue().toString(),
            StandardCharsets.UTF_8
        );
        result = result.replace(placeholder, value);
    }

    return result;
}
```

**Solution 2: Escape Special Characters**
```java
// Before rendering
String authorName = command.getAuthor()
    .replace("'", "\\'")
    .replace("\"", "\\\"");
```

**Solution 3: Validate Rendered Output**
```java
public String render(Map<String, Object> values) {
    String result = // ... render logic

    // Validate
    try {
        new URI(baseUrl + "?" + result);  // Will throw if invalid
    } catch (URISyntaxException e) {
        throw new ExpressionRenderingException("Invalid URL: " + result, e);
    }

    return result;
}
```

---

## Diagnostic Commands Cheatsheet

### Database Queries

**Check Plan Status Distribution**:
```sql
SELECT status, COUNT(*) FROM batch_plan GROUP BY status;
```

**Find Stuck Plans**:
```sql
SELECT id, provenance_code, created_at
FROM batch_plan
WHERE status = 'RUNNING'
  AND created_at < NOW() - INTERVAL 24 HOUR;
```

**Check Task Failures**:
```sql
SELECT
    provenance_code,
    status,
    COUNT(*) as count,
    AVG(retry_count) as avg_retries
FROM batch_task
WHERE plan_id = <plan_id>
GROUP BY provenance_code, status;
```

### Logs

**Filter by Plan ID**:
```bash
grep "plan-123" application.log
```

**Count Errors**:
```bash
grep "ERROR" application.log --since="1 hour ago" | wc -l
```

**Watch Live Logs**:
```bash
tail -f application.log | grep --color=always "Task.*SUCCEEDED\|Task.*FAILED"
```

### API Endpoints (Actuator)

**Check Health**:
```bash
curl http://localhost:8082/actuator/health
```

**List Scheduled Tasks**:
```bash
curl http://localhost:8082/actuator/scheduledtasks
```

**Clear Cache**:
```bash
curl -X POST http://localhost:8081/actuator/caches/expressions/clear
```

---

## Summary

**Most Common Issues**:
1. Plans stuck in RUNNING (event handlers)
2. Missing Expressions (configuration)
3. Rate limiting (too aggressive config)
4. Configuration not applied (temporal validity)

**Key Diagnostic Tools**:
- SQL queries for data inspection
- Log analysis for event tracking
- Actuator endpoints for runtime state

**Best Practices**:
- Always check temporal validity (`effectiveFrom`/`effectiveTo`)
- Monitor retry counts (prevent infinite loops)
- Validate configurations before deployment
- Use conservative estimates and rate limits

**See Also**:
- [plan-task-workflow.md](plan-task-workflow.md) for workflow details
- [provenance-config-system.md](provenance-config-system.md) for configuration
