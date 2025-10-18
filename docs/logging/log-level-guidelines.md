# Log Level Semantic Guidelines (FR-001)

**Version**: 1.0.0
**Status**: Active
**Related**: [Log Level Usage Examples](./log-level-examples.md) | [Troubleshooting Log Levels](./troubleshooting-log-levels.md)

---

## Overview

This document defines the five standard log levels used across all Papertrace microservices and provides clear guidelines on when to use each level. Proper log level usage is critical for production observability, troubleshooting efficiency, and log volume management (SC-004: 40% log volume reduction target).

---

## The Five Log Levels

### 1. ERROR - System Failures Requiring Immediate Attention

**When to use:**
- System failures that prevent normal operation
- External dependency unavailable (database, external API, message queue)
- Unhandled exceptions that reach controller/job boundary
- Data corruption or integrity violations
- Security violations (authentication bypass attempts, authorization failures)

**What to include:**
- Full stack trace (always)
- Operation being performed
- Input context (sanitized)
- Impact assessment (e.g., "Batch processing halted")

**Example:**
```java
try {
  pubMedClient.fetchArticles(query);
} catch (HttpClientErrorException e) {
  log.error(
    "Failed to fetch PubMed articles for query '{}' after 3 retries: {} - Batch processing halted",
    sanitizer.sanitize(query),
    e.getMessage(),
    e
  );
}
```

**Anti-patterns:**
- ❌ Logging recoverable exceptions at ERROR (use WARN for retries)
- ❌ Missing stack trace (always include `e` parameter)
- ❌ Vague messages like "Error occurred" (specify what failed)

---

### 2. WARN - Recoverable Issues or Degraded Functionality

**When to use:**
- Retry attempts (before final failure)
- Missing optional configuration (using defaults)
- Deprecated API usage warnings
- Failed authentication attempts (security audit)
- Missing trace context (fallback to new trace ID)
- Resource usage approaching limits (90% disk, 80% memory)

**What to include:**
- What went wrong (briefly)
- Recovery action taken
- Potential impact (if any)

**Example:**
```java
if (traceContext == null) {
  String newTraceId = UUID.randomUUID().toString();
  log.warn(
    "Trace context missing from request - generating new trace ID: {} (check upstream trace propagation)",
    newTraceId
  );
  traceContext = new DistributedTraceContext(newTraceId, null, null);
}
```

**Anti-patterns:**
- ❌ Using WARN for normal business validation failures (use INFO or DEBUG)
- ❌ Logging every retry attempt (use DEBUG for retries, WARN for final failure)
- ❌ Missing recovery action context ("What did the system do?")

---

### 3. INFO - Key Business Events and State Changes

**When to use:**
- Application startup/shutdown events
- Batch processing start/complete with summary counts
- External API calls (with FR-006 context: URL, status, duration, error)
- Authentication events (successful login, logout)
- Critical business state transitions (provenance updates, data ingestion results)
- Scheduled job execution start/complete
- Message queue publish/consume events
- Configuration changes applied (dynamic log levels)

**What to include:**
- Business operation performed
- Key metrics (counts, duration, success rate)
- Trace/correlation ID (automatic via MDC)
- Outcome (success/failure)

**Example:**
```java
log.info(
  "Completed PubMed batch processing: batchId={}, processed={}, success={}, errors={}, duration={}ms",
  batchId,
  totalCount,
  successCount,
  errorCount,
  durationMs
);
```

**Anti-patterns:**
- ❌ Per-record logging in batches (use aggregated summaries)
- ❌ Logging method entry/exit at INFO (use DEBUG)
- ❌ Missing key metrics (always include counts/duration for operations)

---

### 4. DEBUG - Detailed Processing Flow for Troubleshooting

**When to use:**
- Method entry/exit with parameters (orchestrators only)
- Business logic decision points ("Selected strategy X because Y")
- Database query execution (SQL statement, execution time per FR-007)
- External API request/response details (headers, payload)
- Data transformation logic (input → output)
- Validation logic evaluation
- Message queue message details (payload, headers)
- Retry attempts with context

**What to include:**
- Operation name
- Input parameters (sanitized)
- Decision factors ("if X then Y")
- Intermediate results

