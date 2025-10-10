# window_spec JSON Schema Documentation

> Database JSON column specification for window boundary definitions
> **Version**: 0.1.0 | **Format**: B (Nested) | **Last Updated**: 2025-10-10

---

## Overview

The `window_spec` JSON column is used in `ing_plan` and `ing_plan_slice` tables to store window boundary specifications. This document defines the complete JSON schema for all five supported strategies and explains the database virtual column implementation for efficient querying.

**Tables Using window_spec**:
- `ing_plan`: Plan-level window specification
- `ing_plan_slice`: Slice-level window specification (refined from plan window)

---

## JSON Schema by Strategy

### Format B Structure

All `window_spec` JSON documents follow this unified structure:

```json
{
  "strategy": "<STRATEGY_CODE>",
  // Strategy-specific fields below
}
```

**Common Fields**:
- `strategy` (string, required): Strategy code identifier
  - Valid values: `"TIME"`, `"ID_RANGE"`, `"CURSOR_LANDMARK"`, `"VOLUME_BUDGET"`, `"SINGLE"`, `"HYBRID"`
  - Case-sensitive
  - Maps to `SliceStrategy` enum in domain layer

---

### 1. TIME Strategy Schema

**JSON Structure**:
```json
{
  "strategy": "TIME",
  "window": {
    "from": "<ISO-8601 timestamp>",
    "to": "<ISO-8601 timestamp>",
    "boundary": {
      "from": "CLOSED",
      "to": "OPEN"
    },
    "timezone": "UTC"
  }
}
```

**Field Specifications**:

| JSON Path | Type | Required | Validation | Description |
|-----------|------|----------|------------|-------------|
| `$.strategy` | string | ✅ Yes | Must be `"TIME"` | Strategy identifier |
| `$.window` | object | ✅ Yes | Must be a valid JSON object | Nested window container |
| `$.window.from` | string | ✅ Yes | Valid ISO-8601 timestamp | Inclusive start timestamp |
| `$.window.to` | string | ✅ Yes | Valid ISO-8601 timestamp | Exclusive end timestamp |
| `$.window.boundary.from` | string | ✅ Yes | `"CLOSED"` or `"OPEN"` | Start boundary semantic |
| `$.window.boundary.to` | string | ✅ Yes | `"CLOSED"` or `"OPEN"` | End boundary semantic |
| `$.window.timezone` | string | ✅ Yes | Valid IANA timezone | Timezone identifier (default: `"UTC"`) |

**Example**:
```json
{
  "strategy": "TIME",
  "window": {
    "from": "2024-01-01T00:00:00Z",
    "to": "2024-12-31T23:59:59.999999Z",
    "boundary": {
      "from": "CLOSED",
      "to": "OPEN"
    },
    "timezone": "UTC"
  }
}
```

**Constraints**:
- `from` timestamp must be before or equal to `to` timestamp
- Timestamps must include timezone offset (recommended: use UTC suffix `Z`)
- Boundary semantics:
  - `CLOSED` = inclusive boundary (includes exact timestamp)
  - `OPEN` = exclusive boundary (excludes exact timestamp)
- Default convention: `[from, to)` (closed-open interval)

---

### 2. ID_RANGE Strategy Schema

**JSON Structure**:
```json
{
  "strategy": "ID_RANGE",
  "window": {
    "from": <numeric ID>,
    "to": <numeric ID>
  }
}
```

**Field Specifications**:

| JSON Path | Type | Required | Validation | Description |
|-----------|------|----------|------------|-------------|
| `$.strategy` | string | ✅ Yes | Must be `"ID_RANGE"` | Strategy identifier |
| `$.window` | object | ✅ Yes | Must be a valid JSON object | Nested window container |
| `$.window.from` | number | ✅ Yes | Positive integer | Inclusive start ID |
| `$.window.to` | number | ✅ Yes | Positive integer | Inclusive end ID |

**Example**:
```json
{
  "strategy": "ID_RANGE",
  "window": {
    "from": 30000000,
    "to": 31000000
  }
}
```

**Constraints**:
- Both boundaries are inclusive: `[from, to]`
- `from` must be less than or equal to `to`
- Values must be positive integers
- Typically used for sequential identifiers like PMIDs, article IDs

---

### 3. CURSOR_LANDMARK Strategy Schema

**JSON Structure**:
```json
{
  "strategy": "CURSOR_LANDMARK",
  "window": {
    "from": "<cursor token>",
    "to": "<cursor token>"
  }
}
```

**Field Specifications**:

| JSON Path | Type | Required | Validation | Description |
|-----------|------|----------|------------|-------------|
| `$.strategy` | string | ✅ Yes | Must be `"CURSOR_LANDMARK"` | Strategy identifier |
| `$.window` | object | ✅ Yes | Must be a valid JSON object | Nested window container |
| `$.window.from` | string | ✅ Yes | Non-blank string | Start cursor/token |
| `$.window.to` | string | ✅ Yes | Non-blank string | End cursor/token |

