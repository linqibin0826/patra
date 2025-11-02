# Common Patterns

## Overview

Common architectural and design patterns used in Papertrace, based on Hexagonal Architecture + DDD principles.

---

## 1. Assembler Pattern (Complex Object Graph Creation)

### Purpose
Orchestrate creation of complex object graphs involving multiple aggregates and dependencies.

### Implementation: PlanAssembler

**Location**: `patra-ingest-app/.../plan/PlanAssemblerImpl.java`

**Responsibilities**:
1. Fetch config and expression from Registry
2. Create PlanAggregate
3. Invoke SlicePlanner to generate PlanSliceAggregates
4. Create TaskAggregates (1:1 with slices)
5. Persist all aggregates transactionally
6. Emit domain events (TaskQueuedEvent)

**Key Methods**:
```java
PlanAssemblyResult assemble(PlanAssemblyContext context)
  ↓
1. fetchConfigSnapshot(provenanceCode, operationCode)
2. fetchExprSnapshot(provenanceCode, operationCode)
3. PlanAggregate.create(...)
4. slicePlanner.slice(context) → SlicePlanningResult
5. createTasks(slices) → List<TaskAggregate>
6. persist(plan, slices, tasks) (transactional)
7. publishEvents(tasks)
```

**Benefits**:
- Single responsibility for complex orchestration
- Transactional consistency across multiple aggregates
- Centralized dependency management

---

## 2. Registry Pattern (Strategy Selection)

### Purpose
Select appropriate strategy implementation based on input parameters.

### Implementation: SlicePlannerRegistry

**Location**: `patra-ingest-app/.../plan/SlicePlannerRegistry.java`

**Structure**:
```java
@Component
public class SlicePlannerRegistry {
    private final Map<SliceStrategy, SlicePlanner> planners;

    public SlicePlannerRegistry(
        TimeSlicePlanner timePlanner,
        DateSlicePlanner datePlanner,
        SingleSlicePlanner singlePlanner
    ) {
        planners = Map.of(
            SliceStrategy.TIME, timePlanner,
            SliceStrategy.DATE, datePlanner,
            SliceStrategy.SINGLE, singlePlanner
        );
    }

    public SlicePlanner get(SliceStrategy strategy) {
        return planners.get(strategy);
    }
}
```

**Usage**:
```java
SliceStrategy strategy = determineStrategy(operation, provenance);
SlicePlanner planner = slicePlannerRegistry.get(strategy);
SlicePlanningResult result = planner.slice(context);
```

**Benefits**:
- Centralized strategy selection
- Easy to add new strategies
- Type-safe strategy resolution

---

## 3. Snapshot Converter Pattern

### Purpose
Convert external API responses to immutable domain snapshots.

### Implementation: ProvenanceConfigSnapshotConverter

**Location**: `patra-ingest-infra/.../converter/ProvenanceConfigSnapshotConverter.java`

**Flow**:
```
ProvenanceConfigResp (API DTO)
  ↓ convert()
ProvenanceConfigSnapshot (Domain VO)
```

**Conversion Logic**:
```java
@Component
public class ProvenanceConfigSnapshotConverter {
    public ProvenanceConfigSnapshot convert(ProvenanceConfigResp resp) {
        return new ProvenanceConfigSnapshot(
            convertWindowOffset(resp.windowOffset()),
            convertPagination(resp.pagination()),
            convertHttp(resp.http()),
            convertBatching(resp.batching()),
            convertRetry(resp.retry()),
            convertRateLimit(resp.rateLimit()),
            convertCircuitBreaker(resp.circuitBreaker())
        );
    }
}
```

**Benefits**:
- Isolation from external API changes
- Domain-specific value objects
- Centralized conversion logic

---

## 4. Canonicalizer Pattern (Deterministic Serialization)

### Purpose
Generate deterministic, canonical representations for hashing and comparison.

### Implementation: ExprCanonicalizer

**Location**: `patra-expr-kernel/.../ExprCanonicalizer.java`

**Purpose**:
- Convert expression objects to canonical JSON
- Ensure consistent field ordering
- Remove whitespace/formatting variations
- Generate stable hashes

**Usage**:
```java
ExprSnapshot expr = loadFromRegistry();
String canonicalJson = exprCanonicalizer.canonicalize(expr);
String hash = sha256(canonicalJson);
```

**Benefits**:
- Stable hashing for idempotency
- Change detection accuracy
- Cache key generation

---

## 5. Outbox Pattern (Transactional Messaging)

### Purpose
Ensure reliable event publishing via transactional outbox.

