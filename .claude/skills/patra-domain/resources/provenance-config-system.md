# Provenance 配置系统

## 概览

Provenance 配置系统是 Patra 的**多维时态配置机制**,存储在 `patra-registry` 中并由 `patra-ingest` 消费。它管理从每个 Provenance 源采集数据的方式。

**Key Characteristics:**
- **Multi-Dimensional**: 7 independent configuration dimensions (Window, Pagination, HTTP, Batching, Retry, RateLimit, CircuitBreaker)
- **Temporal Validity**: Time-based configuration versioning with `[effective_from, effective_to)` windows
- **Operation-Specific**: Different configs for different operation types (HARVEST, UPDATE, BACKFILL, ALL)
- **Snapshot Isolation**: Configs are snapshotted at Plan creation time to prevent runtime changes

---

## Configuration Architecture

### Registry Schema Structure

**7 Configuration Tables** (in `patra-registry` database):

```
reg_prov_window_offset_cfg      - Window sizing and offset strategies
reg_prov_pagination_cfg         - Pagination modes and parameters
reg_prov_http_cfg               - HTTP client settings
reg_prov_batching_cfg           - Batch processing parallelism
reg_prov_retry_cfg              - Retry policies and backoff
reg_prov_rate_limit_cfg         - Rate limiting thresholds
reg_prov_circuit_breaker_cfg    - Circuit breaker settings
```

**Common Schema Pattern** (all tables share this structure):

```sql
CREATE TABLE reg_prov_<dimension>_cfg (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  provenance_id BIGINT NOT NULL,            -- FK to reg_provenance
  operation_type VARCHAR(32) NOT NULL,      -- 'HARVEST'/'UPDATE'/'BACKFILL'/'ALL'
  lifecycle_status_code VARCHAR(32),        -- 'ACTIVE', 'DEPRECATED', etc.
  effective_from TIMESTAMP(3) NOT NULL,     -- Config activation time
  effective_to TIMESTAMP(3),                -- Config expiration (NULL = forever)
  deleted TINYINT(1) DEFAULT 0,
  -- Dimension-specific fields...
  UNIQUE KEY uk_dimension(provenance_id, operation_type, effective_from)
);
```

### Precedence Rule (Operation-Specific)

**CRITICAL**: There is **NO** global/task hierarchy. Precedence is **operation-specific**:

```
Specific operation_type  >  operation_type='ALL'
```

**Selection Algorithm** (per dimension):

```sql
SELECT * FROM reg_prov_<dimension>_cfg
WHERE provenance_id = (SELECT id FROM reg_provenance WHERE provenance_code = ?)
  AND operation_type IN (?, 'ALL')  -- Specific operation or 'ALL'
  AND lifecycle_status_code = 'ACTIVE'
  AND deleted = 0
  AND effective_from <= ?
  AND (effective_to IS NULL OR effective_to > ?)
ORDER BY
  CASE WHEN operation_type = ? THEN 1 ELSE 2 END,  -- Specific > 'ALL'
  effective_from DESC,                              -- Newest wins
  id DESC
LIMIT 1;
```

**Example**:
```
Query: provenanceCode=PUBMED, operationType=HARVEST, at=2024-11-02T12:00:00Z

Results:
1. (provenance=PUBMED, operation=HARVEST, effective_from=2024-01-01) ✅ SELECTED
2. (provenance=PUBMED, operation=ALL, effective_from=2024-01-01)     ⏭️  Skipped

→ Uses config #1 (specific HARVEST overrides 'ALL')
```

---

## Configuration Dimensions

### 1. Window/Offset Configuration

**Table**: `reg_prov_window_offset_cfg`

**目的**: Controls time window sizing and offset calculation strategies.

**Key Fields**:

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `window_mode_code` | VARCHAR | Window mode: TIME/DATE/SINGLE | 'DATE' |
| `window_size_value` | INT | Window size numeric value | 30 |
| `window_size_unit_code` | VARCHAR | ChronoUnit: DAYS/HOURS/MONTHS | 'DAYS' |
| `offset_type_code` | VARCHAR | Offset strategy: WATERMARK/FIXED/RELATIVE | 'WATERMARK' |
| `offset_field_key` | VARCHAR | Field for offset extraction | 'publication_date' |
| `offset_date_format` | VARCHAR | Date format pattern | 'yyyy-MM-dd' |
| `watermark_lag_seconds` | BIGINT | Watermark lag (seconds) | 86400 (1 day) |

**Window Modes**:
- **TIME**: Absolute timestamp-based windows (uses Instant)
- **DATE**: Date-only windows (uses LocalDate, ignores time)
- **SINGLE**: No slicing, entire window in one piece

