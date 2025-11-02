# Hexagonal Architecture + DDD Overview

Complete guide to Papertrace's architectural patterns combining Hexagonal Architecture (Ports & Adapters) with Domain-Driven Design.

---

## Table of Contents

1. [Core Architectural Principles](#core-architectural-principles)
2. [Four-Layer Architecture](#four-layer-architecture)
3. [Dependency Rules](#dependency-rules-must-follow)
4. [Layer Responsibilities](#layer-responsibilities)
5. [Adapter Layer Organization](#adapter-layer-organization)
6. [Application Layer Patterns](#application-layer-organization-patterns)
7. [Design Patterns Reference](#design-patterns-reference)
8. [Testing Strategy](#testing-strategy)

---

## Core Architectural Principles

### 1. Hexagonal Architecture (Ports & Adapters)

**Goal**: Isolate business logic from external concerns (HTTP, databases, messaging).

**Key Concepts**:
- **Domain (Core)**: Pure business logic, no external dependencies
- **Ports**: Interfaces defined by domain (e.g., `ProvenancePort`)
- **Adapters**: Implementations that connect to external systems
  - **Driving Adapters** (Inbound): Receive external triggers → `adapter` module
  - **Driven Adapters** (Outbound): Access external resources → `infra` module

**Benefits**:
- Testable business logic (pure Java, no mocks needed for domain)
- Swappable implementations (e.g., change DB without touching domain)
- Clear separation of concerns

---

### 2. Domain-Driven Design (DDD)

**Goal**: Align code structure with business domain.

**Key Tactical Patterns**:
- **Aggregate**: Consistency boundary with root entity
- **Entity**: Identity-based objects (e.g., `BatchPlan`)
- **Value Object**: Immutable, equality by value (e.g., `LiteratureId` as `record`)
- **Domain Event**: Captures business facts (e.g., `PlanCompletedEvent`)
- **Repository**: Collection-like interface for persistence
- **Factory**: Complex object creation

**Benefits**:
- Ubiquitous language (code matches business terms)
- Business rules in domain layer
- Evolutionary design

---

## Four-Layer Architecture

```
┌─────────────────────────────────────────────────────────────┐
│ 1. Adapter Layer (Driving - Outermost)                     │
│    Purpose: Receive external triggers                       │
│    Examples: REST Controllers, Jobs, Message Consumers      │
│    Module: patra-{service}-adapter                          │
│    Dependencies: app + api + web starters                   │
│    ↓ calls ↓                                                 │
├─────────────────────────────────────────────────────────────┤
│ 2. Application Layer                                        │
│    Purpose: Orchestrate use cases, manage transactions      │
│    Examples: Orchestrators, Coordinators                    │
│    Module: patra-{service}-app                              │
│    Dependencies: domain + patra-common + core starter       │
│    ↓ calls ↓                                                 │
├─────────────────────────────────────────────────────────────┤
│ 3. Domain Layer (Core - Pure Java)                          │
│    Purpose: Business logic and rules                        │
│    Examples: Aggregates, Entities, VOs, Events, Ports       │
│    Module: patra-{service}-domain                           │
│    Dependencies: patra-common + Lombok + Hutool ONLY        │
│    ↑ implemented by ↑                                        │
├─────────────────────────────────────────────────────────────┤
│ 4. Infrastructure Layer (Driven)                            │
│    Purpose: Access external resources                       │
│    Examples: DB Repositories, External APIs, MQ Publishers  │
│    Module: patra-{service}-infra                            │
│    Dependencies: domain + mybatis starter + core starter    │
└─────────────────────────────────────────────────────────────┘
```

---

## Dependency Rules (MUST FOLLOW)

### The Golden Rule

**Dependencies MUST flow from outer layers to inner layers:**

```
adapter  →  app + api (+ web starters)
app      →  domain + patra-common + core starter
infra    →  domain + mybatis starter + core starter
domain   →  patra-common + Lombok + Hutool (NO Spring!)
api      →  NO framework dependencies (external contracts)
boot     →  ALL modules + Spring Boot starters
```

**⚠️ Violation of these rules is NOT acceptable!**

---

### Forbidden Dependencies

```java
// ❌ NEVER: Domain depending on Infrastructure
package com.patra.ingest.domain.model.entity;
import com.patra.ingest.infra.persistence.entity.BatchPlanDO;  // WRONG!

// ❌ NEVER: Domain depending on Spring
package com.patra.ingest.domain.model.entity;
import org.springframework.stereotype.Service;  // WRONG!

// ❌ NEVER: Application depending on Infrastructure directly
package com.patra.ingest.app.usecase.plan;
import com.patra.ingest.infra.persistence.repository.ProvenanceRepositoryImpl;  // WRONG!
// Instead, depend on domain port:
import com.patra.ingest.domain.port.ProvenancePort;  // CORRECT!
```

---

## Layer Responsibilities

### 1. Adapter Layer (Driving)

**Module**: `patra-{service}-adapter`

**Responsibility**: Receive external triggers and delegate to Application layer.

**What Goes Here**:
- REST Controllers (`@RestController`)
- Scheduled Jobs (XXL-Job, `@XxlJob`)
- Message Consumers (RocketMQ, `@RocketMQMessageListener`)
- Request/Response DTOs, Validation (`@Valid`)

**What Does NOT Go Here**:
- ❌ Business logic (belongs in Domain)
- ❌ Database access (belongs in Infrastructure)
- ❌ Transaction management (belongs in Application)

**Example**:
```java
@RestController
@RequestMapping("/api/v1/provenances")
@RequiredArgsConstructor
public class ProvenanceController {
    private final ProvenanceManagementUseCase useCase;

    @PostMapping
    public ResponseEntity<ProvenanceResponse> create(
        @Valid @RequestBody CreateProvenanceCommand command
    ) {
        ProvenanceResult result = useCase.create(command);
        return ResponseEntity.ok(ProvenanceResponse.from(result));
    }
}
```

---

### 2. Application Layer

**Module**: `patra-{service}-app`

**Responsibility**: Orchestrate use cases, manage transactions, coordinate domain logic.

**What Goes Here**:
- Orchestrators (`*Orchestrator.java`)
- Coordinators (`*Coordinator.java`)
- Use case commands/DTOs, Domain event handlers
- Transaction boundaries (`@Transactional`)

**What Does NOT Go Here**:
- ❌ Business rules (belongs in Domain)
- ❌ Database queries (use ports)
- ❌ External API calls within transactions

**See**: [Application Layer Patterns](#application-layer-organization-patterns) section below for detailed patterns.

---

### 3. Domain Layer (Core)

**Module**: `patra-{service}-domain`

**Responsibility**: Pure business logic and rules.

**Allowed Dependencies**:
- ✅ Lombok (`@Data`, `@Slf4j`, etc.)
- ✅ Hutool utilities
- ✅ patra-common base classes
- ✅ Java standard library

**What Does NOT Go Here**:
- ❌ Spring annotations (`@Service`, `@Repository`, `@Autowired`)
- ❌ Database entities (DOs belong in Infrastructure)
- ❌ HTTP DTOs (belong in Adapter)

**Aggregate Example**:
```java
@Slf4j
@Data
public class BatchPlan {
    private BatchPlanId id;
    private PlanStatus status;
    private List<Slice> slices;

    public void markAsCompleted() {
        if (this.status == PlanStatus.CANCELLED) {
            throw new BatchPlanException("Cannot complete cancelled plan");
        }
        this.status = PlanStatus.COMPLETED;
        DomainEventPublisher.publish(new PlanCompletedEvent(this.id));
    }
}
```

**Value Object Example**:
```java
public record LiteratureId(String value) {
    public LiteratureId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("LiteratureId cannot be null");
        }
    }
}
```

---

### 4. Infrastructure Layer (Driven)

**Module**: `patra-{service}-infra`

**Responsibility**: Implement domain ports, access external resources.

**What Goes Here**:
- Repository implementations (`*RepositoryImpl.java`)
- MyBatis-Plus DOs (`*DO.java`), Mappers
- MapStruct converters (`*Converter.java`)
- External API clients, MQ publishers

**Repository Implementation Example**:
```java
@Repository
@RequiredArgsConstructor
public class ProvenanceRepositoryImpl implements ProvenancePort {
    private final ProvenanceMapper mapper;
    private final ProvenanceConverter converter;

    @Override
    public Provenance findById(ProvenanceId id) {
        ProvenanceDO dataObject = mapper.selectById(id.getValue());
        return converter.toDomain(dataObject);  // DO → Domain
    }

    @Override
    public void save(Provenance provenance) {
        ProvenanceDO dataObject = converter.toDO(provenance);  // Domain → DO
        if (dataObject.getId() == null) {
            mapper.insert(dataObject);
        } else {
            mapper.updateById(dataObject);
        }
    }
}
```

---

## Adapter Layer Organization

### Module-Level Separation (Driving vs Driven)

Papertrace uses **module-level boundaries** to separate driving from driven adapters:

```
patra-{service}-adapter/     ← Driving Adapters ONLY (receive triggers)
├── REST APIs, Scheduled Jobs, Message Consumers
└── Direction: External World → System

patra-{service}-infra/       ← Driven Adapters ONLY (access resources)
├── DB Repositories, External API Clients, MQ Publishers
└── Direction: System → External Resources
```

**Key Principle**: Module boundary provides clear separation. No need for `inbound/outbound/` packages within adapter module.

---

### Package Organization Standard

**Organize by adapter technology type** directly under `adapter/`:

```
com.patra.{service}.adapter/
├── rest/           REST APIs (Spring MVC, OpenAPI)
│   ├── internal/   (optional: microservice-to-microservice)
│   └── public/     (optional: external-facing)
├── scheduler/      Scheduled jobs (XXL-Job)
│   ├── config/
│   ├── job/
│   └── param/
├── stream/         Message consumers (RocketMQ, Kafka)
│   └── dto/
├── graphql/        GraphQL APIs (future)
└── grpc/           gRPC APIs (future)
```

### Why NOT `inbound/` or `driving/`?

1. **Module name provides context**: `patra-{service}-adapter` already indicates adapter layer
2. **Module contract is explicit**: `-adapter` contains ONLY driving adapters (by design)
3. **Semantic redundancy**: `adapter.inbound.rest` repeats "inbound" unnecessarily
4. **No future conflation**: ALL outbound integrations belong in `-infra` module

### Naming Conventions

| Adapter Type | Naming Pattern | Example |
|--------------|----------------|---------|
| REST Controller | `*Controller` or `*EndpointImpl` | `ProvenanceController` |
| Scheduled Job | `*Job` | `PubmedHarvestJob` |
| Message Consumer | `*Consumers` | `IngestStreamConsumers` |

---

## Application Layer Organization Patterns

The Application layer uses two complementary patterns, each suited to different scenarios.

### Pattern 1: Orchestrator + Coordinators

**Structure:**
```
MainOrchestrator (@Transactional)
  ├─ PersistenceCoordinator    (concern: data persistence)
  ├─ IdempotencyCoordinator    (concern: duplicate detection)
  └─ PublishingCoordinator     (concern: event publishing)
```

**Characteristics:**
- **Separation by Concern**: Split by business concerns
- **Unified Transaction**: Main Orchestrator holds `@Transactional`
- **Lightweight Delegation**: Coordinators are helper classes (NO interfaces)
- **Linear Flow**: Suitable for sequential flows

**When to Use:**
- ✅ Entire flow within single transaction
- ✅ No external API calls (or minimal)
- ✅ Concern separation more important than phase isolation
- ✅ Components have no reuse requirements

**Example**:
```java
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator implements PlanIngestionUseCase {
  private final PlanPersistenceCoordinator persistenceCoordinator;
  private final PlanIdempotencyCoordinator idempotencyCoordinator;

  @Override
  @Transactional  // Single transaction for entire flow
  public PlanIngestionResult ingestPlan(PlanIngestionCommand request) {
    // Orchestrate: prepare → validate → assemble → persist → publish
  }
}
```

---

### Pattern 2: Main Orchestrator + Sub-UseCases

**Structure:**
```
MainOrchestrator (NO @Transactional)
  ├─ PrepareUseCase     (phase: preparation, isolated transaction)
  ├─ ExecuteUseCase     (phase: execution, NO transaction)
  └─ CompleteUseCase    (phase: completion, @Transactional)
```

**Characteristics:**
- **Phase Isolation**: Split by execution phases
- **Independent Reuse**: Each Sub-UseCase has interface
- **Distributed Transactions**: Each phase may have own transaction boundary
- **Error Isolation**: Each phase handles exceptions independently

**When to Use:**
- ✅ Multi-phase transactions (only some need transactions)
- ✅ Contains external API calls (must NOT be in transactions)
- ✅ Clear lifecycle and state transitions
- ✅ Sub-UseCases may be reused by other flows

**Example**:
```java
@Service
@RequiredArgsConstructor
public class TaskExecutionUseCaseImpl implements TaskExecutionUseCase {
  private final PrepareTaskExecutionUseCase prepareUseCase;      // NO @Transactional
  private final ExecuteTaskBatchesUseCase executeUseCase;        // NO @Transactional
  private final CompleteTaskExecutionUseCase completeUseCase;    // @Transactional

  @Override
  public void execute(TaskReadyCommand command) {
    // Phase 0: Prepare (may fail fast)
    // Phase 1: Execute (external API calls)
    // Phase 2: Complete (final updates atomically)
  }
}
```

---

### Golden Rules for Transaction Management

**⚠️ Critical Principles:**

1. **NEVER call external APIs within `@Transactional` methods**
   - External APIs can take 10+ seconds, holding DB connections and locks
   - Use Pattern 2 to isolate external calls from transactions

2. **Minimize transaction scope**
   - Only include operations that MUST be atomic
   - Keep transactions short-lived

3. **Example of WRONG approach:**
   ```java
   @Transactional  // ❌ BAD: Long transaction
   public void execute() {
     prepare();           // DB check
     callPubMedAPI();    // 10+ seconds, blocks transaction!
     saveResults();      // DB update
   }
   ```

4. **Example of CORRECT approach:**
   ```java
   public void execute() {
     prepare();              // NO transaction
     callPubMedAPI();       // NO transaction
     complete();            // @Transactional - only final updates
   }
   ```

---

### Pattern Comparison

| Dimension | Orchestrator + Coordinators | Main + Sub-UseCases |
|-----------|----------------------------|---------------------|
| **Split Strategy** | By Concern | By Phase |
| **Component Interface** | None | Yes |
| **Transaction Boundary** | Unified | Distributed |
| **Component Size** | Small (100-200 lines) | Large (200-300 lines) |
| **Reusability** | Low | High |
| **Use Case** | Single transaction | Multi-phase with external calls |

### Decision Tree

```
Does the flow contain external API calls?
  ├─ YES → Use Pattern 2 (Main + Sub-UseCases)
  │         Reason: Avoid long transactions
  └─ NO  → Does the flow need multiple independent transactions?
            ├─ YES → Use Pattern 2
            │         Reason: Phase isolation
            └─ NO  → Use Pattern 1 (Orchestrator + Coordinators)
                      Reason: Simpler, unified transaction
```

---

### Naming Conventions

| Pattern | Naming | Responsibility | Interface | Transaction |
|---------|--------|---------------|-----------|-------------|
| **Sub-UseCase** | `*UseCase` / `*UseCaseImpl` | Complete business phase | ✅ Has | ✅ Independent |
| **Coordinator** | `*Coordinator` | Assists Orchestrator | ❌ None | ❌ Inherits |
| **Orchestrator** | `*Orchestrator` | Main flow orchestration | ✅ Has | ✅ Controls |

---

## Design Patterns Reference

### DDD Patterns (Domain-Driven Design)
- **Aggregate**: Consistency boundary with root entity, enforces invariants
- **Entity**: Identity-based objects (ID-driven equality)
- **Value Object**: Immutable, equality by value (use `record`)
- **Domain Event**: Captures business facts, triggers reactions
- **Repository**: Collection-like interface for aggregate persistence
- **Factory**: Complex aggregate creation logic
- **Domain Service**: Stateless operation spanning aggregates

### GoF Patterns (Common in Papertrace)
- **Strategy**: Multiple algorithm implementations (e.g., parsers per source)
- **Factory**: Object creation (e.g., Provenance creation)
- **Template Method**: Algorithm skeleton with hook points (e.g., AbstractProvenanceScheduleJob)
- **Observer**: Event-driven reactions (domain events)
- **Adapter**: Convert interfaces (ACL between contexts)

### Enterprise Patterns (Fowler)
- **Service Layer**: Application service orchestrators
- **Repository**: Data access abstraction (port/adapter)
- **Data Mapper**: DTO/DO ↔ Domain mapping (MapStruct)
- **Unit of Work**: Transaction boundary management

### Integration Patterns
- **Outbox Pattern**: Reliable event publishing with DB transaction
- **Idempotency Key**: Prevent duplicate processing
- **Retry with Backoff**: Transient failure recovery (exponential backoff)
- **Anti-Corruption Layer (ACL)**: Protect domain from external models

### Data Patterns
- **Optimistic Locking**: Version-based concurrency control (`@Version`)
- **Eventual Consistency**: Async cross-aggregate updates via events
- **Aggregate Persistence**: Save entire aggregate atomically

---

## Design Principles & Philosophy

### Core Principles
- **Self-contained use cases**: Each use case dir has command/dto/logic
- **Naming conventions**: `*Orchestrator`, `*Command`, `*Port`, `*DO`
- **Contract-first**: Define `*-api` contracts → Domain → App → Infra → Adapter
- **Simplicity first**: Solve current problem, avoid over-engineering
- **YAGNI**: You Aren't Gonna Need It - don't build for hypothetical futures
- **Fail fast**: Validate early, make errors obvious

### Pattern Selection Guidelines
- **Start simple**: Use simplest pattern that solves the problem
- **Refactor when needed**: Abstract after 3rd duplication (Rule of Three)
- **Match context**: Choose patterns that fit team skills
- **Consider trade-offs**: Every pattern has complexity cost vs. flexibility benefit

---

## Testing Strategy

### Domain Layer
- Pure Java unit tests, NO mocks needed
- Test business rules in isolation

```java
@Test
void should_throw_exception_when_completing_cancelled_plan() {
    BatchPlan plan = new BatchPlan();
    plan.setStatus(PlanStatus.CANCELLED);
    assertThrows(BatchPlanException.class, () -> plan.markAsCompleted());
}
```

### Application Layer
- Mock ports (domain interfaces)
- Test orchestration logic

```java
@ExtendWith(MockitoExtension.class)
class PlanIngestionOrchestratorTest {
    @Mock private ProvenancePort provenancePort;
    @InjectMocks private PlanIngestionOrchestrator orchestrator;

    @Test
    void should_create_plan_successfully() {
        when(provenancePort.findById(any())).thenReturn(provenance);
        PlanIngestionResult result = orchestrator.ingest(command);
        assertThat(result.isSuccess()).isTrue();
    }
}
```

### Infrastructure Layer
- Integration tests in boot module with TestContainers

```java
@SpringBootTest
@Testcontainers
class ProvenanceRepositoryImplTest {
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired private ProvenancePort provenancePort;

    @Test
    void should_save_and_find_provenance() {
        provenancePort.save(provenance);
        Provenance found = provenancePort.findById(provenance.getId());
        assertThat(found).isNotNull();
    }
}
```

### ArchUnit (Dependency Validation)
```java
@Test
void domain_should_not_depend_on_spring() {
    noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat().resideInAPackage("org.springframework..")
        .check(importedClasses);
}
```

---

## Quick Reference

### Where to Put New Code?

| Question | Answer |
|----------|--------|
| Receives HTTP request? | `adapter/rest/*Controller.java` |
| Scheduled job? | `adapter/scheduler/job/*Job.java` |
| Message consumer? | `adapter/stream/*Consumers.java` |
| Orchestrates use case? | `app/usecase/{feature}/*Orchestrator.java` |
| Business logic? | `domain/model/entity/*.java` |
| Database access? | `infra/persistence/repository/*RepositoryImpl.java` |
| MyBatis-Plus entity? | `infra/persistence/entity/*DO.java` |
| Port interface? | `domain/port/*Port.java` |

---

## Related Files

- [adapter-layer-patterns.md](adapter-layer-patterns.md) - XXL-Job, Template Method patterns
- [orchestrator-coordinator-patterns.md](orchestrator-coordinator-patterns.md) - Application layer deep dive
- [domain-modeling-patterns.md](domain-modeling-patterns.md) - DDD tactical patterns
- [mybatis-plus-patterns.md](mybatis-plus-patterns.md) - Infrastructure layer persistence
- [outbox-pattern.md](outbox-pattern.md) - Reliable event publishing
- [event-driven-architecture.md](event-driven-architecture.md) - Domain events, handlers

---

**This architecture is non-negotiable in Papertrace. Violations will be caught by ArchUnit tests and code reviews.**
