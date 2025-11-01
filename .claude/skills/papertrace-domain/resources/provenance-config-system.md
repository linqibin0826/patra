# Provenance Configuration System

## Overview

The Provenance Configuration System is Papertrace's flexible, multi-level configuration mechanism that governs how data is harvested from each Provenance. It supports:

- **Scope Precedence**: Task > Source > Global configuration hierarchy
- **Temporal Validity**: Time-based configuration versioning
- **Operation-Specific**: Different configs for different operation types
- **Type Safety**: Strongly-typed configuration objects

---

## Configuration Architecture

### Three-Level Hierarchy

```
┌────────────────────────────────────────────────────┐
│ TASK Level (Highest Priority)                     │
│ Specific override for a single Task               │
│ Example: Increase retry limit for problematic API │
└────────────────────────────────────────────────────┘
                     │
                     ▼ (if not found)
┌────────────────────────────────────────────────────┐
│ SOURCE Level (Medium Priority)                    │
│ Provenance-specific configuration                 │
│ Example: PubMed has different rate limits         │
└────────────────────────────────────────────────────┘
                     │
                     ▼ (if not found)
┌────────────────────────────────────────────────────┐
│ GLOBAL Level (Fallback)                           │
│ Default configuration for all Provenances         │
│ Example: Standard 3-retry policy                  │
└────────────────────────────────────────────────────┘
```

### Scope Precedence Rule

**Rule**: **TASK > SOURCE > GLOBAL** (most specific wins)

**Example Scenario**:
```
Global Config:
  retryLimit: 3
  backoffStrategy: EXPONENTIAL

Source Config (PUBMED):
  retryLimit: 5

Task Config (Task#ABC-123):
  retryLimit: 1

→ Task ABC-123 uses: retryLimit=1, backoffStrategy=EXPONENTIAL
  (1 from TASK, backoffStrategy from GLOBAL)
```

---

## Configuration Components

### 1. WindowOffsetConfig

**Purpose**: Controls how Plans are sliced into time windows.

```java
public record WindowOffsetConfig(
    Duration sliceDuration,     // Size of each Slice (e.g., 30 days)
    Duration lookbackPeriod,    // Default lookback (e.g., 365 days)
    Duration maxWindowSize,     // Maximum allowed window (e.g., 180 days)
    Instant effectiveFrom,      // When this config becomes active
    Instant effectiveTo         // When this config expires (null = forever)
) {
    public boolean isEffectiveAt(Instant instant) {
        return !instant.isBefore(effectiveFrom) &&
               (effectiveTo == null || instant.isBefore(effectiveTo));
    }
}
```

**Configuration Parameters:**

| Parameter | Type | Description | Example |
|-----------|------|-------------|---------|
| `sliceDuration` | Duration | How long each Slice should be | 30 days (monthly slicing) |
| `lookbackPeriod` | Duration | Default time range to harvest if not specified | 365 days (1 year) |
| `maxWindowSize` | Duration | Maximum allowed Plan window (safety limit) | 180 days (6 months) |
| `effectiveFrom` | Instant | Config activation time | 2024-01-01T00:00:00Z |
| `effectiveTo` | Instant | Config expiration time (nullable) | null (permanent) |

**Use Cases:**

**Monthly Slicing for PubMed:**
```java
WindowOffsetConfig pubmedWindow = new WindowOffsetConfig(
    Duration.ofDays(30),     // Monthly slices
    Duration.ofDays(365),    // 1-year lookback
    Duration.ofDays(180),    // Max 6-month window
    Instant.parse("2024-01-01T00:00:00Z"),
    null  // No expiration
);
```

**Weekly Slicing for High-Volume Source:**
```java
WindowOffsetConfig epmcWindow = new WindowOffsetConfig(
    Duration.ofDays(7),      // Weekly slices (more granular)
    Duration.ofDays(180),    // 6-month lookback
    Duration.ofDays(90),     // Max 3-month window
    Instant.parse("2024-01-01T00:00:00Z"),
    null
);
```

---

### 2. PaginationConfig

**Purpose**: Defines how to paginate through API results.

```java
public record PaginationConfig(
    int pageSize,                    // Records per page
    PaginationStrategy strategy,     // OFFSET, CURSOR, PAGE_NUMBER
    int maxPages,                    // Safety limit
    Instant effectiveFrom,
    Instant effectiveTo
) {
    public int maxRecords() {
        return pageSize * maxPages;
    }
}
```

