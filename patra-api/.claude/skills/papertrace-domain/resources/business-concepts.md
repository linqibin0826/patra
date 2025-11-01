# Papertrace Core Business Concepts

## Overview

This document provides deep-dive explanations of Papertrace's core business concepts: **Provenance**, **Plan**, **Task**, **Slice**, and **Expression**. Understanding these concepts is essential for implementing features correctly.

---

## 1. Provenance

### Definition

**Provenance** represents a data source from which scientific literature is harvested. It encapsulates both the identity of the source (e.g., PubMed, Europe PMC, Crossref) and all configuration needed to interact with that source's API.

### Domain Model

```java
// patra-registry-domain
public record Provenance(
    ProvenanceCode provenanceCode,  // Business identifier (PUBMED, EPMC, CROSSREF)
    String name,                    // Display name
    String description,             // Human-readable description
    String baseUrl,                 // API base URL
    boolean isActive,               // Whether this source is operational
    Instant createdAt,
    Instant updatedAt
) {
    public boolean canHarvest() {
        return isActive && baseUrl != null;
    }
}
```

### ProvenanceCode

**ProvenanceCode** is a value object representing the business identifier for a Provenance:

```java
public record ProvenanceCode(String value) {
    public static final ProvenanceCode PUBMED = new ProvenanceCode("PUBMED");
    public static final ProvenanceCode EPMC = new ProvenanceCode("EPMC");
    public static final ProvenanceCode CROSSREF = new ProvenanceCode("CROSSREF");

    public ProvenanceCode {
        Objects.requireNonNull(value, "ProvenanceCode cannot be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("ProvenanceCode cannot be blank");
        }
    }
}
```

### Provenance Configuration Aggregate

The **ProvenanceConfiguration** aggregate groups all settings for a specific Provenance:

```java
public record ProvenanceConfiguration(
    Provenance provenance,
    WindowOffsetConfig windowOffset,
    PaginationConfig pagination,
    HttpConfig http,
    BatchingConfig batching,
    RetryConfig retry,
    RateLimitConfig rateLimit
) {
    public boolean isComplete() {
        return provenance != null &&
               provenance.isActive() &&
               windowOffset != null &&
               pagination != null &&
               http != null;
    }
}
```

**Configuration Components:**

1. **WindowOffsetConfig**: Defines how to slice time windows
   - `sliceDuration`: Size of each time slice (e.g., 1 month)
   - `lookbackPeriod`: How far back to harvest by default
   - `maxWindowSize`: Maximum allowed time window

2. **PaginationConfig**: API pagination strategy
   - `pageSize`: Number of records per page
   - `strategy`: OFFSET, CURSOR, or PAGE_NUMBER
   - `maxPages`: Safety limit to prevent infinite loops

3. **HttpConfig**: HTTP client settings
   - `connectTimeout`: Connection timeout in seconds
   - `readTimeout`: Read timeout in seconds
   - `headers`: Default HTTP headers (e.g., API keys)
   - `userAgent`: User-Agent string

4. **BatchingConfig**: Batch processing parameters
   - `batchSize`: Number of Tasks to process in parallel
   - `maxConcurrency`: Thread pool size

5. **RetryConfig**: Retry strategy
   - `maxRetries`: Maximum retry attempts
   - `backoffStrategy`: FIXED, EXPONENTIAL, or LINEAR
   - `initialDelay`: First retry delay
   - `maxDelay`: Maximum retry delay

6. **RateLimitConfig**: Rate limiting
   - `requestsPerSecond`: Max requests/second
   - `requestsPerMinute`: Max requests/minute
   - `burst Size`: Burst capacity for token bucket

---

## 2. Plan (BatchPlan)

### Definition

A **Plan** represents a batch processing job that harvests data from a specific Provenance within a defined time window. Plans are broken down into Slices for parallel processing.

### Domain Model

```java
// patra-ingest-domain
public class BatchPlan {
    private PlanId id;
    private ProvenanceCode provenanceCode;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private PlanStatus status;
    private String createdBy;
    private List<Slice> slices;
    private Instant createdAt;
    private Instant updatedAt;

    // Business logic
    public void markAsRunning() {
        if (this.status != PlanStatus.PENDING) {
            throw new PlanException("Can only start a PENDING plan");
        }
        this.status = PlanStatus.RUNNING;
    }

    public void markAsCompleted() {
        if (this.status != PlanStatus.RUNNING) {
            throw new PlanException("Can only complete a RUNNING plan");
        }
        this.status = PlanStatus.COMPLETED;
        this.updatedAt = Instant.now();
    }

    public Duration timeWindow() {
        return Duration.between(startTime, endTime);
    }
}
```

### PlanStatus