**Example:**
```java
log.debug(
  "Selecting provenance source for article PMID={}: found {} candidates, applying priority rules",
  pmid,
  candidateCount
);
// ... selection logic ...
log.debug(
  "Selected provenance source: {} (priority={}, lastUpdate={})",
  selectedSource.getId(),
  selectedSource.getPriority(),
  selectedSource.getLastUpdate()
);
```

**Anti-patterns:**
- ❌ Logging in tight loops (use TRACE or sampling)
- ❌ Logging every method in every layer (focus on orchestrators and complex logic)
- ❌ Missing context ("Processing record" without saying which record)

---

### 5. TRACE - Fine-Grained Diagnostics Including Variable States

**When to use:**
- Loop iterations with per-item details
- Variable states at decision points
- Detailed algorithm steps (parsing, validation)
- Framework internals (filter execution, interceptor chains)
- Performance profiling (enter/exit with timestamps)

**What to include:**
- Variable names and values
- Iteration index
- Execution path details

**Example:**
```java
log.trace("Parsing PubMed XML: processing author element {}/{}", i + 1, authorCount);
log.trace("Author details: lastName={}, foreName={}, affiliation={}", lastName, foreName, affiliation);
```

**Anti-patterns:**
- ❌ Using TRACE in production by default (only enable for active debugging)
- ❌ Logging sensitive data without sanitization
- ❌ Excessive TRACE logging causing performance issues (use sampling)

---

## Decision Tree for Log Level Selection

```
Is the operation failing AND preventing normal system function?
├─ YES → ERROR
└─ NO
    └─ Is there an issue BUT system recovered?
        ├─ YES → WARN
        └─ NO
            └─ Is this a key business event or state change?
                ├─ YES → INFO
                └─ NO
                    └─ Is this needed for troubleshooting detailed flow?
                        ├─ YES → DEBUG
                        └─ NO
                            └─ Is this fine-grained diagnostic data?
                                ├─ YES → TRACE
                                └─ NO → Don't log it
```

---

## Production vs Development Log Level Defaults

### Production (Baseline)
```yaml
logging:
  level:
    root: INFO
    com.papertrace: INFO
    com.papertrace.starter.logging: WARN
    org.springframework: WARN
    com.baomidou.mybatisplus: WARN
```

**Rationale:**
- INFO captures all key business events (SC-004 target: 40% volume reduction)
- WARN/ERROR capture all issues requiring attention
- DEBUG/TRACE disabled to minimize performance impact (<5% throughput impact per SC-004)

### Development (Troubleshooting)
```yaml
logging:
  level:
    root: INFO
    com.papertrace: DEBUG
    com.papertrace.registry.app: TRACE  # Fine-grained debugging
    com.baomidou.mybatisplus: DEBUG     # SQL query logging
```

**Rationale:**
- DEBUG enables detailed troubleshooting without overwhelming logs
- TRACE used sparingly for specific packages under active development
- External libraries kept at WARN to reduce noise

---

## Layer-Specific Guidelines

### Adapter Layer (Controllers, Jobs, MQ Listeners)
- **INFO**: Request received, batch job started/completed, message consumed
- **DEBUG**: Request/response details, validation errors, message headers
- **TRACE**: Full request/response payloads (sanitized)

### Application Layer (Orchestrators)
- **INFO**: Use case started/completed with outcome
- **DEBUG**: Orchestration flow, cross-aggregate coordination, transaction boundaries
- **TRACE**: Method entry/exit with parameters

### Domain Layer (Pure Java)
- **WARN**: Business rule violations, validation failures
- **DEBUG**: Business logic evaluation, decision points
- **TRACE**: Variable states, algorithm steps

### Infrastructure Layer (Repositories, External Clients)
- **INFO**: External API calls (FR-006: URL, status, duration, error)
- **ERROR**: Database failures (FR-007: query type, table, exception)
- **DEBUG**: SQL queries, API request/response details, DB query execution time
- **TRACE**: Full query parameters, API payloads

---

## Anti-Patterns to Avoid

### 1. String Concatenation (Violates FR-012)
❌ **Bad:**
```java
log.info("Processing batch " + batchId + " with " + recordCount + " records");
```

✅ **Good:**
```java
log.info("Processing batch {} with {} records", batchId, recordCount);
```