**Pagination Strategies:**

```java
public enum PaginationStrategy {
    OFFSET,       // offset=0, offset=1000, offset=2000 (SQL-style)
    CURSOR,       // cursor=abc123, cursor=def456 (opaque tokens)
    PAGE_NUMBER   // page=1, page=2, page=3 (simple numbering)
}
```

**Strategy Comparison:**

| Strategy | API Example | Use When | Pros | Cons |
|----------|-------------|----------|------|------|
| **OFFSET** | `retstart=0&retmax=1000` | PubMed E-utilities | Simple, resumable | Slow for large offsets |
| **CURSOR** | `cursor=abc123&limit=100` | Modern REST APIs | Fast, consistent | Cannot jump to page N |
| **PAGE_NUMBER** | `page=5&size=100` | Traditional APIs | Human-readable | May have consistency issues |

**Examples:**

**PubMed (OFFSET):**
```java
PaginationConfig pubmedPagination = new PaginationConfig(
    1000,                    // 1000 records per request
    PaginationStrategy.OFFSET,
    100,                     // Max 100 pages = 100k records
    Instant.parse("2024-01-01T00:00:00Z"),
    null
);

// Generated API calls:
// Page 1: retstart=0&retmax=1000
// Page 2: retstart=1000&retmax=1000
// Page 3: retstart=2000&retmax=1000
```

**EPMC (PAGE_NUMBER):**
```java
PaginationConfig epmcPagination = new PaginationConfig(
    100,
    PaginationStrategy.PAGE_NUMBER,
    500,  // Max 500 pages = 50k records
    Instant.parse("2024-01-01T00:00:00Z"),
    null
);

// Generated API calls:
// Page 1: page=1&pageSize=100
// Page 2: page=2&pageSize=100
```

---

### 3. HttpConfig

**Purpose**: HTTP client configuration for API calls.

```java
public record HttpConfig(
    int connectTimeout,           // Seconds
    int readTimeout,              // Seconds
    Map<String, String> headers,  // Default headers
    String userAgent,
    boolean followRedirects,
    Instant effectiveFrom,
    Instant effectiveTo
) {
    public Duration connectTimeoutDuration() {
        return Duration.ofSeconds(connectTimeout);
    }
}
```

**Configuration Parameters:**

| Parameter | Type | Description | Example |
|-----------|------|-------------|---------|
| `connectTimeout` | int | Connection timeout (seconds) | 10 |
| `readTimeout` | int | Read timeout (seconds) | 30 |
| `headers` | Map | Default HTTP headers | {"User-Agent": "Papertrace/1.0"} |
| `userAgent` | String | User-Agent header value | "Papertrace/1.0 (contact@papertrace.io)" |
| `followRedirects` | boolean | Follow HTTP redirects | true |

**Example:**

```java
HttpConfig pubmedHttp = new HttpConfig(
    10,   // 10s connect timeout
    30,   // 30s read timeout
    Map.of(
        "User-Agent", "Papertrace/1.0 (contact@papertrace.io)",
        "Accept", "application/json"
    ),
    "Papertrace/1.0",
    true,  // Follow redirects
    Instant.parse("2024-01-01T00:00:00Z"),
    null
);
```

---

### 4. BatchingConfig

**Purpose**: Control batch processing parallelism.

```java
public record BatchingConfig(
    int batchSize,         // Number of Tasks to process concurrently
    int maxConcurrency,    // Thread pool size
    Duration batchDelay,   // Delay between batches
    Instant effectiveFrom,
    Instant effectiveTo
) { }
```

**Configuration Parameters:**

| Parameter | Type | Description | Example |
|-----------|------|-------------|---------|
| `batchSize` | int | Tasks per batch | 50 |
| `maxConcurrency` | int | Parallel thread count | 10 |
| `batchDelay` | Duration | Delay between batches | 5 seconds |

**Example:**

```java
BatchingConfig pubmedBatching = new BatchingConfig(
    50,                      // Process 50 Tasks per batch
    10,                      // Use 10 threads
    Duration.ofSeconds(5),   // 5s delay between batches
    Instant.parse("2024-01-01T00:00:00Z"),
    null
);
```