```java
public enum PlanStatus {
    PENDING,      // Created, not yet started
    RUNNING,      // In progress
    COMPLETED,    // Successfully finished
    CANCELLED,    // User cancelled
    FAILED        // Unrecoverable error
}
```

### State Transitions

```
        ┌─────────┐
        │ PENDING │
        └────┬────┘
             │
    markAsRunning()
             │
        ┌────▼────┐
        │ RUNNING │
        └────┬────┘
             │
             ├──── markAsCompleted() ──→ ┌───────────┐
             │                             │ COMPLETED │
             │                             └───────────┘
             ├──── cancel() ────────────→ ┌───────────┐
             │                             │ CANCELLED │
             │                             └───────────┘
             └──── markAsFailed() ──────→ ┌─────────┐
                                            │ FAILED  │
                                            └─────────┘
```

### Plan Creation Workflow

**Input**:
- `ProvenanceCode`
- `startTime` and `endTime` (time window)
- `createdBy` (user identifier)

**Steps**:
1. Load `ProvenanceConfiguration` from registry
2. Validate time window (not too large, within lookback period)
3. Generate Slices based on `WindowOffsetConfig.sliceDuration`
4. Persist Plan with status = PENDING
5. Emit `PlanCreatedEvent`

**Output**: Saved Plan with generated Slices

---

## 3. Slice

### Definition

A **Slice** is a subdivison of a Plan's time window. Each Slice represents a smaller time range that can be processed independently, enabling parallelism and fault isolation.

### Domain Model

```java
public record Slice(
    SliceId id,
    PlanId planId,
    LocalDateTime startTime,
    LocalDateTime endTime,
    int estimatedRecordCount,
    SliceStatus status
) {
    public Duration duration() {
        return Duration.between(startTime, endTime);
    }

    public boolean isLarge() {
        return estimatedRecordCount > 10_000;
    }
}
```

### Slicing Strategy

**Purpose**: Break large time windows into manageable chunks.

**Example**:
```
Plan: PUBMED, 2024-01-01 to 2024-12-31

sliceDuration = 1 MONTH

Slices:
  1. 2024-01-01 to 2024-01-31 (~50k records)
  2. 2024-02-01 to 2024-02-29 (~48k records)
  3. 2024-03-01 to 2024-03-31 (~51k records)
  ...
  12. 2024-12-01 to 2024-12-31 (~49k records)
```

**Benefits**:
- **Parallelism**: Process multiple Slices concurrently
- **Fault Isolation**: One Slice failure doesn't affect others
- **Rate Limiting**: Smaller API requests stay under provider limits
- **Progress Tracking**: Can show % completion by Slice

### Slice to Task Mapping

Each Slice may generate multiple Tasks depending on estimated record count:

```
if estimatedRecordCount < 1000:
    create 1 Task
elif estimatedRecordCount < 10000:
    create 5 Tasks (split by pagination)
else:
    create 10 Tasks
```

---

## 4. Task (BatchTask)

### Definition

A **Task** is an atomic unit of work that makes a single API call to a Provenance endpoint. Tasks are idempotent and retriable.

### Domain Model

```java
public record BatchTask(
    TaskId id,
    PlanId planId,
    SliceId sliceId,
    ProvenanceCode provenanceCode,
    String businessKey,         // Idempotency key
    Map<String, Object> params, // API request parameters
    TaskStatus status,
    int retryCount,
    Integer recordsFetched,
    String errorMessage,
    Instant createdAt,
    Instant updatedAt
) {
    public boolean canRetry(int maxRetries) {
        return status == TaskStatus.FAILED && retryCount < maxRetries;
    }

    public boolean isIdempotent(BatchTask other) {
        return this.businessKey.equals(other.businessKey);
    }
}
```

### TaskStatus

```java
public enum TaskStatus {
    PENDING,      // Queued for execution
    RUNNING,      // Currently executing
    SUCCEEDED,    // Successfully completed
    FAILED,       // Failed but retriable
    EXHAUSTED     // Max retries reached
}
```

### Business Key Design

**Purpose**: Ensure idempotency - same parameters = same Task.

**Formula**:
```java
String businessKey = DigestUtils.md5Hex(
    provenanceCode.value() +
    params.get("mindate") +
    params.get("maxdate") +
    params.get("page") +
    params.get("pageSize")
);
```

**Effect**:
- Duplicate Tasks (same business key) are skipped
- Safe to retry: re-running creates Task with same business key
- Database unique constraint on `business_key` prevents duplicates

### Task Execution Workflow

**Input**: BatchTask

**Steps**:
1. Check if Task with same `businessKey` exists (idempotency check)
2. If exists, skip execution
3. Render API parameters using Expression engine
4. Make HTTP call to Provenance API
5. Parse response and extract records
6. Send records to patra-storage
7. Update Task status:
   - `SUCCEEDED` if successful
   - `FAILED` if error (will retry)
   - `EXHAUSTED` if max retries reached

