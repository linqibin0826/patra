---
skill: papertrace-domain
description: Papertrace business domain knowledge - Provenance, Plan, Task, Expression engine, and workflow patterns
type: domain
enforcement: suggest
priority: high
---

# Papertrace Domain Knowledge

## Purpose

This skill provides comprehensive knowledge about Papertrace's business domain concepts, workflow patterns, and architectural decisions. Use this skill when working with Papertrace-specific business logic, data models, and integration patterns.

**Core Principle**: Papertrace is a medical literature data platform that harvests, processes, and indexes scientific publications from multiple sources (PubMed, EPMC, Crossref) using configurable provenance-based workflows.

---

## When to Use This Skill

This skill automatically activates when:

**Working with Core Concepts:**
- Provenance configuration and management
- Plan creation and slicing strategies
- Task execution workflows
- Expression engine customization

**Implementing Features:**
- Data source integration (PubMed, EPMC, Crossref)
- Batch processing pipelines
- Temporal slicing and window management
- Idempotency and retry mechanisms

**Debugging Issues:**
- Plan slicing problems
- Task execution failures
- Expression rendering errors
- Configuration scope precedence

**File Context:**
- Editing files in `patra-registry` (configuration service)
- Working in `patra-ingest` (data ingestion service)
- Modifying `patra-expr-kernel` (expression engine)

---

## Quick Start

### Understanding Papertrace Architecture

**Three-Service Architecture:**

```
┌─────────────────────────────────────────────────────────┐
│  patra-gateway (API Gateway)                            │
│  - Routing, Authentication, Rate Limiting               │
└─────────────────────────────────────────────────────────┘
              │
              ├──────────────┬──────────────┐
              ▼              ▼              ▼
┌──────────────────┐  ┌─────────────────┐  ┌───────────────┐
│ patra-registry   │  │ patra-ingest    │  │ patra-storage │
│ (SSOT Service)   │  │ (Orchestrator)  │  │ (Data Store)  │
├──────────────────┤  ├─────────────────┤  ├───────────────┤
│ • Provenance     │  │ • Plan Mgmt     │  │ • Documents   │
│   Configuration  │  │ • Task Exec     │  │ • Indexing    │
│ • Expressions    │  │ • Slice Logic   │  │ • Search      │
│ • Dictionaries   │  │ • Harvest Coord │  │               │
└──────────────────┘  └─────────────────┘  └───────────────┘
```

**Key Responsibilities:**
- **patra-registry**: Single Source of Truth for Provenance configurations, Expressions, Dictionaries
- **patra-ingest**: Plan/Task lifecycle management, data harvesting orchestration
- **patra-storage**: Document storage, indexing, and search

---

## Core Domain Concepts

### 1. **Provenance**

**Definition**: A data source from which literature data is harvested (e.g., PubMed, EPMC, Crossref).

**Key Attributes:**
```java
public record Provenance(
    ProvenanceCode provenanceCode,  // PUBMED, EPMC, CROSSREF
    String name,
    String baseUrl,
    boolean isActive
) { }
```

**Provenance Configuration**: Aggregates all settings for a specific Provenance:
- `WindowOffsetConfig`: Time window slicing parameters
- `PaginationConfig`: API pagination settings
- `HttpConfig`: HTTP client configuration
- `BatchingConfig`: Batch size and concurrency
- `RetryConfig`: Retry strategy and limits
- `RateLimitConfig`: Rate limiting rules

**See**: [business-concepts.md](resources/business-concepts.md) for complete details.

---

### 2. **Plan** (Batch Plan)

**Definition**: A batch processing plan that defines how to harvest data from a Provenance within a specific time window.

**Lifecycle:**
```
PENDING → RUNNING → COMPLETED
   ↓
CANCELLED (user action)
```

**Key Concepts:**
- **Time Window**: `[startTime, endTime)` for data collection
- **Slicing**: Breaking a large window into smaller Slices for parallel processing
- **Slice**: Atomic unit of work within a Plan

**Example Scenario:**
```
Plan: Harvest PubMed data from 2024-01-01 to 2024-12-31

Slicing Strategy (Monthly):
  Slice 1: 2024-01-01 to 2024-01-31
  Slice 2: 2024-02-01 to 2024-02-29
  ...
  Slice 12: 2024-12-01 to 2024-12-31

Each Slice → Multiple Tasks (based on expected record count)
```

