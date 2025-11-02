# Slicing Strategies

## Overview

Slicing is the process of dividing a Plan's time/ID window into smaller **PlanSlice** units for parallel processing. Each slice becomes exactly ONE task.

**Core Problem**: Processing large windows (e.g., 1 year of data) in a single API call:
- Exceeds API result limits (e.g., PubMed max 10,000 results)
- Causes timeouts on large datasets
- Makes retry difficult
- Prevents parallel processing

**Solution**: Divide the window into smaller slices using **SlicePlanner** strategies, each becoming an independent **PlanSliceAggregate** → **TaskAggregate**.

---

## WindowSpec (Window Specification)

### Purpose
Define the boundaries of a plan or slice window using different strategies.

### Sealed Interface (5 Variants)

**Package**: `patra-ingest-domain/src/main/java/.../vo/plan/WindowSpec.java`

```java
public sealed interface WindowSpec permits
    WindowSpec.Time,
    WindowSpec.IdRange,
    WindowSpec.CursorLandmark,
    WindowSpec.VolumeBudget,
    WindowSpec.Single
{
    // Each variant has specific fields
}
```

---

### 1. WindowSpec.Time

**Purpose**: Absolute timestamp-based windows.

**Fields**:
```java
record Time(
    Instant from,
    Instant to
) implements WindowSpec
```

**Use Case**: Time-based slicing (most common).

**Example**:
```java
WindowSpec window = new WindowSpec.Time(
    Instant.parse("2024-01-01T00:00:00Z"),
    Instant.parse("2024-01-31T23:59:59Z")
);
```

---

### 2. WindowSpec.IdRange

**Purpose**: ID-based windows (numeric range).

**Fields**:
```java
record IdRange(
    Long from,
    Long to
) implements WindowSpec
```

**Use Case**: ID-based pagination (e.g., PubMed IDs 1-1000000).

**Example**:
```java
WindowSpec window = new WindowSpec.IdRange(
    1L,
    1000000L
);
```

---

### 3. WindowSpec.CursorLandmark

**Purpose**: Cursor/landmark-based windows.

**Fields**:
```java
record CursorLandmark(
    String from,
    String to
) implements WindowSpec
```

**Use Case**: Cursor-based APIs (opaque pagination tokens).

**Example**:
```java
WindowSpec window = new WindowSpec.CursorLandmark(
    "cursor_start",
    "cursor_end"
);
```

---

### 4. WindowSpec.VolumeBudget

**Purpose**: Volume-limited windows.

**Fields**:
```java
record VolumeBudget(
    Long limit,
    String unit  // "RECORDS", "BYTES", "PAGES"
) implements WindowSpec
```

**Use Case**: Budget-based slicing (max 10,000 records per slice).

**Example**:
```java
WindowSpec window = new WindowSpec.VolumeBudget(
    10000L,
    "RECORDS"
);
```

---

### 5. WindowSpec.Single

**Purpose**: No slicing (entire window in one slice).

**Fields**:
```java
record Single() implements WindowSpec
```

**Use Case**: UPDATE operations (small windows), single-shot harvests.

**Example**:
```java
WindowSpec window = new WindowSpec.Single();
```

---

## SlicePlanner Architecture

### Purpose
Generate **SlicePlan** DTOs (app-layer) that are converted to **PlanSliceAggregates** (domain-layer).

### Interface

**Package**: `patra-ingest-app/.../plan/SlicePlanner.java`

```java
public interface SlicePlanner {
    SlicePlanningResult slice(SlicePlanningContext context);
}
```

**Context**:
```java
record SlicePlanningContext(
    PlanAggregate plan,
    WindowSpec window,
    ProvenanceConfigSnapshot config,
    ExprSnapshot exprProto
)
```

**Result**:
```java
record SlicePlanningResult(
    List<SlicePlan> drafts,           // App-layer DTOs
    List<PlanSliceAggregate> aggregates  // Domain aggregates
)
```

---

### SlicePlannerRegistry (Strategy Pattern)

**Purpose**: Select appropriate SlicePlanner based on strategy code.

**Implementation**:
```java
@Component
public class SlicePlannerRegistry {
    private final Map<SliceStrategy, SlicePlanner> planners;

    public SlicePlanner get(SliceStrategy strategy) {
        return planners.get(strategy);
    }
}
```

**Strategy Selection** (in PlanAssemblerImpl):
```java
SliceStrategy strategy;
if (operation == OperationType.UPDATE) {
    strategy = SliceStrategy.SINGLE;  // No slicing for UPDATE
} else if (provenanceHasOnlyDateFields(provenance)) {
    strategy = SliceStrategy.DATE;    // Date-only slicing
} else {
    strategy = SliceStrategy.DATE;    // Default to DATE
}
```

---

## Slicing Strategies

### 1. TimeSlicePlanner (TIME)

**Purpose**: Slice by time duration (hours, days, weeks).