**Output**: Updated Task with status and recordsFetched count

---

## 5. Expression

### Definition

An **Expression** maps abstract query fields to provider-specific API parameters. This allows Papertrace to use a unified query model while supporting heterogeneous APIs.

### Domain Model

```java
public record Expression(
    ExpressionId id,
    ProvenanceCode provenanceCode,
    String exprField,       // Abstract field name (e.g., "publicationDate")
    String capability,      // Query capability (e.g., "range", "exact")
    String renderRule,      // Template with placeholders (e.g., "mindate={start}")
    boolean isActive
) {
    public String render(Map<String, Object> values) {
        String result = renderRule;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue().toString());
        }
        return result;
    }
}
```

### Expression Fields

**ExprField**: Abstract field from Papertrace's query model
- `publicationDate`: When the article was published
- `author`: Author name
- `title`: Article title
- `journal`: Journal name
- `keyword`: Subject keyword

### Capabilities

**Capability**: Type of query operation supported

| Capability | Description | Example Query |
|------------|-------------|---------------|
| `exact` | Exact match | `author=Smith` |
| `range` | Range query | `publicationDate=[2024-01-01,2024-12-31]` |
| `wildcard` | Pattern match | `title=*COVID*` |
| `fuzzy` | Fuzzy match | `keyword~virus` |

### Render Rules

**RenderRule**: Provider-specific template with placeholders

**Examples**:

| Provider | ExprField | Capability | RenderRule |
|----------|-----------|------------|------------|
| PubMed | publicationDate | range | `mindate={start}&maxdate={end}` |
| EPMC | publicationDate | range | `PUB_YEAR:[{start} TO {end}]` |
| Crossref | publicationDate | range | `from-pub-date={start}&until-pub-date={end}` |

### Expression Rendering Example

**Query (Abstract)**:
```json
{
  "exprField": "publicationDate",
  "capability": "range",
  "values": {
    "start": "2024-01-01",
    "end": "2024-12-31"
  }
}
```

**PubMed Expression**:
```java
Expression expr = new Expression(
    id,
    ProvenanceCode.PUBMED,
    "publicationDate",
    "range",
    "mindate={start}&maxdate={end}",
    true
);

String rendered = expr.render(Map.of(
    "start", "2024-01-01",
    "end", "2024-12-31"
));
// Result: "mindate=2024-01-01&maxdate=2024-12-31"
```

**EPMC Expression**:
```java
Expression expr = new Expression(
    id,
    ProvenanceCode.EPMC,
    "publicationDate",
    "range",
    "PUB_YEAR:[{start} TO {end}]",
    true
);

String rendered = expr.render(Map.of(
    "start", "2024",
    "end", "2024"
));
// Result: "PUB_YEAR:[2024 TO 2024]"
```

---

## 6. Relationships Between Concepts

### Hierarchy

```
Provenance (PUBMED)
  │
  ├─ ProvenanceConfiguration
  │   ├─ WindowOffsetConfig (1 month slices)
  │   ├─ PaginationConfig (1000 per page)
  │   ├─ HttpConfig (30s timeout)
  │   ├─ RetryConfig (3 retries)
  │   └─ RateLimitConfig (10 req/s)
  │
  ├─ Expressions (multiple)
  │   ├─ Expression (publicationDate, range, "mindate={start}&maxdate={end}")
  │   ├─ Expression (author, exact, "author={value}")
  │   └─ Expression (keyword, wildcard, "term={value}")
  │
  └─ Plans (multiple)
      ├─ Plan #1 (2024-01-01 to 2024-12-31)
      │   ├─ Slice #1 (2024-01-01 to 2024-01-31)
      │   │   ├─ Task #1 (page=1, mindate=2024-01-01, maxdate=2024-01-31)
      │   │   ├─ Task #2 (page=2, mindate=2024-01-01, maxdate=2024-01-31)
      │   │   └─ Task #3 (page=3, mindate=2024-01-01, maxdate=2024-01-31)
      │   ├─ Slice #2 (2024-02-01 to 2024-02-29)
      │   │   └─ ...
      │   └─ Slice #12 (2024-12-01 to 2024-12-31)
      │
      └─ Plan #2 (2023-01-01 to 2023-12-31)
          └─ ...
```

### Data Flow