**Example**:
```json
{
  "strategy": "CURSOR_LANDMARK",
  "window": {
    "from": "eyJpZCI6MTAwMDAwMCwiZGlyZWN0aW9uIjoiRk9SV0FSRCJ9",
    "to": "eyJpZCI6MjAwMDAwMCwiZGlyZWN0aW9uIjoiRk9SV0FSRCJ9"
  }
}
```

**Constraints**:
- Tokens are treated as opaque strings (no structure validation)
- Both tokens must be non-null and non-blank
- Token semantics are source-specific (e.g., base64-encoded JSON, GraphQL cursors)
- Boundary semantics depend on source API behavior

---

### 4. VOLUME_BUDGET Strategy Schema

**JSON Structure**:
```json
{
  "strategy": "VOLUME_BUDGET",
  "limit": <positive integer>,
  "unit": "<unit identifier>"
}
```

**Field Specifications**:

| JSON Path | Type | Required | Validation | Description |
|-----------|------|----------|------------|-------------|
| `$.strategy` | string | ✅ Yes | Must be `"VOLUME_BUDGET"` | Strategy identifier |
| `$.limit` | number | ✅ Yes | Positive integer | Maximum volume/count |
| `$.unit` | string | ✅ Yes | Non-blank string | Unit of measurement |

**Example**:
```json
{
  "strategy": "VOLUME_BUDGET",
  "limit": 100000,
  "unit": "RECORDS"
}
```

**Common Unit Values**:
- `"RECORDS"`: Number of records/documents
- `"BYTES"`: Byte count
- `"KB"`, `"MB"`, `"GB"`: Storage units
- Custom units as needed

**Constraints**:
- `limit` must be a positive integer (> 0)
- `unit` is a free-form string (no validation)
- Note: **No nested `window` object** (flat structure)

---

### 5. SINGLE Strategy Schema

**JSON Structure**:
```json
{
  "strategy": "SINGLE"
}
```

**Field Specifications**:

| JSON Path | Type | Required | Validation | Description |
|-----------|------|----------|------------|-------------|
| `$.strategy` | string | ✅ Yes | Must be `"SINGLE"` | Strategy identifier |

**Example**:
```json
{
  "strategy": "SINGLE"
}
```

**Constraints**:
- Minimal structure (only strategy field)
- Indicates no partitioning/windowing
- Represents "fetch all" or "single execution" semantics

---

### 6. HYBRID Strategy (Reserved)

**Status**: Not yet implemented

**Planned JSON Structure**:
```json
{
  "strategy": "HYBRID",
  "primary": {
    "strategy": "TIME",
    "window": { ... }
  },
  "secondary": {
    "strategy": "ID_RANGE",
    "window": { ... }
  }
}
```

**Purpose**: Combine multiple strategies for complex windowing scenarios

---

## Database Schema Definition

### Table: `ing_plan`

```sql
CREATE TABLE IF NOT EXISTS `ing_plan` (
    -- ... other columns ...

    -- Main JSON column
    `window_spec` JSON NOT NULL
        COMMENT 'Window boundary specification (Format B: nested JSON with strategy-specific structure)',

    -- Virtual columns for TIME strategy queries
    `window_from_time` TIMESTAMP(6) GENERATED ALWAYS AS (
        CASE
            WHEN JSON_UNQUOTE(JSON_EXTRACT(`window_spec`, '$.strategy')) = 'TIME'
            THEN STR_TO_DATE(
                JSON_UNQUOTE(JSON_EXTRACT(`window_spec`, '$.window.from')),
                '%Y-%m-%dT%H:%i:%s.%f'
            )
            ELSE NULL
        END
    ) VIRTUAL COMMENT 'Virtual column for time-range queries on window start boundary',

    `window_to_time` TIMESTAMP(6) GENERATED ALWAYS AS (
        CASE
            WHEN JSON_UNQUOTE(JSON_EXTRACT(`window_spec`, '$.strategy')) = 'TIME'
            THEN STR_TO_DATE(
                JSON_UNQUOTE(JSON_EXTRACT(`window_spec`, '$.window.to')),
                '%Y-%m-%dT%H:%i:%s.%f'
            )
            ELSE NULL
        END
    ) VIRTUAL COMMENT 'Virtual column for time-range queries on window end boundary',

    -- ... other columns ...

    -- Indexes
    KEY `idx_window_time_range` (`window_from_time`, `window_to_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

### Table: `ing_plan_slice`

