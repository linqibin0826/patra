# Provenance Configuration Value Objects

This package contains **immutable value objects** representing provenance metadata and operational configurations.

---

## Package Contents

### Core Entity

**Provenance.java** — Root provenance entity
- Represents external data source catalog entry
- Referenced by all `reg_prov_*` configuration tables

### Operational Configurations (Temporal)

All configs below support **time-effective slicing** (`effective_from`, `effective_until`):

| Config | Purpose | Key Fields |
|--------|---------|------------|
| **WindowOffsetConfig** | Time window segmentation | `startOffsetDays`, `lookbackWindowDays` |
| **PaginationConfig** | Pagination strategy | `pageSize`, `maxPages`, `cursorField` |
| **HttpConfig** | HTTP client settings | `baseUrl`, `connectTimeout`, `readTimeout`, `headers` |
| **BatchingConfig** | Batching rules | `batchSize`, `maxConcurrentBatches` |
| **RetryConfig** | Retry policy | `maxRetries`, `backoffMillis`, `retryableStatusCodes` |
| **RateLimitConfig** | Rate limiting | `requestsPerSecond`, `burstCapacity` |

---

## Temporal Configuration Pattern

### Concept

**Temporal Configuration** = Configs have time validity ranges, queries retrieve config effective at specific instant.

**Why?**
- Safe config updates without impacting running tasks
- Audit trail of config changes
- Gradual rollout / A/B testing support

### Schema Pattern

All `reg_prov_*_cfg` tables include:
```sql
effective_from   DATETIME NOT NULL,
effective_until  DATETIME NULL,
INDEX idx_temporal (provenance_id, operation_type, effective_from, effective_until)
```

### Query Pattern

```java
Optional<HttpConfig> findActiveHttpConfig(
    Long provenanceId,
    String operationType,  // "HARVEST", "UPDATE", null (=ALL)
    Instant at             // Query instant
);

// SQL:
// WHERE provenance_id = ?
//   AND (operation_type = ? OR operation_type IS NULL)
//   AND effective_from <= ?
//   AND (effective_until IS NULL OR effective_until > ?)
// ORDER BY effective_from DESC
// LIMIT 1
```

**Result**: Most recent config valid at instant `at`.

---

## Value Object Details

### 1. Provenance

**Purpose**: Core provenance catalog entry (external data source).

**Attributes**:
```java
public record Provenance(
    Long id,                     // PK
    String code,                 // Unique stable code (e.g., "pubmed")
    String name,                 // Display name (e.g., "PubMed")
    String baseUrlDefault,       // Default API base URL
    String timezoneDefault,      // Default timezone (IANA, e.g., "UTC")
    String docsUrl,              // Official docs URL
    boolean active,              // Is source active?
    String lifecycleStatusCode   // Dict code (lifecycle_status)
) { }
```

**Validation**:
- `code`, `name`, `timezoneDefault`, `lifecycleStatusCode`: NOT BLANK
- `id`: POSITIVE

**File**: [`Provenance.java`](Provenance.java:1)

---

### 2. WindowOffsetConfig

**Purpose**: Defines time window segmentation for collection.

**Attributes**:
```java
public record WindowOffsetConfig(
    Long id,
    Long provenanceId,
    String operationType,        // "HARVEST", "UPDATE", null (=SOURCE-level default)
    Integer startOffsetDays,     // How many days back from today to start
    Integer lookbackWindowDays,  // Size of collection window
    Instant effectiveFrom,
    Instant effectiveUntil
) { }
```

**Example**:
```
startOffsetDays = 7        → Start 7 days ago
lookbackWindowDays = 3     → Collect 3 days of data
Window = [today-7d, today-4d)
```

**Scope Precedence**: TASK-level (operationType != null) overrides SOURCE-level (operationType = null).

**File**: [`WindowOffsetConfig.java`](WindowOffsetConfig.java)

---

### 3. PaginationConfig