**Offset Types**:
- **WATERMARK**: Use last successful cursor value
- **FIXED**: Fixed start date (e.g., "2020-01-01")
- **RELATIVE**: Relative to current time (e.g., "now - 365 days")

---

### 2. Pagination Configuration

**Table**: `reg_prov_pagination_cfg`

**目的**: Defines pagination strategy and parameters.

**Key Fields**:

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `pagination_mode_code` | VARCHAR | Mode: PAGE_NUMBER/CURSOR/TOKEN/SCROLL | 'PAGE_NUMBER' |
| `page_size` | INT | Records per page | 1000 |
| `max_pages` | INT | Safety limit (max pages) | 100 |
| `cursor_field_key` | VARCHAR | Cursor field for CURSOR mode | 'next_cursor' |
| `token_field_key` | VARCHAR | Token field for TOKEN mode | 'continuation_token' |

**Pagination Modes**:

| Mode | API Pattern | Use Case |
|------|-------------|----------|
| **PAGE_NUMBER** | `page=1&size=100` | Traditional REST APIs |
| **CURSOR** | `cursor=abc123&limit=100` | Modern APIs (opaque cursor) |
| **TOKEN** | `token=xyz789&pageSize=100` | Continuation token pattern |
| **SCROLL** | `scroll_id=...&scroll=5m` | Elasticsearch-style |

**Example** (PubMed):
- Mode: PAGE_NUMBER
- page_size: 1000
- API call: `retstart=0&retmax=1000`

---

### 3. HTTP Configuration

**Table**: `reg_prov_http_cfg`

**目的**: HTTP client settings.

**Key Fields**:

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `request_method_code` | VARCHAR | HTTP method: GET/POST | 'GET' |
| `timeout_seconds` | INT | Request timeout | 30 |
| `headers` | JSON | Default HTTP headers | `{"User-Agent":"Patra/1.0"}` |

---

### 4. Batching Configuration

**Table**: `reg_prov_batching_cfg`

**目的**: Batch processing parallelism.

**Key Fields**:

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `batch_size` | INT | Tasks per batch | 50 |
| `max_concurrency` | INT | Parallel threads | 10 |
| `batch_delay_millis` | BIGINT | Delay between batches (ms) | 5000 |

---

### 5. Retry Configuration

**Table**: `reg_prov_retry_cfg`

**目的**: Retry policies for failed requests.

**Key Fields**:

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `max_retries` | INT | Maximum retry attempts | 5 |
| `backoff_strategy_code` | VARCHAR | FIXED/LINEAR/EXPONENTIAL | 'EXPONENTIAL' |
| `initial_delay_millis` | BIGINT | Initial retry delay (ms) | 5000 |
| `max_delay_millis` | BIGINT | Max retry delay (ms) | 60000 |
| `retriable_http_codes` | JSON | Retry-eligible HTTP codes | `[429,503,504]` |

---

### 6. Rate Limit Configuration

**Table**: `reg_prov_rate_limit_cfg`

**目的**: API rate limiting thresholds.

**Key Fields**:

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `requests_per_second` | INT | Max requests/second | 10 |
| `requests_per_minute` | INT | Max requests/minute | 600 |
| `burst_size` | INT | Token bucket burst capacity | 20 |

---

### 7. Circuit Breaker Configuration

**Table**: `reg_prov_circuit_breaker_cfg`

**目的**: Circuit breaker fault tolerance.

**Key Fields**:

| Field | Type | Description | Example |
|-------|------|-------------|---------|
| `failure_threshold` | INT | Failures before opening | 5 |
| `success_threshold` | INT | Successes before closing | 3 |
| `timeout_millis` | BIGINT | Half-open retry timeout | 60000 |

---

## Temporal Validity

### Time Window Pattern

Every configuration uses `[effective_from, effective_to)` for temporal validity:

```
[2024-01-01T00:00:00Z, 2024-06-30T23:59:59Z)  - Old config (expired)
[2024-07-01T00:00:00Z, NULL)                   - New config (active forever)
```

**Benefits**:
- Seamless config transitions (no downtime)
- Historical config tracking
- Reproducible plan creation (time-based)

**Query Pattern**:
```sql
WHERE effective_from <= ?
  AND (effective_to IS NULL OR effective_to > ?)
```

---

## Configuration Snapshot

### Snapshot Creation (Plan Level)

When a Plan is created, all 7 dimensions are **fetched and serialized** into `ing_plan.provenance_config_snapshot`:

