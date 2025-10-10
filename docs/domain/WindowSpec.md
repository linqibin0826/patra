# WindowSpec - Window Boundary Specification

> Domain value object for defining data ingestion window boundaries
> **Version**: 0.1.0 | **Last Updated**: 2025-10-10 | **Format**: B (Nested JSON)

---

## Overview

`WindowSpec` is a sealed interface value object that defines window boundary specifications for data ingestion plans. It supports five distinct strategies for partitioning data collection tasks, each optimized for different data source characteristics and pagination patterns.

**Key Characteristics**:
- **Sealed Hierarchy**: Compile-time exhaustiveness ensures all strategies are handled
- **Immutable**: Implemented as Java records for thread-safety and value semantics
- **Strategy-Specific**: Each strategy has its own structure and validation rules
- **Persistence-Ready**: Bidirectional conversion between domain objects and JSON maps

**Location**: `patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/vo/WindowSpec.java`

---

## JSON Format (Format B - Nested Structure)

All `WindowSpec` instances serialize to a unified nested JSON format with the following structure:

```json
{
  "strategy": "STRATEGY_CODE",
  "window": { ... },  // Present for TIME, ID_RANGE, CURSOR_LANDMARK
  "limit": ...,       // Present for VOLUME_BUDGET
  "unit": ...         // Present for VOLUME_BUDGET
}
```

**Key Design Principles**:
1. **Unified Key**: All strategies use `"strategy"` (not `"type"`) to indicate the strategy code
2. **Nested Window**: TIME, ID_RANGE, and CURSOR_LANDMARK use a nested `"window"` object
3. **Flat Structure**: VOLUME_BUDGET and SINGLE use flat structure (no nesting needed)
4. **Type Safety**: Strategy enum maps to string codes for persistence

---

## Strategy Catalog

### 1. TIME - Time-Based Windows

**Use Case**: Time-series data sources with temporal boundaries (e.g., PubMed date ranges)

**Domain Model**:
```java
WindowSpec.Time(Instant from, Instant to)
```

**JSON Format (Format B)**:
```json
{
  "strategy": "TIME",
  "window": {
    "from": "2024-01-01T00:00:00Z",
    "to": "2024-12-31T23:59:59Z",
    "boundary": {
      "from": "CLOSED",
      "to": "OPEN"
    },
    "timezone": "UTC"
  }
}
```

**Field Descriptions**:
- `strategy`: Always `"TIME"`
- `window.from`: ISO-8601 start timestamp (inclusive)
- `window.to`: ISO-8601 end timestamp (exclusive)
- `window.boundary.from`: Boundary semantic (`CLOSED` = inclusive)
- `window.boundary.to`: Boundary semantic (`OPEN` = exclusive)
- `window.timezone`: Timezone identifier (default: `UTC`)

**Validation Rules**:
- Both `from` and `to` must be non-null
- `from` must be before or equal to `to`
- Timestamps must be valid ISO-8601 format

**Java Usage**:
```java
// Create
WindowSpec spec = WindowSpec.ofTime(
    Instant.parse("2024-01-01T00:00:00Z"),
    Instant.parse("2024-12-31T23:59:59Z")
);

// Serialize
Map<String, Object> map = spec.toMap();
// {"strategy":"TIME","window":{"from":"2024-01-01T00:00:00Z",...}}

// Deserialize
WindowSpec restored = WindowSpec.fromMap(map);
```

---

### 2. ID_RANGE - ID-Based Windows

**Use Case**: Data sources with sequential numeric identifiers (e.g., article IDs, PMID ranges)

**Domain Model**:
```java
WindowSpec.IdRange(Long from, Long to)
```

**JSON Format (Format B)**:
```json
{
  "strategy": "ID_RANGE",
  "window": {
    "from": 1000000,
    "to": 2000000
  }
}
```

**Field Descriptions**:
- `strategy`: Always `"ID_RANGE"`
- `window.from`: Start ID (inclusive)
- `window.to`: End ID (inclusive)