**Purpose**: Pagination strategy (offset-based, cursor-based, or page-based).

**Attributes**:
```java
public record PaginationConfig(
    Long id,
    Long provenanceId,
    String operationType,
    String paginationType,       // "OFFSET", "CURSOR", "PAGE"
    Integer pageSize,            // Records per page
    Integer maxPages,            // Max pages to fetch (safety limit)
    String cursorField,          // Cursor field name (for CURSOR type)
    Instant effectiveFrom,
    Instant effectiveUntil
) { }
```

**Pagination Types**:
- **OFFSET**: `?offset=0&limit=100`, `?offset=100&limit=100`, ...
- **CURSOR**: `?cursor=abc123`, `?cursor=xyz789`, ...
- **PAGE**: `?page=1`, `?page=2`, ...

**File**: [`PaginationConfig.java`](PaginationConfig.java)

---

### 4. HttpConfig

**Purpose**: HTTP client settings (timeouts, headers, base URL override).

**Attributes**:
```java
public record HttpConfig(
    Long id,
    Long provenanceId,
    String operationType,
    String baseUrl,              // Override provenance.baseUrlDefault
    Integer connectTimeoutMs,    // Connection timeout
    Integer readTimeoutMs,       // Read timeout
    String headersJson,          // Custom headers (JSON map)
    Instant effectiveFrom,
    Instant effectiveUntil
) { }
```

**Headers Format** (JSON):
```json
{
  "User-Agent": "Papertrace/1.0",
  "Accept": "application/json",
  "X-API-Key": "${env.PUBMED_API_KEY}"
}
```

**File**: [`HttpConfig.java`](HttpConfig.java)

---

### 5. BatchingConfig

**Purpose**: Batching rules for detail fetching (e.g., fetch 1000 IDs in batches of 100).

**Attributes**:
```java
public record BatchingConfig(
    Long id,
    Long provenanceId,
    String operationType,
    Integer batchSize,           // Items per batch
    Integer maxConcurrentBatches,  // Parallel batch execution limit
    Instant effectiveFrom,
    Instant effectiveUntil
) { }
```

**Use Case**:
- List API returns IDs: `[id1, id2, ..., id10000]`
- Detail API fetches full records: batch into `[id1..id100]`, `[id101..id200]`, ...
- Control concurrency to respect rate limits

**File**: [`BatchingConfig.java`](BatchingConfig.java)

---

### 6. RetryConfig

**Purpose**: Retry policy (max retries, backoff, retryable errors).

**Attributes**:
```java
public record RetryConfig(
    Long id,
    Long provenanceId,
    String operationType,
    Integer maxRetries,          // Max retry attempts
    Long backoffMillis,          // Fixed backoff delay (ms)
    String retryableStatusCodes, // Comma-separated HTTP codes (e.g., "429,502,503")
    Instant effectiveFrom,
    Instant effectiveUntil
) { }
```

**Backoff Strategy**: Fixed delay (future: exponential backoff).

**File**: [`RetryConfig.java`](RetryConfig.java)

---

### 7. RateLimitConfig

**Purpose**: Rate limiting (requests per second, burst capacity).

**Attributes**:
```java
public record RateLimitConfig(
    Long id,
    Long provenanceId,
    String operationType,
    Double requestsPerSecond,    // Sustained rate
    Integer burstCapacity,       // Max burst size
    Instant effectiveFrom,
    Instant effectiveUntil
) { }
```

**Algorithm**: Token bucket (future implementation).

**File**: [`RateLimitConfig.java`](RateLimitConfig.java)

---

## Scope Precedence

### Concept

Configs can exist at two levels:
1. **SOURCE-level** (`operation_type = NULL`): Applies to all operations
2. **TASK-level** (`operation_type = 'HARVEST'`): Applies only to specific operation

**Precedence**: TASK-level **overrides** SOURCE-level.

### Example

**Scenario**: PubMed has two retry configs:

