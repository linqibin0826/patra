# Hexagonal Architecture + DDD Overview

Complete guide to Papertrace's architectural patterns combining Hexagonal Architecture (Ports & Adapters) with Domain-Driven Design.

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

### ⚠️ Forbidden Dependencies

```java
// ❌ NEVER: Domain depending on Infrastructure
package com.patra.ingest.domain.model.entity;
import com.patra.ingest.infra.persistence.entity.BatchPlanDO;  // WRONG!

// ❌ NEVER: Domain depending on Spring
package com.patra.ingest.domain.model.entity;
import org.springframework.stereotype.Service;  // WRONG!
@Service
public class BatchPlan { }

// ❌ NEVER: Application depending on Infrastructure directly
package com.patra.ingest.app.usecase.plan;
import com.patra.ingest.infra.persistence.repository.ProvenanceRepositoryImpl;  // WRONG!
// Instead, depend on domain port:
import com.patra.ingest.domain.port.ProvenancePort;  // CORRECT!
```

### ✅ Allowed Dependencies

```java
// ✅ Infrastructure implements Domain port
package com.patra.ingest.infra.persistence.repository;
import com.patra.ingest.domain.port.ProvenancePort;  // Correct!
import com.patra.ingest.domain.model.entity.Provenance;  // Correct!

public class ProvenanceRepositoryImpl implements ProvenancePort { }

// ✅ Domain uses Lombok
package com.patra.ingest.domain.model.entity;
import lombok.Data;  // Correct!
@Data
public class BatchPlan { }

// ✅ Application depends on Domain
package com.patra.ingest.app.usecase.plan;
import com.patra.ingest.domain.model.entity.BatchPlan;  // Correct!
import com.patra.ingest.domain.port.ProvenancePort;  // Correct!
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
- Request/Response DTOs
- Validation (`@Valid`)

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
- Use case commands/DTOs
- Domain event handlers
- Transaction boundaries (`@Transactional`)

**What Does NOT Go Here**:
- ❌ Business rules (belongs in Domain)
- ❌ Database queries (use ports)
- ❌ External API calls (use ports)

**Pattern 1: Orchestrator + Coordinators** (for complex flows with single transaction):

```java
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator implements PlanIngestionUseCase {
    private final PlanPersistenceCoordinator persistenceCoordinator;
    private final PlanIdempotencyCoordinator idempotencyCoordinator;
    private final PlanPublishingCoordinator publishingCoordinator;

    @Override
    @Transactional  // Single transaction boundary
    public PlanIngestionResult ingest(PlanIngestionCommand command) {
        // Phase 1: Prepare data
        // Phase 2: Validate business rules (delegate to Domain)
        // Phase 3: Assemble domain objects (delegate to Domain Factory)
        // Phase 4: Check idempotency
        // Phase 5: Persist (delegate to coordinator)
        // Phase 6: Publish events (delegate to coordinator)
    }
}
```

**Pattern 2: Main Orchestrator + Sub-UseCases** (for multi-phase with independent transactions):

```java
@Service
@RequiredArgsConstructor
public class TaskExecutionUseCaseImpl implements TaskExecutionUseCase {
    private final PrepareTaskExecutionUseCase prepareUseCase;  // NO @Transactional
    private final ExecuteTaskBatchesUseCase executeUseCase;    // NO @Transactional
    private final CompleteTaskExecutionUseCase completeUseCase; // @Transactional

    @Override
    public void execute(TaskReadyCommand command) {
        // Phase 0: Prepare (fast checks, may fail fast)
        // Phase 1: Execute (external API calls, NO transaction)
        // Phase 2: Complete (update state atomically, @Transactional)
    }
}
```

**Key Rule**: NEVER call external APIs within `@Transactional` methods (holds DB locks too long).

---

### 3. Domain Layer (Core)

**Module**: `patra-{service}-domain`

**Responsibility**: Pure business logic and rules.

**What Goes Here**:
- Aggregates and Entities
- Value Objects (`record` for immutables)
- Domain Events
- Port interfaces (define what domain needs)
- Factories (complex object creation)
- Domain exceptions

**What Does NOT Go Here**:
- ❌ Spring annotations (`@Service`, `@Repository`, `@Autowired`)
- ❌ Database entities (DOs belong in Infrastructure)
- ❌ HTTP DTOs (belong in Adapter)
- ❌ Transaction management

**Allowed Dependencies**:
- ✅ Lombok (`@Data`, `@Slf4j`, etc.)
- ✅ Hutool utilities
- ✅ patra-common base classes
- ✅ Java standard library

**Aggregate Example**:
```java
@Slf4j
@Data
public class BatchPlan {
    private BatchPlanId id;
    private ProvenanceId provenanceId;
    private PlanStatus status;
    private List<Slice> slices;
    private Instant createdAt;