**Flow**:
```
1. PatraRegistryPort.fetchConfig(provenanceCode, operationCode)
   ↓
2. ProvenanceClient.getConfiguration(...) → ProvenanceConfigResp (REST API)
   ↓
3. ProvenanceConfigSnapshotConverter.convert(resp)
   ↓
4. Returns: ProvenanceConfigSnapshot (domain VO)
   ↓
5. Store: JSON.stringify(snapshot) → ing_plan.provenance_config_snapshot
```

**Snapshot Structure** (Java VO in patra-ingest-domain):

```java
record ProvenanceConfigSnapshot(
    WindowOffsetConfig windowOffset,
    PaginationConfig pagination,
    HttpConfig http,
    BatchingConfig batching,
    RetryConfig retry,
    RateLimitConfig rateLimit,
    CircuitBreakerConfig circuitBreaker
)
```

**Storage**:
- **Database**: `ing_plan.provenance_config_snapshot` (JsonNode column)
- **Immutability**: Once snapshotted, never changes (even if registry config updates)

---

## Operation Types

**Enum**: `OperationType` (in `patra-registry`)

| Code | Description | Use Case |
|------|-------------|----------|
| **HARVEST** | Initial bulk ingestion | Large historical windows, aggressive rate limits |
| **UPDATE** | Incremental updates | Small recent windows, conservative rate |
| **BACKFILL** | Gap filling | Specific date ranges, medium rate |
| **ALL** | Fallback default | Used when no operation-specific config exists |

**Example**:
```
PUBMED/HARVEST config:
  - window_size: 30 days
  - rate_limit: 10 req/s

PUBMED/UPDATE config:
  - window_size: 1 day
  - rate_limit: 5 req/s
```

---

## Integration (Registry → Ingest)

### API Endpoint

**Registry exposes**:
```
GET /_internal/provenance/configuration?
    provenanceCode=PUBMED&
    operationType=HARVEST&
    at=2024-11-02T12:00:00Z
```

**Response** (ProvenanceConfigResp DTO):
```json
{
  "provenance": {...},
  "windowOffset": {...},
  "pagination": {...},
  "http": {...},
  "batching": {...},
  "retry": {...},
  "rateLimit": {...},
  "circuitBreaker": {...}
}
```

### Ingest Consumption

**PlanAssemblerImpl** (patra-ingest-app):
```java
// Fetch config from registry
ProvenanceConfigSnapshot configSnapshot =
    patraRegistryPort.fetchConfig(provenanceCode, operationCode);

// Snapshot to JSON
JsonNode configJson = objectMapper.valueToTree(configSnapshot);

// Create Plan with snapshot
PlanAggregate plan = PlanAggregate.create(
    ...,
    configJson,  // Stored in provenance_config_snapshot
    ...
);
```

---

## Best Practices

### 1. Define Operation-Specific Configs

**Good**:
```
PUBMED/HARVEST - Aggressive (30-day windows, 10 req/s)
PUBMED/UPDATE  - Conservative (1-day windows, 5 req/s)
```

**Bad**:
```
PUBMED/ALL - One-size-fits-all (suboptimal for all operations)
```

### 2. Use 'ALL' as Fallback

Create `operation_type='ALL'` config as a safety net when specific configs are missing.

### 3. Plan Config Transitions

**Smooth transition**:
```
Old config: [2024-01-01, 2024-12-31T23:59:59Z]
New config: [2025-01-01, NULL)
```

**Overlap**: No gap between configs ensures no downtime.

---

## Troubleshooting

### Issue: "No configuration found"

**Diagnosis**:
1. Check if config exists:
   ```sql
   SELECT * FROM reg_prov_window_offset_cfg
   WHERE provenance_id = (SELECT id FROM reg_provenance WHERE provenance_code='PUBMED')
     AND operation_type IN ('HARVEST', 'ALL');
   ```
2. Check temporal validity: `effective_from <= NOW() < effective_to`
3. Check lifecycle_status: Should be 'ACTIVE'

**Fix**: Create missing config or activate existing one.

---

### Issue: "Wrong config applied"

**Diagnosis**:
1. Check operation precedence: Specific > 'ALL'
2. Verify `operation_type` in request
3. Check `effective_from` ordering (newest wins)

**Fix**: Update config or adjust `effective_from` dates.

---

## Summary

**Key Differences from Original Documentation**:
- ❌ NO three-level hierarchy (TASK > SOURCE > GLOBAL)
- ✅ Multi-dimensional configuration (7 independent tables)
- ✅ Operation-specific precedence (specific operation > 'ALL')
- ✅ Snapshot isolation (immutable at Plan creation)
- ✅ Temporal validity for smooth transitions

**Configuration Lifecycle**:
```
1. Define in Registry DB (7 tables)
2. Query via REST API (at Plan creation time)
3. Snapshot in Plan (provenance_config_snapshot JSON)
4. Use immutable snapshot during execution
```