**Algorithm**:
```java
public SlicePlanningResult slice(SlicePlanningContext context) {
    WindowSpec.Time timeWindow = (WindowSpec.Time) context.window();

    Instant from = timeWindow.from();
    Instant to = timeWindow.to();
    ChronoUnit unit = context.config().windowOffset().sliceUnit(); // HOURS, DAYS

    List<SlicePlan> slices = new ArrayList<>();
    int sliceNo = 1;

    Instant currentFrom = from;
    while (currentFrom.isBefore(to)) {
        Instant currentTo = currentFrom.plus(1, unit).isBefore(to)
            ? currentFrom.plus(1, unit)
            : to;

        slices.add(createSlicePlan(sliceNo++, currentFrom, currentTo));
        currentFrom = currentTo;
    }

    return new SlicePlanningResult(slices, convertToAggregates(slices));
}
```

**Example** (7-day slices):
```
Plan window: 2024-01-01 to 2024-01-31
Slice unit: 7 DAYS

Generated slices:
1. 2024-01-01 to 2024-01-08
2. 2024-01-08 to 2024-01-15
3. 2024-01-15 to 2024-01-22
4. 2024-01-22 to 2024-01-29
5. 2024-01-29 to 2024-01-31
```

---

### 2. DateSlicePlanner (DATE)

**Purpose**: Slice by calendar date (ignores time component).

**Algorithm**:
```java
public SlicePlanningResult slice(SlicePlanningContext context) {
    WindowSpec.Time timeWindow = (WindowSpec.Time) context.window();

    LocalDate fromDate = LocalDate.ofInstant(timeWindow.from(), ZoneId.systemDefault());
    LocalDate toDate = LocalDate.ofInstant(timeWindow.to(), ZoneId.systemDefault());

    List<SlicePlan> slices = new ArrayList<>();
    int sliceNo = 1;

    LocalDate current = fromDate;
    while (current.isBefore(toDate) || current.isEqual(toDate)) {
        Instant from = current.atStartOfDay(ZoneId.systemDefault()).toInstant();
        Instant to = current.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant();

        slices.add(createSlicePlan(sliceNo++, from, to));
        current = current.plusDays(1);
    }

    return new SlicePlanningResult(slices, convertToAggregates(slices));
}
```

**Example** (daily slices):
```
Plan window: 2024-01-01T00:00:00Z to 2024-01-05T23:59:59Z

Generated slices:
1. 2024-01-01T00:00:00Z to 2024-01-01T23:59:59Z
2. 2024-01-02T00:00:00Z to 2024-01-02T23:59:59Z
3. 2024-01-03T00:00:00Z to 2024-01-03T23:59:59Z
4. 2024-01-04T00:00:00Z to 2024-01-04T23:59:59Z
5. 2024-01-05T00:00:00Z to 2024-01-05T23:59:59Z
```

---

### 3. SingleSlicePlanner (SINGLE)

**Purpose**: No slicing, entire window in one slice.

**Algorithm**:
```java
public SlicePlanningResult slice(SlicePlanningContext context) {
    SlicePlan singleSlice = createSlicePlan(1, context.window());
    return new SlicePlanningResult(
        List.of(singleSlice),
        List.of(convertToAggregate(singleSlice))
    );
}
```

**Use Case**: UPDATE operations (small windows), single-shot harvests.

**Example**:
```
Plan window: 2024-11-01T00:00:00Z to 2024-11-02T00:00:00Z
Strategy: SINGLE

Generated slices:
1. 2024-11-01T00:00:00Z to 2024-11-02T00:00:00Z (entire window)
```

---

## SlicePlan → PlanSliceAggregate Conversion

### SlicePlan (App-Layer DTO)

```java
public record SlicePlan(
    int sliceNo,
    WindowSpec windowSpec,
    ExprSnapshot sliceExpr,     // Localized expression
    String sliceSignatureHash
)
```

### Conversion Process

```java
PlanSliceAggregate convertToAggregate(SlicePlan draft, PlanAggregate plan) {
    // Serialize window spec
    JsonNode windowSpecJson = objectMapper.valueToTree(draft.windowSpec());

    // Serialize localized expression
    String exprSnapshotJson = objectMapper.writeValueAsString(draft.sliceExpr());

    // Hash expression
    String exprHash = sha256(exprSnapshotJson);

    // Create aggregate
    return PlanSliceAggregate.create(
        plan.getId(),
        draft.sliceNo(),
        draft.sliceSignatureHash(),
        exprHash,
        exprSnapshotJson,
        windowSpecJson,
        SliceStatus.PENDING
    );
}
```

---

## Slice Signature Hash (Idempotency)

### Purpose
Unique identifier for a slice based on its window boundaries, ensuring idempotency.

### Algorithm

```java
public String calculateSliceSignatureHash(WindowSpec windowSpec) {
    // 1. Canonicalize window spec (deterministic JSON)
    String canonicalJson = canonicalizer.canonicalize(windowSpec);

    // 2. Hash
    return sha256(canonicalJson);
}
```

**Example**:
```java
WindowSpec.Time window = new WindowSpec.Time(
    Instant.parse("2024-01-01T00:00:00Z"),
    Instant.parse("2024-01-07T23:59:59Z")
);

String canonical = "Time{from:1704067200000,to:1704671999000}";
String hash = sha256(canonical);
// → "a7f3e2d1c4b8..."
```