    // Business logic method
    public void markAsCompleted() {
        // Business rule validation
        if (this.status == PlanStatus.CANCELLED) {
            throw new BatchPlanException("Cannot complete cancelled plan");
        }
        if (this.slices.stream().anyMatch(s -> !s.isCompleted())) {
            throw new BatchPlanException("Cannot complete plan with incomplete slices");
        }

        this.status = PlanStatus.COMPLETED;
        
        // Emit domain event for cross-aggregate reactions
        DomainEventPublisher.publish(new PlanCompletedEvent(this.id));
    }
}
```

**Value Object Example (using `record`)**:
```java
public record LiteratureId(String value) {
    public LiteratureId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("LiteratureId cannot be null or empty");
        }
    }

    public static LiteratureId of(String value) {
        return new LiteratureId(value);
    }
}
```

**Port Interface Example**:
```java
public interface ProvenancePort {
    Provenance findById(ProvenanceId id);
    void save(Provenance provenance);
    List<Provenance> findByStatus(ProvenanceStatus status);
    boolean existsByName(String name);
}
```

---

### 4. Infrastructure Layer (Driven)

**Module**: `patra-{service}-infra`

**Responsibility**: Implement domain ports, access external resources.

**What Goes Here**:
- Repository implementations (`*RepositoryImpl.java`)
- MyBatis-Plus DOs (`*DO.java`)
- MyBatis-Plus Mappers
- MapStruct converters (`*Converter.java`)
- External API clients
- MQ publishers

**What Does NOT Go Here**:
- ❌ Business logic (belongs in Domain)
- ❌ Use case orchestration (belongs in Application)

**Repository Implementation Example**:
```java
@Repository
@RequiredArgsConstructor
public class ProvenanceRepositoryImpl implements ProvenancePort {
    private final ProvenanceMapper mapper;  // MyBatis-Plus
    private final ProvenanceConverter converter;  // MapStruct

    @Override
    public Provenance findById(ProvenanceId id) {
        ProvenanceDO dataObject = mapper.selectById(id.getValue());
        if (dataObject == null) {
            return null;
        }
        return converter.toDomain(dataObject);  // DO → Domain
    }

    @Override
    public void save(Provenance provenance) {
        ProvenanceDO dataObject = converter.toDO(provenance);  // Domain → DO
        if (dataObject.getId() == null) {
            mapper.insert(dataObject);
            provenance.setId(ProvenanceId.of(dataObject.getId()));
        } else {
            mapper.updateById(dataObject);
        }
    }
}
```

**MyBatis-Plus DO Example**:
```java
@Data
@TableName("t_provenance")
public class ProvenanceDO {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;
    private String description;
    
    @TableField(typeHandler = JacksonTypeHandler.class)
    private JsonNode configJson;  // Store complex config as JSON

    private Integer status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    @TableLogic
    private Boolean deleted;
    
    @Version
    private Integer version;  // Optimistic locking
}
```

**MapStruct Converter Example**:
```java
@Mapper(componentModel = "spring")
public interface ProvenanceConverter {
    @Mapping(target = "id", expression = "java(ProvenanceId.of(source.getId()))")
    @Mapping(target = "status", expression = "java(ProvenanceStatus.fromCode(source.getStatus()))")
    Provenance toDomain(ProvenanceDO source);

    @Mapping(target = "id", expression = "java(source.getId() != null ? source.getId().getValue() : null)")
    @Mapping(target = "status", expression = "java(source.getStatus().getCode())")
    ProvenanceDO toDO(Provenance source);
}
```

---

## Module Isolation & API Module

### API Module (`patra-{service}-api`)

**Purpose**: Define external contracts (DTOs, error codes) that other services can depend on.

**Key Rule**: NO framework dependencies (pure POJOs).

**What Goes Here**:
- Request/Response DTOs (for remote calls)
- Error codes (enums)
- Constants shared externally

**Example**:
```java
// api/dto/CreateProvenanceRequest.java
public record CreateProvenanceRequest(
    @NotBlank String name,
    String description,
    ProvenanceType type
) { }

// api/error/IngestErrorCode.java
public enum IngestErrorCode {
    PROVENANCE_NOT_FOUND("INGEST_001", "Provenance not found"),
    PLAN_CREATION_FAILED("INGEST_002", "Plan creation failed");