**Why:** String concatenation is evaluated even if log level is disabled, wasting CPU. Parameterized logging defers evaluation until needed.

---

### 2. Logging Sensitive Data Without Sanitization (Violates FR-008, SC-006)
❌ **Bad:**
```java
log.debug("User credentials: username={}, password={}", username, password);
```

✅ **Good:**
```java
log.debug("User credentials: username={}, password=[REDACTED]", username);
// OR use sanitizer:
log.debug("User data: {}", sanitizer.sanitize(userData));
```

**Why:** SC-006 mandates zero sensitive data in production logs. Passwords, tokens, PII must be sanitized.

---

### 3. Logging in Tight Loops Without Sampling
❌ **Bad:**
```java
for (Article article : articles) {
  log.info("Processing article PMID={}", article.getPmid());  // 10,000 logs for 10k articles!
}
```

✅ **Good:**
```java
// Option 1: Aggregated summary
log.info("Processing {} articles", articles.size());
for (Article article : articles) {
  // ... process ...
}
log.info("Completed processing: {} success, {} errors", successCount, errorCount);

// Option 2: Per-item at DEBUG (filtered out in production)
for (Article article : articles) {
  log.debug("Processing article PMID={}", article.getPmid());
}
```

**Why:** High-frequency logging causes performance issues and log volume explosion (violates SC-004).

---

### 4. Missing Context in Exception Logs
❌ **Bad:**
```java
try {
  processRecord(record);
} catch (Exception e) {
  log.error("Error processing record", e);  // Which record? What operation?
}
```

✅ **Good:**
```java
try {
  processRecord(record);
} catch (Exception e) {
  log.error(
    "Failed to process article PMID={} in batch batchId={}: {}",
    record.getPmid(),
    batchId,
    e.getMessage(),
    e
  );
}
```

**Why:** SC-001 target: diagnose issues in <10 minutes. Context is critical for troubleshooting.

---

### 5. Logging Both Cause and Effect (Redundant)
❌ **Bad:**
```java
log.error("Database connection failed: {}", e.getMessage());
throw new DatabaseConnectionException("Database unavailable", e);  // Exception also logged by aspect
```

✅ **Good:**
```java
// Let ExceptionLoggingAspect handle the logging at controller boundary
throw new DatabaseConnectionException("Database unavailable", e);
```

**Why:** Reduces log volume (SC-004). Exception logging aspect (T031) automatically logs exceptions at controller/job boundaries with full context.

---

## Performance Considerations

### Async Appenders (Configured in logback-spring.xml)
- All log writes are asynchronous (non-blocking)
- Queue size: 256 (sufficient for burst logging)
- Discard policy: DEBUG/TRACE dropped under pressure, ERROR/WARN never dropped
- Target: <5% throughput impact (SC-004)

### Log Sampling for High-Frequency Events
- Automatically applied for DEBUG/TRACE logs exceeding 100 logs/sec
- ERROR/WARN logs never sampled
- Configuration: `SamplingFilter` (T075)

### Dynamic Log Level Changes (FR-011, SC-007)
- Use Nacos console to change log levels without restart
- Changes take effect within 60 seconds
- Enable DEBUG temporarily for troubleshooting, revert to INFO after resolution

---

## Testing Your Log Levels

### Unit Tests (Verify Log Output)
```java
@Test
void shouldLogBatchCompletionAtInfo() {
  // Capture log output
  logCaptor.setLevel(Level.INFO);

  orchestrator.processBatch(batchId);

  assertThat(logCaptor.getLogMessages())
    .anyMatch(msg -> msg.contains("Completed PubMed batch processing"));
}
```

### Integration Tests (Verify Trace Context)
```java
@Test
void shouldIncludeTraceIdInLogs() {
  traceContext.setTraceId("test-trace-123");

  orchestrator.processBatch(batchId);

  assertThat(logCaptor.getLogMessages())
    .anyMatch(msg -> msg.contains("test-trace-123"));
}
```

---

## References

- [Log Level Usage Examples by Layer](./log-level-examples.md)
- [Troubleshooting Log Levels](./troubleshooting-log-levels.md)
- [Quickstart Guide](../../specs/001-logging-starter/quickstart.md)
- FR-001: Log level definitions
- SC-004: 40% log volume reduction at INFO level
- SC-006: Zero sensitive data in logs