```sql
CREATE TABLE IF NOT EXISTS `ing_plan_slice` (
    -- ... other columns ...

    -- Main JSON column (similar structure to ing_plan)
    `window_spec` JSON NOT NULL
        COMMENT 'Slice-specific window boundary specification (Format B)',

    -- Virtual columns (same logic as ing_plan)
    `window_from_time` TIMESTAMP(6) GENERATED ALWAYS AS (
        CASE
            WHEN JSON_UNQUOTE(JSON_EXTRACT(`window_spec`, '$.strategy')) = 'TIME'
            THEN STR_TO_DATE(
                JSON_UNQUOTE(JSON_EXTRACT(`window_spec`, '$.window.from')),
                '%Y-%m-%dT%H:%i:%s.%f'
            )
            ELSE NULL
        END
    ) VIRTUAL,

    `window_to_time` TIMESTAMP(6) GENERATED ALWAYS AS (
        CASE
            WHEN JSON_UNQUOTE(JSON_EXTRACT(`window_spec`, '$.strategy')) = 'TIME'
            THEN STR_TO_DATE(
                JSON_UNQUOTE(JSON_EXTRACT(`window_spec`, '$.window.to')),
                '%Y-%m-%dT%H:%i:%s.%f'
            )
            ELSE NULL
        END
    ) VIRTUAL,

    -- ... other columns ...

    KEY `idx_slice_window_time` (`window_from_time`, `window_to_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
```

---

## Virtual Columns Explained

### Purpose

Virtual columns enable efficient time-range queries on `window_spec` JSON data without full table scans:

```sql
-- Fast query using virtual column index
SELECT * FROM ing_plan
WHERE window_from_time >= '2024-01-01'
  AND window_to_time <= '2024-12-31';

-- Slow query without virtual columns (JSON function calls)
SELECT * FROM ing_plan
WHERE JSON_UNQUOTE(JSON_EXTRACT(window_spec, '$.window.from')) >= '2024-01-01'
  AND JSON_UNQUOTE(JSON_EXTRACT(window_spec, '$.window.to')) <= '2024-12-31';
```

### JSON Path Extraction Logic

**Step 1: Check Strategy**
```sql
JSON_UNQUOTE(JSON_EXTRACT(`window_spec`, '$.strategy')) = 'TIME'
```
- Extracts `strategy` field from root level
- Only processes rows where strategy is `"TIME"`
- Returns `NULL` for non-TIME strategies

**Step 2: Extract Nested Timestamp**
```sql
STR_TO_DATE(
    JSON_UNQUOTE(JSON_EXTRACT(`window_spec`, '$.window.from')),
    '%Y-%m-%dT%H:%i:%s.%f'
)
```
- Navigates nested path: `window_spec` → `window` → `from`
- Unquotes JSON string: `"2024-01-01T00:00:00Z"` → `2024-01-01T00:00:00Z`
- Parses ISO-8601 format to MySQL `TIMESTAMP(6)` type

**Step 3: Virtual Column Result**
- `window_from_time`: Start timestamp or `NULL` (if not TIME strategy)
- `window_to_time`: End timestamp or `NULL` (if not TIME strategy)

### Index Strategy

```sql
-- Composite index for time range queries
KEY `idx_window_time_range` (`window_from_time`, `window_to_time`)
```

**Query Patterns Optimized**:
1. **Range queries**: Find plans within time window
2. **Overlap detection**: Find plans overlapping with specific period
3. **Sorting**: Order plans by start/end time
4. **Aggregation**: Group plans by time boundaries

**Performance Considerations**:
- Virtual columns have zero storage overhead (computed on-the-fly)
- Index on virtual columns enables B-tree access (no JSON scan)
- Only effective for TIME strategy (returns `NULL` for others)

---

## Query Examples

### Example 1: Find TIME Plans in Date Range

```sql
-- Find all plans with TIME strategy in January 2024
SELECT
    id,
    plan_key,
    provenance_code,
    JSON_EXTRACT(window_spec, '$.strategy') as strategy,
    window_from_time,
    window_to_time
FROM ing_plan
WHERE window_from_time >= '2024-01-01 00:00:00'
  AND window_to_time <= '2024-02-01 00:00:00';
```

### Example 2: Find Overlapping TIME Plans

```sql
-- Find plans that overlap with Q1 2024
SELECT *
FROM ing_plan
WHERE window_from_time < '2024-04-01 00:00:00'
  AND window_to_time > '2024-01-01 00:00:00';
```

### Example 3: Extract ID_RANGE Boundaries

```sql
-- Extract ID range boundaries for ID_RANGE plans
SELECT
    id,
    plan_key,
    JSON_UNQUOTE(JSON_EXTRACT(window_spec, '$.strategy')) as strategy,
    JSON_EXTRACT(window_spec, '$.window.from') as id_from,
    JSON_EXTRACT(window_spec, '$.window.to') as id_to