**See**: [plan-task-workflow.md](resources/plan-task-workflow.md) for workflow details.

---

### 3. **Task** (Batch Task)

**Definition**: An atomic unit of work that fetches data from a specific Provenance API endpoint.

**Key Attributes:**
```java
public record BatchTask(
    TaskId taskId,
    PlanId planId,
    ProvenanceCode provenanceCode,
    String businessKey,      // Idempotency key
    TaskStatus status,
    Map<String, Object> params,  // API request parameters
    int retryCount
) { }
```

**Idempotency Design:**
- `businessKey` = hash(Provenance + API params)
- Prevents duplicate API calls
- Enables safe retries

**Status Transitions:**
```
PENDING → RUNNING → SUCCEEDED
   ↓          ↓
   └─────→ FAILED → PENDING (retry)
              ↓
          EXHAUSTED (max retries)
```

**See**: [plan-task-workflow.md](resources/plan-task-workflow.md) for complete workflow.

---

### 4. **Expression Engine**

**Definition**: A dynamic parameter mapping system that translates Papertrace's abstract query model to provider-specific API parameters.

**Core Components:**

```java
public record Expression(
    String exprField,       // Abstract field name (e.g., "publicationDate")
    String capability,      // Search capability (e.g., "range", "exact")
    String renderRule       // Provider-specific template (e.g., "mindate={start}&maxdate={end}")
) { }
```

**Example Usage:**

```
User Query (Abstract):
  publicationDate: [2024-01-01, 2024-12-31]
  capability: range

PubMed Expression:
  exprField: publicationDate
  capability: range
  renderRule: "mindate={start}&maxdate={end}"

Rendered Output:
  mindate=2024-01-01&maxdate=2024-12-31
```

**See**: [expression-engine.md](resources/expression-engine.md) for complete guide.

---

## Design Patterns

### 1. **Temporal Slicing**

**Problem**: Large time windows result in API timeouts or rate limiting.

**Solution**: Break time windows into smaller, manageable slices.

**Implementation**:
- Window size configured per Provenance
- Slice boundaries aligned to natural units (days/weeks/months)
- Each Slice processed independently

### 2. **Scope Precedence**

**Problem**: Configuration can be defined at multiple levels (Global, Provenance, Task).

**Solution**: Scope precedence hierarchy.

```
TASK > SOURCE > GLOBAL
(Most specific wins)
```

**Example**:
```
Global: retryLimit = 3
Provenance PUBMED: retryLimit = 5
Task ABC: retryLimit = 1

→ Task ABC uses retryLimit = 1
```

### 3. **Idempotency by Business Key**

**Problem**: Network failures cause duplicate API calls.

**Solution**: Hash-based business key.

```
businessKey = hash(
  provenanceCode +
  apiEndpoint +
  requestParams
)
```

**Effect**: Duplicate Tasks (same business key) are skipped.

---

## Common Workflows

### Workflow 1: Creating a New Provenance

1. **Define Provenance** (registry domain):
   - `ProvenanceCode`, `name`, `baseUrl`

2. **Configure Settings** (registry domain):
   - Window offset: How far back to harvest?
   - Pagination: Page size, offset/cursor strategy
   - HTTP: Timeouts, headers, authentication
   - Retry: Max retries, backoff strategy
   - Rate limit: Requests per second/minute

3. **Define Expressions** (expr-kernel):
   - Map abstract fields to API parameters
   - Support multiple capabilities (exact, range, wildcard)

4. **Test Configuration**:
   - Create a small test Plan
   - Verify slicing logic
   - Validate API parameter rendering

### Workflow 2: Executing a Batch Plan

1. **Plan Creation** (ingest app):
   ```
   Input: Provenance, Time Window, User
   Output: Plan with Slices
   ```

2. **Slice Generation**:
   ```
   for each slice in [plan.startTime, plan.endTime):
     estimate record count
     if count > threshold:
       split into multiple Tasks
     else:
       create single Task
   ```

3. **Task Execution**:
   ```
   for each Task:
     render API params using Expressions
     check businessKey (skip if exists)
     call Provenance API
     store results in patra-storage
     update Task status
   ```