**Validation Rules**:
- Both `from` and `to` must be non-null
- `from` must be less than or equal to `to`
- Values must be positive Long integers

**Java Usage**:
```java
// Create
WindowSpec spec = WindowSpec.ofIdRange(1000000L, 2000000L);

// Serialize
Map<String, Object> map = spec.toMap();
// {"strategy":"ID_RANGE","window":{"from":1000000,"to":2000000}}

// Deserialize
WindowSpec restored = WindowSpec.fromMap(map);
```

---

### 3. CURSOR_LANDMARK - Cursor/Token-Based Windows

**Use Case**: Pagination-based APIs using opaque tokens or cursor strings (e.g., GraphQL cursors)

**Domain Model**:
```java
WindowSpec.CursorLandmark(String from, String to)
```

**JSON Format (Format B)**:
```json
{
  "strategy": "CURSOR_LANDMARK",
  "window": {
    "from": "eyJpZCI6MTAwMH0=",
    "to": "eyJpZCI6MjAwMH0="
  }
}
```

**Field Descriptions**:
- `strategy`: Always `"CURSOR_LANDMARK"`
- `window.from`: Start cursor/token (opaque string)
- `window.to`: End cursor/token (opaque string)

**Validation Rules**:
- Both `from` and `to` must be non-null and non-blank
- Tokens are treated as opaque strings (no internal validation)

**Java Usage**:
```java
// Create
WindowSpec spec = WindowSpec.ofCursor(
    "eyJpZCI6MTAwMH0=",
    "eyJpZCI6MjAwMH0="
);

// Serialize
Map<String, Object> map = spec.toMap();
// {"strategy":"CURSOR_LANDMARK","window":{"from":"eyJpZCI6MTAwMH0=","to":"eyJpZCI6MjAwMH0="}}

// Deserialize
WindowSpec restored = WindowSpec.fromMap(map);
```

---

### 4. VOLUME_BUDGET - Volume-Limited Windows

**Use Case**: Quota-based collection with explicit limits (e.g., "fetch up to 100,000 records")

**Domain Model**:
```java
WindowSpec.VolumeBudget(Integer limit, String unit)
```

**JSON Format (Format B)**:
```json
{
  "strategy": "VOLUME_BUDGET",
  "limit": 100000,
  "unit": "RECORDS"
}
```

**Field Descriptions**:
- `strategy`: Always `"VOLUME_BUDGET"`
- `limit`: Maximum volume/count (positive integer)
- `unit`: Unit of measurement (e.g., `"RECORDS"`, `"BYTES"`, `"MB"`)

**Validation Rules**:
- `limit` must be positive (> 0)
- `unit` must be non-null and non-blank

**Java Usage**:
```java
// Create
WindowSpec spec = WindowSpec.ofVolume(100000, "RECORDS");

// Serialize
Map<String, Object> map = spec.toMap();
// {"strategy":"VOLUME_BUDGET","limit":100000,"unit":"RECORDS"}

// Deserialize
WindowSpec restored = WindowSpec.fromMap(map);
```

---

### 5. SINGLE - Single Execution Window

**Use Case**: One-shot collection tasks without partitioning (e.g., fetch all available data)

**Domain Model**:
```java
WindowSpec.Single()
```

**JSON Format (Format B)**:
```json
{
  "strategy": "SINGLE"
}
```

**Field Descriptions**:
- `strategy`: Always `"SINGLE"` (no additional fields)

**Validation Rules**:
- No additional validation (strategy code is sufficient)

**Java Usage**:
```java
// Create
WindowSpec spec = WindowSpec.ofSingle();

// Serialize
Map<String, Object> map = spec.toMap();
// {"strategy":"SINGLE"}

// Deserialize
WindowSpec restored = WindowSpec.fromMap(map);
```

---

## SliceStrategy Enum Mapping

The `strategy` field in JSON maps to the `SliceStrategy` enum:

```java
public enum SliceStrategy {
    TIME("TIME"),
    ID_RANGE("ID_RANGE"),
    CURSOR_LANDMARK("CURSOR_LANDMARK"),
    VOLUME_BUDGET("VOLUME_BUDGET"),
    SINGLE("SINGLE"),
    HYBRID("HYBRID");  // Reserved for future use

    private final String code;

    public static Optional<SliceStrategy> fromCode(String code) {
        // Case-insensitive lookup by code
    }
}
```

**Mapping Table**:

| Strategy Code | Enum Value | WindowSpec Type | Nested Window? |
|---------------|------------|-----------------|----------------|
| `TIME` | `SliceStrategy.TIME` | `WindowSpec.Time` | ✅ Yes |
| `ID_RANGE` | `SliceStrategy.ID_RANGE` | `WindowSpec.IdRange` | ✅ Yes |
| `CURSOR_LANDMARK` | `SliceStrategy.CURSOR_LANDMARK` | `WindowSpec.CursorLandmark` | ✅ Yes |
| `VOLUME_BUDGET` | `SliceStrategy.VOLUME_BUDGET` | `WindowSpec.VolumeBudget` | ❌ No (flat) |
| `SINGLE` | `SliceStrategy.SINGLE` | `WindowSpec.Single` | ❌ No (minimal) |
| `HYBRID` | `SliceStrategy.HYBRID` | *(Not yet implemented)* | N/A |

---

## Database Representation

### Table: `ing_plan`

The `window_spec` column stores the JSON representation using Format B:

```sql
CREATE TABLE ing_plan (
    -- ... other columns ...

    `window_spec` JSON NOT NULL COMMENT 'Window boundary specification (Format B: nested JSON)',

    -- Virtual columns for time-based queries
    `window_from_time` TIMESTAMP(6) GENERATED ALWAYS AS (
        CASE
            WHEN JSON_UNQUOTE(JSON_EXTRACT(`window_spec`, '$.strategy')) = 'TIME'
            THEN STR_TO_DATE(JSON_UNQUOTE(JSON_EXTRACT(`window_spec`, '$.window.from')), '%Y-%m-%dT%H:%i:%s.%f')
            ELSE NULL
        END
    ) VIRTUAL,

    `window_to_time` TIMESTAMP(6) GENERATED ALWAYS AS (
        CASE
            WHEN JSON_UNQUOTE(JSON_EXTRACT(`window_spec`, '$.strategy')) = 'TIME'
            THEN STR_TO_DATE(JSON_UNQUOTE(JSON_EXTRACT(`window_spec`, '$.window.to')), '%Y-%m-%dT%H:%i:%s.%f')
            ELSE NULL
        END
    ) VIRTUAL,

    -- ... other columns ...
);
```

**Key Points**:
- JSON column stores complete Format B structure
- Virtual columns extract timestamps from nested `$.window.from` and `$.window.to` paths
- Virtual columns only populated when `strategy = 'TIME'`
- Enables efficient time-range queries with indexing

**JSON Path References**:
- Strategy: `$.strategy`
- TIME window start: `$.window.from`
- TIME window end: `$.window.to`
- ID_RANGE start: `$.window.from`
- ID_RANGE end: `$.window.to`
- CURSOR start: `$.window.from`
- CURSOR end: `$.window.to`
- VOLUME_BUDGET limit: `$.limit`
- VOLUME_BUDGET unit: `$.unit`

### Table: `ing_plan_slice`

Similar structure applies to `ing_plan_slice.window_spec` column for slice-specific boundaries.

---

## Serialization & Deserialization

### Domain → JSON (toMap)

Each `WindowSpec` implementation provides a `toMap()` method:

```java
public interface WindowSpec {
    /**
     * Convert to JSON-serializable map for persistence layer.
     * Returns Format B structure with "strategy" key and strategy-specific fields.
     */
    Map<String, Object> toMap();
}
```

**Example Implementation (TIME)**:
```java
@Override
public Map<String, Object> toMap() {
    Map<String, Object> boundaryMap = Map.of(
        "from", "CLOSED",
        "to", "OPEN"
    );
    Map<String, Object> windowMap = Map.of(
        "from", from.toString(),      // ISO-8601 string
        "to", to.toString(),           // ISO-8601 string
        "boundary", boundaryMap,
        "timezone", "UTC"
    );
    return Map.of(
        "strategy", SliceStrategy.TIME.getCode(),
        "window", windowMap
    );
}
```