FROM ing_plan
WHERE JSON_UNQUOTE(JSON_EXTRACT(window_spec, '$.strategy')) = 'ID_RANGE';
```

### Example 4: Find VOLUME_BUDGET Plans by Limit

```sql
-- Find plans with volume limit >= 100000
SELECT
    id,
    plan_key,
    JSON_EXTRACT(window_spec, '$.strategy') as strategy,
    JSON_EXTRACT(window_spec, '$.limit') as volume_limit,
    JSON_UNQUOTE(JSON_EXTRACT(window_spec, '$.unit')) as volume_unit
FROM ing_plan
WHERE JSON_UNQUOTE(JSON_EXTRACT(window_spec, '$.strategy')) = 'VOLUME_BUDGET'
  AND JSON_EXTRACT(window_spec, '$.limit') >= 100000;
```

### Example 5: Count Plans by Strategy

```sql
-- Aggregate plans by strategy type
SELECT
    JSON_UNQUOTE(JSON_EXTRACT(window_spec, '$.strategy')) as strategy,
    COUNT(*) as plan_count
FROM ing_plan
GROUP BY JSON_UNQUOTE(JSON_EXTRACT(window_spec, '$.strategy'))
ORDER BY plan_count DESC;
```

---

## Validation Rules

### Database-Level Validation

MySQL JSON column type provides basic validation:
- Must be valid JSON syntax
- Automatically rejects malformed JSON

### Application-Level Validation

Domain layer (`WindowSpec.fromMap()`) enforces:

**All Strategies**:
- `strategy` field must be present and non-null
- `strategy` value must be a valid `SliceStrategy` code

**TIME**:
- `window` object must be present
- `window.from` and `window.to` must be valid ISO-8601 strings
- `from` must be before or equal to `to`

**ID_RANGE**:
- `window` object must be present
- `window.from` and `window.to` must be numeric
- `from` must be less than or equal to `to`

**CURSOR_LANDMARK**:
- `window` object must be present
- `window.from` and `window.to` must be non-blank strings

**VOLUME_BUDGET**:
- `limit` must be a positive integer
- `unit` must be a non-blank string

**SINGLE**:
- No additional fields required

---

## Migration Guide

### From Format A to Format B

If you have existing `window_spec` data in Format A (flat structure with `"type"` key), migrate using:

```sql
-- Migration script example (adjust as needed)
UPDATE ing_plan
SET window_spec = JSON_OBJECT(
    'strategy', JSON_UNQUOTE(JSON_EXTRACT(window_spec, '$.type')),
    'window', JSON_OBJECT(
        'from', JSON_UNQUOTE(JSON_EXTRACT(window_spec, '$.from')),
        'to', JSON_UNQUOTE(JSON_EXTRACT(window_spec, '$.to')),
        'boundary', JSON_OBJECT('from', 'CLOSED', 'to', 'OPEN'),
        'timezone', 'UTC'
    )
)
WHERE JSON_UNQUOTE(JSON_EXTRACT(window_spec, '$.type')) = 'TIME'
  AND JSON_EXTRACT(window_spec, '$.window') IS NULL;
```

**⚠️ Warning**: Test migration on non-production data first. Backup tables before migrating.

---

## Performance Optimization

### Index Recommendations

**For TIME strategy queries**:
```sql
-- Enable fast time-range lookups
CREATE INDEX idx_window_time_range
ON ing_plan (window_from_time, window_to_time);
```

**For strategy-specific queries**:
```sql
-- MySQL 8.0+ supports functional indexes on JSON paths
CREATE INDEX idx_window_strategy
ON ing_plan ((CAST(JSON_UNQUOTE(JSON_EXTRACT(window_spec, '$.strategy')) AS CHAR(32))));
```

### Query Optimization Tips

1. **Use virtual columns for TIME queries**: Faster than JSON_EXTRACT
2. **Avoid LIKE on JSON strings**: Use exact match with `JSON_UNQUOTE`
3. **Cache strategy checks**: If querying multiple fields, extract strategy once
4. **Consider materialized views**: For complex multi-strategy aggregations

---

## Related Documentation

- **Domain Model**: [docs/domain/WindowSpec.md](../domain/WindowSpec.md)
- **ER Diagrams**: [docs/database/er-diagrams.md](./er-diagrams.md)
- **Ingest Module**: [docs/modules/ingest/deep-dive.md](../modules/ingest/deep-dive.md)
- **Migration Script**: `patra-ingest-infra/src/main/resources/db/migration/V0.1.0__init_ingest_schema.sql`

---

## Changelog

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1.0 | 2025-10-10 | Initial Format B schema: 5 strategies, virtual columns, query examples | docs-engineer |

---

**License**

Copyright © 2025 Papertrace