### Implementation

**Outbox Table**: `ing_outbox_message`

**Flow**:
```
1. Business transaction:
   - TaskAggregate.create(...)
   - task.addDomainEvent(new TaskQueuedEvent(...))
   - taskRepository.save(task)
   - outboxRepository.save(extractOutboxMessage(task))
   [COMMIT]

2. Outbox Relay (separate process):
   - SELECT * FROM ing_outbox_message WHERE status='PENDING'
   - Publish to MQ (RabbitMQ/Kafka)
   - UPDATE status='PUBLISHED'
```

**Domain Event Emission**:
```java
public class TaskAggregate extends AggregateRoot<Long> {
    public static TaskAggregate create(...) {
        TaskAggregate task = new TaskAggregate(...);
        task.addDomainEvent(new TaskQueuedEvent(task.getId()));
        return task;
    }
}
```

**Outbox Extraction**:
```java
public class OutboxMessageExtractor {
    public List<OutboxMessage> extract(AggregateRoot<?> aggregate) {
        return aggregate.getDomainEvents().stream()
            .map(this::convertToOutboxMessage)
            .collect(toList());
    }
}
```

**Benefits**:
- Guaranteed event delivery
- Transactional consistency
- At-least-once delivery semantics

---

## 6. Port-Adapter Pattern (Hexagonal Architecture)

### Purpose
Decouple domain logic from external dependencies.

### Implementation: PatraRegistryPort

**Domain Port** (`patra-ingest-domain`):
```java
public interface PatraRegistryPort {
    ProvenanceConfigSnapshot fetchConfig(
        String provenanceCode,
        String operationCode
    );

    ExprSnapshot fetchExprSnapshot(
        String provenanceCode,
        String operationCode,
        Instant at
    );
}
```

**Infrastructure Adapter** (`patra-ingest-infra`):
```java
@Component
public class PatraRegistryAdapter implements PatraRegistryPort {
    private final ProvenanceClient provenanceClient;
    private final ExprClient exprClient;
    private final ProvenanceConfigSnapshotConverter configConverter;
    private final ExprSnapshotConverter exprConverter;

    @Override
    public ProvenanceConfigSnapshot fetchConfig(...) {
        ProvenanceConfigResp resp = provenanceClient.getConfiguration(...);
        return configConverter.convert(resp);
    }

    @Override
    public ExprSnapshot fetchExprSnapshot(...) {
        ExprSnapshotResp resp = exprClient.getSnapshot(...);
        return exprConverter.convert(resp);
    }
}
```

**Benefits**:
- Domain independence
- Testability (mock ports)
- Technology flexibility

---

## 7. Aggregate Root Pattern (DDD)

### Purpose
Enforce consistency boundaries and transactional integrity.

### Key Principles

**Identity**:
```java
public abstract class AggregateRoot<ID> {
    protected ID id;
    protected List<DomainEvent> domainEvents = new ArrayList<>();

    public ID getId() { return id; }

    protected void addDomainEvent(DomainEvent event) {
        domainEvents.add(event);
    }
}
```

**Aggregate Examples**:
- **PlanAggregate**: Owns PlanSliceAggregates (composition)
- **TaskAggregate**: Owns TaskRun entities (composition)
- **PlanSliceAggregate**: Independent aggregate (referenced by Task)

**Consistency Rules**:
1. Only aggregate roots have repositories
2. External references use IDs (not object references)
3. Transactions don't span multiple aggregates

---

## 8. Value Object Pattern (DDD)

### Purpose
Represent domain concepts without identity.

### Examples

**WindowSpec** (sealed interface with variants):
```java
public sealed interface WindowSpec permits
    Time, IdRange, CursorLandmark, VolumeBudget, Single
{
    record Time(Instant from, Instant to) implements WindowSpec {}
    record IdRange(Long from, Long to) implements WindowSpec {}
    // ...
}
```

**LeaseInfo**:
```java
public record LeaseInfo(
    String leaseOwner,
    Instant leasedUntil
) {
    public boolean isExpired() {
        return leasedUntil != null && Instant.now().isAfter(leasedUntil);
    }
}
```

**Characteristics**:
- Immutable (use `record`)
- No identity
- Value-based equality
- Self-validating (compact constructor)

---

## 9. Repository Pattern (Persistence Abstraction)

### Purpose
Abstract persistence logic from domain.

### Structure

**Domain Interface** (`patra-ingest-domain`):
```java
public interface PlanRepository {
    PlanAggregate save(PlanAggregate plan);
    Optional<PlanAggregate> findById(Long id);
    Optional<PlanAggregate> findByPlanKey(String planKey);
}
```