**Use Case:**
```
Plan has 500 Tasks

Batch 1: Tasks 1-50   (parallel, 10 threads)
Wait 5 seconds
Batch 2: Tasks 51-100
Wait 5 seconds
...
Batch 10: Tasks 451-500
```

---

### 5. RetryConfig

**Purpose**: Retry strategy for failed Tasks.

```java
public record RetryConfig(
    int maxRetries,
    BackoffStrategy backoffStrategy,
    Duration initialDelay,
    Duration maxDelay,
    List<Integer> retriableHttpCodes,  // e.g., [429, 503, 504]
    Instant effectiveFrom,
    Instant effectiveTo
) {
    public Duration calculateDelay(int retryCount) {
        return switch (backoffStrategy) {
            case FIXED -> initialDelay;
            case LINEAR -> initialDelay.multipliedBy(retryCount + 1);
            case EXPONENTIAL -> {
                long delayMs = initialDelay.toMillis() * (long) Math.pow(2, retryCount);
                yield Duration.ofMillis(Math.min(delayMs, maxDelay.toMillis()));
            }
        };
    }
}
```

**Backoff Strategies:**

```java
public enum BackoffStrategy {
    FIXED,        // Always use initialDelay
    LINEAR,       // Delay increases linearly (1x, 2x, 3x)
    EXPONENTIAL   // Delay doubles each time (2^0, 2^1, 2^2)
}
```

**Strategy Comparison:**

| Retry | FIXED (5s) | LINEAR (5s) | EXPONENTIAL (5s, max 60s) |
|-------|------------|-------------|--------------------------|
| 1st   | 5s         | 5s          | 5s (2^0 * 5s) |
| 2nd   | 5s         | 10s         | 10s (2^1 * 5s) |
| 3rd   | 5s         | 15s         | 20s (2^2 * 5s) |
| 4th   | 5s         | 20s         | 40s (2^3 * 5s) |
| 5th   | 5s         | 25s         | 60s (capped at maxDelay) |

**Example:**

```java
RetryConfig pubmedRetry = new RetryConfig(
    5,                            // Max 5 retries
    BackoffStrategy.EXPONENTIAL,
    Duration.ofSeconds(5),        // Start with 5s
    Duration.ofSeconds(60),       // Cap at 60s
    List.of(429, 503, 504),      // Retry on rate limit and server errors
    Instant.parse("2024-01-01T00:00:00Z"),
    null
);
```

**Retriable HTTP Codes:**
- `429 Too Many Requests`: Rate limiting
- `503 Service Unavailable`: Temporary server issue
- `504 Gateway Timeout`: Upstream timeout

---

### 6. RateLimitConfig

**Purpose**: Prevent exceeding API rate limits.

```java
public record RateLimitConfig(
    int requestsPerSecond,
    int requestsPerMinute,
    int burstSize,          // Token bucket burst capacity
    Instant effectiveFrom,
    Instant effectiveTo
) { }
```

**Configuration Parameters:**

| Parameter | Type | Description | Example |
|-----------|------|-------------|---------|
| `requestsPerSecond` | int | Max requests per second | 10 |
| `requestsPerMinute` | int | Max requests per minute | 300 |
| `burstSize` | int | Burst capacity (token bucket) | 20 |

**Token Bucket Algorithm:**

```
Bucket Capacity: burstSize (e.g., 20 tokens)
Refill Rate: requestsPerSecond (e.g., 10 tokens/s)

Request arrives:
  if bucket has tokens:
    consume 1 token
    process request
  else:
    wait for token refill
```

**Example:**

```java
// PubMed: 10 req/s, burst up to 20
RateLimitConfig pubmedRateLimit = new RateLimitConfig(
    10,    // 10 requests/second
    300,   // 300 requests/minute (backup limit)
    20,    // Burst size: can send 20 immediately, then throttle
    Instant.parse("2024-01-01T00:00:00Z"),
    null
);
```

**Burst Example:**
```
Time 0s:   Send 20 requests (burst) → bucket empty
Time 0.1s: Wait (bucket refilling at 10/s)
Time 1s:   Bucket has 10 tokens → send 10 requests
Time 2s:   Bucket has 10 tokens → send 10 requests
```

---

## Temporal Validity (Configuration Versioning)

### Problem

APIs evolve:
- PubMed increases rate limits: 3 req/s → 10 req/s
- EPMC changes pagination: OFFSET → CURSOR
- Need to switch configs at specific time