**Use Case**: Detect duplicate slices across plan recreations.

---

## Expression Localization (Slice-Level)

### Purpose
Inject window boundaries into the expression prototype at slice creation.

### Process

```java
public ExprSnapshot localizeExpression(
    ExprSnapshot exprProto,
    WindowSpec sliceWindow
) {
    // Copy prototype
    ExprSnapshot localized = exprProto.copy();

    // Inject window boundaries into render rules
    if (sliceWindow instanceof WindowSpec.Time time) {
        for (ExprRenderRule rule : localized.renderRules()) {
            if (rule.emissionMode() == EmissionMode.PARAMS) {
                // Inject from/to into params JSON
                rule.params().put("from", time.from().toString());
                rule.params().put("to", time.to().toString());
            }
        }
    }

    return localized;
}
```

**Example**:
```
Plan Expression (prototype):
{
  "renderRules": [
    {
      "fieldKey": "publication_date",
      "emissionMode": "PARAMS",
      "params": {"from": "{{from}}", "to": "{{to}}", "datetype": "edat"}
    }
  ]
}

Slice Window: Time(2024-01-01T00:00:00Z, 2024-01-07T23:59:59Z)

Localized Expression (slice):
{
  "renderRules": [
    {
      "fieldKey": "publication_date",
      "emissionMode": "PARAMS",
      "params": {"from": "2024-01-01T00:00:00Z", "to": "2024-01-07T23:59:59Z", "datetype": "edat"}
    }
  ]
}
```

---

## Slice Status Lifecycle

### States

```
PENDING → ASSIGNED → FINISHED
```

| Status | Description | Next |
|--------|-------------|------|
| **PENDING** | Slice created, task not yet created | ASSIGNED |
| **ASSIGNED** | Task created and dispatched | FINISHED |
| **FINISHED** | Task execution completed (terminal) | - |

**Database**:
```sql
CREATE TABLE ing_plan_slice (
  ...
  status_code VARCHAR(32),  -- 'PENDING', 'ASSIGNED', 'FINISHED'
  ...
);
```

---

## Best Practices

### 1. Choose Appropriate Strategy

**Use DATE for**:
- Daily granularity needs
- Date-only fields (publication_date without time)
- Calendar-aligned slicing

**Use TIME for**:
- Sub-day granularity (hourly slicing)
- Precise timestamp windows

**Use SINGLE for**:
- UPDATE operations (small windows)
- Single-shot harvests (entire dataset < 10k records)

### 2. Respect WindowSpec Variants

```java
// Good (type-safe pattern matching)
switch (windowSpec) {
    case WindowSpec.Time time -> processTimeWindow(time);
    case WindowSpec.IdRange range -> processIdRange(range);
    case WindowSpec.Single single -> processSingleWindow(single);
    default -> throw new UnsupportedWindowSpecException();
}

// Bad (instanceof checks)
if (windowSpec instanceof WindowSpec.Time) {
    WindowSpec.Time time = (WindowSpec.Time) windowSpec;
    // ...
}
```

### 3. Slice Count Estimation

**Avoid** over-slicing (too many small slices):
```
Bad: 365 slices for 1-year window (daily) → 365 tasks
Better: 52 slices for 1-year window (weekly) → 52 tasks
```

**Balance**:
- Too many slices → overhead (scheduling, coordination)
- Too few slices → timeouts, large retry scope

---

## Troubleshooting

### Issue: "Too many slices generated"

**Diagnosis**:
1. Check slice strategy: `SELECT slice_strategy_code FROM ing_plan WHERE id=?`
2. Check window size: Large window with small slice unit (e.g., 1 year with DAILY)

**Fix**: Increase slice unit (WEEKLY instead of DAILY) or use DATE strategy.

---

### Issue: "Slice signature hash collision"

**Diagnosis**: Two different slices have the same signature hash (rare).

**Fix**: Verify WindowSpec canonicalization is deterministic.

---

## Summary

**Key Components**:
- ✅ WindowSpec sealed interface (5 variants)
- ✅ SlicePlanner strategy pattern (TIME/DATE/SINGLE)
- ✅ SlicePlan → PlanSliceAggregate conversion
- ✅ Slice signature hash for idempotency
- ✅ Expression localization (inject boundaries)

**Slicing Flow**:
```
1. Plan created with window + strategy
2. SlicePlanner.slice(context) → List<SlicePlan>
3. Convert SlicePlan DTOs → PlanSliceAggregates
4. Calculate slice signature hash (idempotency)
5. Localize expression (inject window boundaries)
6. Store PlanSliceAggregates (status: PENDING)
```

**No**:
- ❌ `record Slice` (actual: `class PlanSliceAggregate`)
- ❌ Slice states PENDING→RUNNING→COMPLETED (actual: PENDING→ASSIGNED→FINISHED)
- ❌ Fabricated estimatedCount/actualCount fields
- ❌ WEEKLY/MONTHLY enum values (actual: TIME/DATE/SINGLE)