    private final String code;
    private final String message;
}
```

---

## Transaction Management

### Rules

1. **@Transactional at Orchestrator level ONLY**
2. **NEVER call external APIs within transactions** (holds DB locks)
3. **Keep transactions short** (minimize lock contention)

### Pattern: Isolate External Calls from Transactions

```java
// ❌ BAD: External API call inside transaction
@Transactional
public void execute() {
    prepare();           // DB query
    callPubMedAPI();    // 10+ seconds, blocks transaction!
    saveResults();      // DB update
}

// ✅ GOOD: External API call outside transaction
public void execute() {
    prepare();              // NO transaction
    callPubMedAPI();       // NO transaction - external API
    complete();            // @Transactional - only final DB updates
}
```

---

## Event-Driven Architecture

### Domain Events

**Purpose**: Decouple aggregates, enable cross-aggregate reactions.

**Pattern**:
```java
// Domain layer - emit event
public class BatchPlan {
    public void markAsCompleted() {
        this.status = PlanStatus.COMPLETED;
        DomainEventPublisher.publish(new PlanCompletedEvent(this.id));
    }
}

// Application layer - handle event
@Component
@RequiredArgsConstructor
public class PlanCompletedEventHandler {
    private final OutboxPublisher outboxPublisher;

    @EventListener
    @Transactional
    public void handlePlanCompleted(PlanCompletedEvent event) {
        // Persist to outbox for reliable publishing
        outboxPublisher.publishPlanCompletedMessage(event);
    }
}
```

### Outbox Pattern (Papertrace Core)

**Purpose**: Reliable event publishing with transactional guarantees.

**Flow**:
1. Business transaction writes to domain table + outbox table (atomic)
2. Separate relay job polls outbox table
3. Publishes to MQ
4. Marks as published in outbox

**See**: [outbox-pattern.md](outbox-pattern.md) for complete implementation.

---

## Validation & Error Handling

### Validation Layers

1. **Adapter Layer**: `@Valid` for request format
2. **Domain Layer**: Business rule validation
3. **Application Layer**: Cross-aggregate validation

**Example**:
```java
// Adapter - format validation
@PostMapping
public ResponseEntity<ProvenanceResponse> create(
    @Valid @RequestBody CreateProvenanceCommand command  // @NotBlank, @Size, etc.
) { }

// Domain - business rule validation
public class Provenance {
    public void setName(String name) {
        if (name.length() < 3) {
            throw new ProvenanceException("Name must be at least 3 characters");
        }
        this.name = name;
    }
}
```

### Error Mapping

**Flow**: Domain Exception → App Exception → ProblemDetail (HTTP)

```java
// Domain exception
public class ProvenanceException extends RuntimeException { }

// Global exception handler (adapter layer)
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(ProvenanceException.class)
    public ProblemDetail handleProvenanceException(ProvenanceException ex) {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            ex.getMessage()
        );
    }
}
```

---

## Testing Strategy

### Domain Layer
- **Pure Java unit tests**, NO mocks needed
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
    @Mock
    private ProvenancePort provenancePort;

    @InjectMocks
    private PlanIngestionOrchestrator orchestrator;

    @Test
    void should_create_plan_successfully() {
        // Given
        when(provenancePort.findById(any())).thenReturn(provenance);

        // When
        PlanIngestionResult result = orchestrator.ingest(command);

        // Then
        assertThat(result.isSuccess()).isTrue();
    }
}
```

### Infrastructure Layer
- Integration tests in boot module
- Use TestContainers for DB

```java
@SpringBootTest
@Testcontainers
class ProvenanceRepositoryImplTest {
    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0");

    @Autowired
    private ProvenancePort provenancePort;

    @Test
    void should_save_and_find_provenance() {
        // Given
        Provenance provenance = new Provenance();

        // When
        provenancePort.save(provenance);
        Provenance found = provenancePort.findById(provenance.getId());

        // Then
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

### Dependency Check

```bash
# ✅ Allowed
adapter → app, api
app → domain
infra → domain
domain → patra-common, Lombok, Hutool

# ❌ Forbidden
domain → infra, app, adapter, Spring
app → infra
```

---

## Next Steps

1. **Read layer-specific guides**:
   - [adapter-layer-patterns.md](adapter-layer-patterns.md)
   - [orchestrator-coordinator-patterns.md](orchestrator-coordinator-patterns.md)
   - [domain-modeling-patterns.md](domain-modeling-patterns.md)
   - [mybatis-plus-patterns.md](mybatis-plus-patterns.md)

2. **Run ArchUnit tests** to validate dependencies

3. **Check complete examples** in [complete-examples.md](complete-examples.md)

---

**This architecture is non-negotiable in Papertrace. Violations will be caught by ArchUnit tests and code reviews.**