### Solution: Effective Time Windows

**Each config has**:
- `effectiveFrom`: When config becomes active
- `effectiveTo`: When config expires (null = forever)

**Query Logic:**
```java
public Optional<WindowOffsetConfig> loadActiveConfig(
    ProvenanceCode provenanceCode,
    Instant at
) {
    return repository.findAllConfigs(provenanceCode).stream()
        .filter(config -> config.isEffectiveAt(at))
        .findFirst();
}
```

### Example: PubMed Rate Limit Increase

**Old Config (2023-01-01 to 2024-06-30):**
```java
RateLimitConfig oldConfig = new RateLimitConfig(
    3,     // 3 req/s
    180,
    10,
    Instant.parse("2023-01-01T00:00:00Z"),
    Instant.parse("2024-06-30T23:59:59Z")  // Expires
);
```

**New Config (2024-07-01 onwards):**
```java
RateLimitConfig newConfig = new RateLimitConfig(
    10,    // 10 req/s (increased!)
    600,
    20,
    Instant.parse("2024-07-01T00:00:00Z"),
    null   // No expiration
);
```

**Query Result:**
```
loadActiveConfig(PUBMED, Instant.parse("2024-06-15T12:00:00Z"))
  → Returns oldConfig (3 req/s)

loadActiveConfig(PUBMED, Instant.parse("2024-07-15T12:00:00Z"))
  → Returns newConfig (10 req/s)
```

---

## Operation-Specific Configuration

### Problem

Different operations have different requirements:
- **Harvest**: Initial data ingestion (high volume, aggressive rate limit)
- **Update**: Incremental updates (low volume, conservative)
- **Backfill**: Historical data (very high volume, slow rate)

### Solution: operationType Field

```java
public record ProvenanceConfiguration(
    Provenance provenance,
    String operationType,  // "harvest", "update", "backfill"
    WindowOffsetConfig windowOffset,
    ...
) { }
```

**Configuration Repository Method:**
```java
public Optional<ProvenanceConfiguration> loadConfiguration(
    ProvenanceCode provenanceCode,
    String operationType,
    Instant at
) {
    return configurations.stream()
        .filter(config -> config.provenance().provenanceCode().equals(provenanceCode))
        .filter(config -> config.operationType().equals(operationType))
        .filter(config -> config.isEffectiveAt(at))
        .findFirst();
}
```

### Example: PubMed Harvest vs Update

**Harvest Config (monthly slices, aggressive):**
```java
ProvenanceConfiguration harvestConfig = new ProvenanceConfiguration(
    pubmed,
    "harvest",
    new WindowOffsetConfig(Duration.ofDays(30), ...),
    new RateLimitConfig(10, 600, 20, ...)
);
```

**Update Config (daily slices, conservative):**
```java
ProvenanceConfiguration updateConfig = new ProvenanceConfiguration(
    pubmed,
    "update",
    new WindowOffsetConfig(Duration.ofDays(1), ...),  // Daily slices
    new RateLimitConfig(5, 300, 10, ...)               // Slower rate
);
```

**Usage:**
```java
// Creating a harvest Plan
ProvenanceConfiguration config = repository.loadConfiguration(
    ProvenanceCode.PUBMED,
    "harvest",  // Operation type
    Instant.now()
);

// Creating an update Plan
ProvenanceConfiguration updateConf = repository.loadConfiguration(
    ProvenanceCode.PUBMED,
    "update",   // Different operation type
    Instant.now()
);
```

---

## Configuration Loading Algorithm

### Pseudocode

```java
public ProvenanceConfiguration loadConfiguration(
    ProvenanceCode provenanceCode,
    String operationType,
    Optional<TaskId> taskId,
    Instant at
) {
    // 1. Try TASK level (highest priority)
    if (taskId.isPresent()) {
        Optional<Config> taskConfig = loadTaskConfig(taskId.get(), at);
        if (taskConfig.isPresent()) {
            return taskConfig.get();
        }
    }

    // 2. Try SOURCE level (medium priority)
    Optional<Config> sourceConfig = loadSourceConfig(
        provenanceCode,
        operationType,
        at
    );
    if (sourceConfig.isPresent()) {
        return sourceConfig.get();
    }

    // 3. Fall back to GLOBAL level
    return loadGlobalConfig(operationType, at)
        .orElseThrow(() -> new ConfigurationException("No config found"));
}
```