### JSON → Domain (fromMap)

Static factory method reconstructs `WindowSpec` from JSON map:

```java
public static WindowSpec fromMap(Map<String, Object> map) {
    // 1. Extract and validate "strategy" key
    String strategyCode = (String) map.get("strategy");
    SliceStrategy strategy = SliceStrategy.fromCode(strategyCode)
        .orElseThrow(() -> new IllegalArgumentException("Unknown strategy: " + strategyCode));

    // 2. Switch on strategy and extract strategy-specific fields
    return switch (strategy) {
        case TIME -> {
            Map<String, Object> windowMap = extractRequiredWindowMap(map, "TIME");
            yield new Time(
                Instant.parse((String) windowMap.get("from")),
                Instant.parse((String) windowMap.get("to"))
            );
        }
        // ... other strategies ...
    };
}
```

**Error Handling**:
- `IllegalArgumentException` if map is null/empty
- `IllegalArgumentException` if "strategy" key is missing
- `IllegalArgumentException` if strategy code is unknown
- `IllegalArgumentException` if required fields are missing
- `IllegalArgumentException` if field types are incorrect

---

## Infrastructure Layer Integration

### PlanConverter (MapStruct)

The `PlanConverter` uses `WindowSpec.toMap()` and `WindowSpec.fromMap()` for persistence:

```java
@Mapper(componentModel = "spring", uses = {JsonNodeMappings.class})
public interface PlanConverter {

    // Domain → DO
    @Mapping(target = "windowSpec", expression = "java(JsonNodeMappings.objectToJsonNode(plan.windowSpec().toMap()))")
    PlanDO toDO(PlanAggregate plan);

    // DO → Domain
    @Mapping(target = "windowSpec", expression = "java(WindowSpec.fromMap(JsonNodeMappings.jsonNodeToMap(planDO.getWindowSpec())))")
    PlanAggregate toDomain(PlanDO planDO);
}
```

**Conversion Flow**:
1. Domain → Map: `WindowSpec.toMap()`
2. Map → JsonNode: `JsonNodeMappings.objectToJsonNode()`
3. JsonNode → Database: MyBatis-Plus JSON type handler
4. Database → JsonNode: MyBatis-Plus JSON type handler
5. JsonNode → Map: `JsonNodeMappings.jsonNodeToMap()`
6. Map → Domain: `WindowSpec.fromMap()`

### PlanSliceConverter

Similar conversion logic applies to `PlanSliceConverter` for slice-specific window specs.

---

## Usage Examples

### Example 1: Time-Based Harvest Window

```java
// Create a time-based window for January 2024
WindowSpec januaryWindow = WindowSpec.ofTime(
    Instant.parse("2024-01-01T00:00:00Z"),
    Instant.parse("2024-02-01T00:00:00Z")
);

// Serialize to JSON for persistence
Map<String, Object> json = januaryWindow.toMap();
// Result:
// {
//   "strategy": "TIME",
//   "window": {
//     "from": "2024-01-01T00:00:00Z",
//     "to": "2024-02-01T00:00:00Z",
//     "boundary": {"from": "CLOSED", "to": "OPEN"},
//     "timezone": "UTC"
//   }
// }

// Store in database (via converter)
PlanAggregate plan = PlanAggregate.builder()
    .windowSpec(januaryWindow)
    // ... other fields ...
    .build();

// Later: retrieve and deserialize
WindowSpec restored = WindowSpec.fromMap(json);
assert restored instanceof WindowSpec.Time;
WindowSpec.Time timeSpec = (WindowSpec.Time) restored;
assert timeSpec.from().equals(Instant.parse("2024-01-01T00:00:00Z"));
```

### Example 2: ID Range for Backfill