4. **Retry Mechanism**:
   ```
   if Task fails:
     if retryCount < maxRetries:
       status = PENDING (re-queue)
     else:
       status = EXHAUSTED
   ```

**See**: [plan-task-workflow.md](resources/plan-task-workflow.md) for complete workflow.

---

## Resource Files

Detailed guides for specific topics (each <500 lines):

### Business Domain
- **[business-concepts.md](resources/business-concepts.md)** - Core concepts: Provenance, Plan, Task, Expression
- **[provenance-config-system.md](resources/provenance-config-system.md)** - Configuration hierarchy and scope precedence
- **[expression-engine.md](resources/expression-engine.md)** - Expression rendering and capability system

### Workflow Patterns
- **[plan-task-workflow.md](resources/plan-task-workflow.md)** - Plan/Task lifecycle and execution patterns
- **[slicing-strategies.md](resources/slicing-strategies.md)** - Temporal slicing and window management
- **[idempotency-patterns.md](resources/idempotency-patterns.md)** - Business key design and deduplication

### Integration
- **[service-integration.md](resources/service-integration.md)** - How patra-registry, patra-ingest, patra-storage work together
- **[pubmed-integration.md](resources/pubmed-integration.md)** - PubMed-specific configuration and quirks
- **[common-patterns.md](resources/common-patterns.md)** - Common code patterns from actual codebase

### Troubleshooting
- **[troubleshooting.md](resources/troubleshooting.md)** - Common issues and debugging strategies

---

## Quick Decision Tree

**Need to add a new data source?**

1. **Registry Service**: Define Provenance and Configuration
2. **Expression Engine**: Map abstract fields to API params
3. **Ingest Service**: No code changes (uses generic workflow)
4. **Storage Service**: No code changes (provider-agnostic)

**Plan execution fails?**

1. Check Task status distribution (how many FAILED?)
2. Look at Task error messages
3. Verify Expression rendering (are API params correct?)
4. Check rate limiting (are we hitting provider limits?)

**Expression not working?**

1. Verify `exprField` matches user query
2. Check `capability` is supported (exact/range/wildcard)
3. Validate `renderRule` template syntax
4. Test with actual values in Expression engine

**Configuration not applied?**

1. Check scope precedence (TASK > SOURCE > GLOBAL)
2. Verify `operationType` matches (e.g., "harvest" vs "update")
3. Ensure temporal validity (effectiveFrom/effectiveTo)

---

## Examples from Papertrace Codebase

### Example 1: ProvenanceConfiguration Aggregate

```java
// patra-registry-domain/src/main/java/com/patra/registry/domain/model/aggregate/
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
               pagination != null;
    }

    public boolean supportsOperation(String operationType) {
        // Scope precedence logic
        return // ... implementation
    }
}
```

### Example 2: Plan Slicing Logic

```java
// patra-ingest-app/src/main/java/com/patra/ingest/app/service/
public class PlanSlicingOrchestrator {

    public List<Slice> createSlices(
        Plan plan,
        WindowOffsetConfig windowConfig
    ) {
        Duration sliceDuration = windowConfig.sliceDuration();
        LocalDateTime current = plan.startTime();
        List<Slice> slices = new ArrayList<>();

        while (current.isBefore(plan.endTime())) {
            LocalDateTime sliceEnd = current.plus(sliceDuration);
            if (sliceEnd.isAfter(plan.endTime())) {
                sliceEnd = plan.endTime();
            }

            slices.add(new Slice(
                current,
                sliceEnd,
                estimateRecordCount(current, sliceEnd)
            ));

            current = sliceEnd;
        }

        return slices;
    }
}
```

**See**: [common-patterns.md](resources/common-patterns.md) for more examples.

---

## Integration with Other Skills

This skill complements `java-backend-guidelines`:

- **java-backend-guidelines**: How to structure code (Hexagonal + DDD)
- **papertrace-domain**: What the business concepts mean

**Use both together** when implementing new features in Papertrace.

---

## Next Steps

1. Browse resource files for deep dives on specific topics
2. Check [common-patterns.md](resources/common-patterns.md) for code examples from actual codebase
3. Review [troubleshooting.md](resources/troubleshooting.md) when debugging issues

**This skill auto-activates when editing patra-registry, patra-ingest, or patra-expr-kernel files, or when prompts mention Provenance, Plan, Task, or Expression-related keywords.**