### Real-World Example

**Scenario: Load Config for Task ABC-123 (PUBMED, harvest, 2024-07-15)**

**Step 1: Check TASK level**
```
Query: taskId = ABC-123, effectiveAt = 2024-07-15
Result: No task-specific config found
```

**Step 2: Check SOURCE level**
```
Query: provenanceCode = PUBMED, operationType = harvest, effectiveAt = 2024-07-15
Result: Found PubMed harvest config
  - retryLimit: 5
  - rateLimit: 10 req/s
  - sliceDuration: 30 days
```

**Step 3: Return SOURCE level config**
```
Task ABC-123 uses:
  - retryLimit: 5 (from SOURCE)
  - rateLimit: 10 req/s (from SOURCE)
  - sliceDuration: 30 days (from SOURCE)
```

---

## Best Practices

### 1. Start with GLOBAL, Specialize to SOURCE

```java
// 1. Define sensible GLOBAL defaults
GlobalConfig global = new GlobalConfig(
    new RetryConfig(3, EXPONENTIAL, ...),  // 3 retries is reasonable
    new RateLimitConfig(5, 300, 10, ...)   // Conservative rate limit
);

// 2. Override for specific Provenances
SourceConfig pubmed = new SourceConfig(
    ProvenanceCode.PUBMED,
    new RateLimitConfig(10, 600, 20, ...)  // PubMed allows higher rate
);
```

### 2. Use TASK level sparingly

**Good Use Cases:**
- Debugging: Increase logging for one Task
- Workaround: Retry limit=1 for a known-bad API

**Bad Use Cases:**
- ❌ Setting different retry limits for every Task
- ❌ Using TASK configs as a substitute for SOURCE configs

### 3. Plan for Config Changes

**Use temporal validity:**
```java
// Old config expires on 2024-12-31
WindowOffsetConfig oldConfig = new WindowOffsetConfig(
    ...,
    Instant.parse("2023-01-01T00:00:00Z"),
    Instant.parse("2024-12-31T23:59:59Z")  // Expiration
);

// New config takes over on 2025-01-01
WindowOffsetConfig newConfig = new WindowOffsetConfig(
    ...,
    Instant.parse("2025-01-01T00:00:00Z"),
    null  // No expiration
);
```

**Effect**: Smooth transition, no downtime.

---

## Troubleshooting

### Issue: "Configuration not found"

**Symptoms**: `ConfigurationException: No config found for PUBMED/harvest`

**Diagnosis**:
1. Check if SOURCE config exists: `SELECT * FROM provenance_config WHERE provenance_code = 'PUBMED' AND operation_type = 'harvest'`
2. Check temporal validity: Is `effective_from <= now < effective_to`?
3. Check GLOBAL fallback: Does a GLOBAL config exist?

**Fix**: Create missing SOURCE or GLOBAL config.

---

### Issue: "Wrong config applied"

**Symptoms**: Task uses unexpected rate limit

**Diagnosis**:
1. Print effective config: `log.info("Using config: {}", config)`
2. Check scope precedence: TASK > SOURCE > GLOBAL
3. Verify `operationType` matches

**Fix**: Remove conflicting TASK config or update SOURCE config.

---

### Issue: "Config change not applied"

**Symptoms**: New config created but Tasks still use old config

**Diagnosis**:
1. Check `effectiveFrom`: Is it in the future?
2. Check config cache: Is system caching old config?
3. Verify temporal overlap: Do old and new configs overlap?

**Fix**: Set correct `effectiveFrom`, clear cache, or expire old config.

---

## Summary

**Key Concepts:**

1. **Three-Level Hierarchy**: TASK > SOURCE > GLOBAL
2. **Temporal Validity**: `effectiveFrom` / `effectiveTo` for versioning
3. **Operation-Specific**: Different configs for harvest/update/backfill
4. **Six Config Components**: Window, Pagination, Http, Batching, Retry, RateLimit

**Design Goals:**

- **Flexibility**: Override configs at multiple levels
- **Safety**: Temporal validity prevents abrupt changes
- **Scalability**: Different configs for different operations

**See Also:**
- [business-concepts.md](business-concepts.md) for Provenance definition
- [plan-task-workflow.md](plan-task-workflow.md) for how configs are used