```java
// Create an ID range for backfilling PMIDs 30000000-31000000
WindowSpec backfillWindow = WindowSpec.ofIdRange(30000000L, 31000000L);

// Serialize to JSON
Map<String, Object> json = backfillWindow.toMap();
// Result:
// {
//   "strategy": "ID_RANGE",
//   "window": {
//     "from": 30000000,
//     "to": 31000000
//   }
// }

// Check strategy
assert backfillWindow.strategy() == SliceStrategy.ID_RANGE;
```

### Example 3: Volume Budget for Trial Run

```java
// Create a volume-limited window for testing
WindowSpec trialWindow = WindowSpec.ofVolume(1000, "RECORDS");

// Serialize to JSON
Map<String, Object> json = trialWindow.toMap();
// Result:
// {
//   "strategy": "VOLUME_BUDGET",
//   "limit": 1000,
//   "unit": "RECORDS"
// }

// Pattern matching in usage
if (trialWindow instanceof WindowSpec.VolumeBudget budget) {
    System.out.println("Fetching up to " + budget.limit() + " " + budget.unit());
}
```

### Example 4: Single Window for Full Sync

```java
// Create a single-window spec for complete sync
WindowSpec fullSyncWindow = WindowSpec.ofSingle();

// Serialize to JSON
Map<String, Object> json = fullSyncWindow.toMap();
// Result:
// {
//   "strategy": "SINGLE"
// }

// Minimal structure, no boundaries needed
```

---

## Migration from Format A to Format B

**Historical Context**: Earlier versions used Format A (flat structure with `"type"` key). Format B introduces nested `"window"` objects and unified `"strategy"` key.

### Format A (Deprecated)

```json
{
  "type": "TIME",
  "from": "2024-01-01T00:00:00Z",
  "to": "2024-12-31T23:59:59Z"
}
```

### Format B (Current)

```json
{
  "strategy": "TIME",
  "window": {
    "from": "2024-01-01T00:00:00Z",
    "to": "2024-12-31T23:59:59Z",
    "boundary": {"from": "CLOSED", "to": "OPEN"},
    "timezone": "UTC"
  }
}
```

**Migration Strategy**:
1. ✅ All new code uses Format B exclusively
2. ✅ Database virtual columns updated to use `$.strategy` and `$.window.*` paths
3. ✅ No backward compatibility layer (clean cut for 0.1.0 release)
4. ⚠️ If migrating existing data, write Flyway migration to transform JSON structure

---

## Design Rationale

### Why Sealed Interface?

- **Compile-time exhaustiveness**: Switch expressions require all cases to be handled
- **Type safety**: Cannot create invalid subtypes outside the sealed hierarchy
- **Pattern matching**: Enables clean instanceof checks with pattern variables

### Why Nested "window" Object?

- **Consistency**: Separates strategy metadata from window boundaries
- **Extensibility**: Future strategies can add metadata without conflicting with window fields
- **Clarity**: Explicit nesting makes JSON structure self-documenting

### Why "strategy" instead of "type"?

- **Domain alignment**: Matches `SliceStrategy` enum naming
- **Disambiguation**: Avoids confusion with JSON schema "type" or Java types
- **Consistency**: Aligns with domain terminology throughout codebase

---

## Related Documentation

- **Database Schema**: [docs/database/window_spec_schema.md](../database/window_spec_schema.md)
- **ER Diagrams**: [docs/database/er-diagrams.md](../database/er-diagrams.md)
- **Ingest Deep Dive**: [docs/modules/ingest/deep-dive.md](../modules/ingest/deep-dive.md)
- **Plan Aggregate**: `patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/aggregate/PlanAggregate.java`
- **Migration Script**: `patra-ingest-infra/src/main/resources/db/migration/V0.1.0__init_ingest_schema.sql`

---

## Changelog

| Version | Date | Changes | Author |
|---------|------|---------|--------|
| 0.1.0 | 2025-10-10 | Initial Format B documentation: unified JSON structure, 5 strategies, database virtual columns | docs-engineer |

---

**License**

Copyright © 2025 Papertrace