**Infrastructure Implementation** (`patra-ingest-infra`):
```java
@Repository
public class PlanRepositoryImpl implements PlanRepository {
    private final PlanMapper planMapper;  // MyBatis-Plus

    @Override
    public PlanAggregate save(PlanAggregate plan) {
        PlanPO po = toDataModel(plan);
        planMapper.insert(po);
        return toDomain(po);
    }
}
```

**Benefits**:
- Persistence technology independence
- Domain-centric interface
- Testability with in-memory implementations

---

## 10. MapStruct Conversion Pattern

### Purpose
Type-safe object mapping between layers.

### Usage

**Domain ↔ Persistence**:
```java
@Mapper(componentModel = "spring")
public interface PlanEntityMapper {
    PlanAggregate toDomain(PlanPO po);
    PlanPO toDataModel(PlanAggregate domain);
}
```

**API ↔ Domain**:
```java
@Mapper(componentModel = "spring")
public interface ProvenanceConfigMapper {
    ProvenanceConfigSnapshot toSnapshot(ProvenanceConfigResp resp);
}
```

**Benefits**:
- Compile-time type safety
- No reflection overhead
- Explicit mapping logic

---

## Best Practices

### 1. Use Assemblers for Complex Orchestration
**Good**: PlanAssembler orchestrates Plan + Slices + Tasks creation
**Bad**: Controller creates aggregates directly

### 2. Use Registries for Strategy Selection
**Good**: SlicePlannerRegistry selects strategy
**Bad**: if-else chains in service layer

### 3. Use Outbox for Event Publishing
**Good**: Transactional outbox guarantees delivery
**Bad**: Direct MQ publish (may lose events)

### 4. Use Ports for External Dependencies
**Good**: PatraRegistryPort abstracts registry client
**Bad**: Direct Feign client injection in domain

### 5. Use Canonical Forms for Hashing
**Good**: ExprCanonicalizer ensures deterministic JSON
**Bad**: Direct `JSON.stringify()` (unstable field order)

---

## Anti-Patterns to Avoid

### ❌ Anemic Domain Models
**Bad**:
```java
public class Task {
    public Long id;
    public String status;  // Public fields, no behavior
}
```

**Good**:
```java
public class TaskAggregate {
    private String statusCode;

    public void markAsSucceeded() {
        validateStateTransition(StatusCode.SUCCEEDED);
        this.statusCode = StatusCode.SUCCEEDED.name();
        addDomainEvent(new TaskSucceededEvent(id));
    }
}
```

### ❌ Transaction Spanning Multiple Aggregates
**Bad**:
```java
@Transactional
public void createPlanAndExecuteTasks(...) {
    Plan plan = planRepository.save(...);
    List<Task> tasks = createTasks(plan);
    taskRepository.saveAll(tasks);
    taskExecutor.executeAll(tasks);  // Long transaction!
}
```

**Good**:
```java
@Transactional
public void createPlan(...) {
    // Only persist aggregates
    planRepository.save(plan);
    sliceRepository.saveAll(slices);
    taskRepository.saveAll(tasks);
    outboxRepository.saveAll(events);
}
// Execution happens asynchronously via MQ
```

### ❌ Direct External API Calls from Domain
**Bad**:
```java
public class TaskAggregate {
    @Autowired
    private PubMedClient pubmedClient;  // Violation!
}
```

**Good**:
```java
// Domain defines port
public interface ProviderApiPort {
    ApiResponse fetch(ApiRequest request);
}

// Infra implements adapter
@Component
public class PubMedApiAdapter implements ProviderApiPort { ... }
```

---

## Summary

**Key Patterns**:
1. **Assembler**: Complex object graph creation (PlanAssembler)
2. **Registry**: Strategy selection (SlicePlannerRegistry)
3. **Snapshot Converter**: API ↔ Domain conversion
4. **Canonicalizer**: Deterministic serialization
5. **Outbox**: Transactional messaging
6. **Port-Adapter**: Hexagonal architecture
7. **Aggregate Root**: Consistency boundaries
8. **Value Object**: Identity-less domain concepts
9. **Repository**: Persistence abstraction
10. **MapStruct**: Layer-to-layer mapping

**Architecture Principles**:
- Domain independence (Hexagonal Architecture)
- Aggregate boundaries (DDD)
- Event-driven communication (Outbox pattern)
- Immutability (Records, Value Objects)
- Type safety (Sealed interfaces, MapStruct)