```
1. User creates Plan (Provenance, Time Window)
   ↓
2. patra-ingest loads ProvenanceConfiguration from patra-registry
   ↓
3. patra-ingest generates Slices based on WindowOffsetConfig
   ↓
4. patra-ingest creates Tasks for each Slice
   ↓
5. Task executor loads Expressions from patra-registry
   ↓
6. Task executor renders API params using Expressions
   ↓
7. Task executor calls Provenance API
   ↓
8. Task executor sends results to patra-storage
   ↓
9. patra-storage indexes and stores documents
```

---

## 7. Common Misunderstandings

### ❌ "Provenance is just an API wrapper"

**Wrong**: Provenance is a business concept (data source) with rich configuration.

**Correct**: Provenance encapsulates:
- Identity (code, name)
- API access (base URL, auth)
- Harvesting strategy (window, pagination, retry)
- Query translation (Expressions)

### ❌ "Plan = batch job"

**Wrong**: A Plan is not a generic batch job framework.

**Correct**: A Plan is a **time-windowed data harvesting job** specific to a single Provenance. It enforces:
- Temporal slicing (manageable API calls)
- Idempotency (business key)
- Retry logic (configurable)

### ❌ "Tasks can be executed in any order"

**Wrong**: Tasks have dependencies based on pagination.

**Correct**: Tasks within a Slice may have ordering constraints (e.g., sequential pages). However, Tasks from different Slices are independent.

### ❌ "Expressions are just string templates"

**Wrong**: Expressions are domain objects with validation and semantics.

**Correct**: Expressions:
- Enforce capability constraints (not all fields support all capabilities)
- Validate render rules (syntax, placeholders)
- Support versioning (different rules for different API versions)

---

## 8. Real-World Example

### Scenario: Harvesting 2024 PubMed Data

**Step 1: Create Provenance Configuration**

```java
Provenance pubmed = new Provenance(
    ProvenanceCode.PUBMED,
    "PubMed",
    "NCBI PubMed database",
    "https://eutils.ncbi.nlm.nih.gov/entrez/eutils",
    true,
    Instant.now(),
    Instant.now()
);

WindowOffsetConfig window = new WindowOffsetConfig(
    Duration.ofDays(30),  // 1-month slices
    Duration.ofDays(365), // 1-year lookback
    Duration.ofDays(180)  // Max 6-month window
);

PaginationConfig pagination = new PaginationConfig(
    1000,                    // 1000 records per page
    PaginationStrategy.OFFSET,
    100                      // Max 100 pages
);
```

**Step 2: Define Expressions**

```java
Expression dateRangeExpr = new Expression(
    id,
    ProvenanceCode.PUBMED,
    "publicationDate",
    "range",
    "mindate={start}&maxdate={end}",
    true
);
```

**Step 3: Create Plan**

```java
Plan plan = new BatchPlan(
    PlanId.generate(),
    ProvenanceCode.PUBMED,
    LocalDateTime.of(2024, 1, 1, 0, 0),  // Start
    LocalDateTime.of(2024, 12, 31, 23, 59), // End
    PlanStatus.PENDING,
    "user123",
    List.of(),  // Slices generated later
    Instant.now(),
    Instant.now()
);
```

**Step 4: Generate Slices**

```
Slice 1: 2024-01-01 to 2024-01-31 (~50,000 records)
  → Create 10 Tasks (5000 records each)
Slice 2: 2024-02-01 to 2024-02-29 (~48,000 records)
  → Create 10 Tasks
...
Slice 12: 2024-12-01 to 2024-12-31 (~49,000 records)
  → Create 10 Tasks

Total: 120 Tasks
```

**Step 5: Execute Tasks**

```
Task #1:
  businessKey = md5("PUBMED_mindate=2024-01-01_maxdate=2024-01-31_page=1")
  params = {
    "mindate": "2024-01-01",
    "maxdate": "2024-01-31",
    "retstart": "0",
    "retmax": "1000"
  }
  API Call: GET https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?
            db=pubmed&mindate=2024-01-01&maxdate=2024-01-31&retstart=0&retmax=1000
  Result: 1000 records → patra-storage
  Status: SUCCEEDED
```

---

## Summary

**Key Takeaways:**

1. **Provenance** = Data source + Configuration + Expressions
2. **Plan** = Time-windowed harvest job for a single Provenance
3. **Slice** = Subdivision of Plan for parallelism
4. **Task** = Atomic API call with idempotency
5. **Expression** = Abstract-to-concrete parameter mapping

**Design Principles:**

- **Idempotency**: Business key prevents duplicates
- **Fault Tolerance**: Retry logic + status tracking
- **Flexibility**: Expression engine supports heterogeneous APIs
- **Scalability**: Slicing enables parallel processing

**See Also:**
- [provenance-config-system.md](provenance-config-system.md) for configuration deep-dive
- [plan-task-workflow.md](plan-task-workflow.md) for execution workflows
- [expression-engine.md](expression-engine.md) for Expression details