| ID | provenance_id | operation_type | max_retries | effective_from |
|----|---------------|----------------|-------------|----------------|
| 1  | 1 (PubMed)    | NULL           | 3           | 2025-01-01     |
| 2  | 1 (PubMed)    | HARVEST        | 5           | 2025-01-10     |

**Query 1**: `findActiveRetry(provenanceId=1, operationType=UPDATE, at=2025-01-15)`
- Result: Config #1 (SOURCE-level, max_retries=3)

**Query 2**: `findActiveRetry(provenanceId=1, operationType=HARVEST, at=2025-01-15)`
- Result: Config #2 (TASK-level, max_retries=5) — **overrides** SOURCE-level

---

## Design Patterns

### 1. Records for Immutability

**All VOs are `record` types** → immutable by default.

```java
public record HttpConfig(...) { }

// No setters — construct new instance to "update"
HttpConfig updated = new HttpConfig(
    config.id(),
    config.provenanceId(),
    config.operationType(),
    "https://new-base-url.com",  // Changed
    config.connectTimeoutMs(),
    // ...
);
```

### 2. Canonical Constructor Validation

**Validation in compact constructor**:
```java
public record Provenance(String code, String name, ...) {
    public Provenance {
        Objects.requireNonNull(code, "code required");
        if (code.isBlank()) {
            throw new IllegalArgumentException("code cannot be blank");
        }
    }
}
```

### 3. Nullable Optional Fields

**Use `null` for optional fields** (not `Optional<T>` — records prefer null).

```java
public record WindowOffsetConfig(
    Long id,
    Long provenanceId,
    String operationType,        // Nullable (null = SOURCE-level)
    Integer startOffsetDays,     // Nullable (use provenance default)
    // ...
) { }
```

---

## Testing Guidelines

### Unit Tests

```java
@Test
void testProvenanceValidation() {
    // Given
    String blankCode = "   ";

    // When/Then
    assertThrows(IllegalArgumentException.class, () ->
        new Provenance(1L, blankCode, "name", null, "UTC", null, true, "ACTIVE")
    );
}
```

### Integration Tests (Repository)

```java
@Test
void testTemporalQuery() {
    // Given: Two configs for same provenance
    HttpConfig config1 = new HttpConfig(..., effectiveFrom=Jan1, effectiveUntil=Jan10);
    HttpConfig config2 = new HttpConfig(..., effectiveFrom=Jan10, effectiveUntil=null);

    repository.save(config1);
    repository.save(config2);

    // When: Query at Jan5
    Optional<HttpConfig> result = repository.findActiveHttpConfig(
        provenanceId, operationType, Instant.parse("2025-01-05T00:00:00Z")
    );

    // Then: Should return config1
    assertTrue(result.isPresent());
    assertEquals(config1.id(), result.get().id());
}
```

---

## Common Pitfalls

### ❌ DON'T: Query without temporal filtering

```java
// BAD: Returns all configs (ignores time validity)
List<HttpConfig> findByProvenanceId(Long provenanceId);
```

### ✅ DO: Always query with `at` parameter

```java
// GOOD: Returns config valid at specific instant
Optional<HttpConfig> findActiveHttpConfig(Long provenanceId, String operationType, Instant at);
```

### ❌ DON'T: Modify record fields (impossible, but don't try workarounds)

```java
// BAD: Records are immutable
config.setBaseUrl("new-url");  // ❌ Compile error
```

### ✅ DO: Create new instance

```java
// GOOD: Construct new record
HttpConfig updated = new HttpConfig(
    config.id(),
    config.provenanceId(),
    // ... copy fields, change only what's needed
);
```

---

**See Also**:
- [patra-registry README](../../../../../README.md)
- [Architecture Guide](../../../../../../../../docs/ARCHITECTURE.md)
- [ProvenanceConfigRepository](../../port/ProvenanceConfigRepository.java) — Port interface for queries
