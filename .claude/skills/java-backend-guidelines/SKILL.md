---
name: java-backend-guidelines
description: Comprehensive Java backend development guide for Hexagonal Architecture + DDD with Spring Boot 3.5.7. Use when creating REST endpoints, orchestrators, domain entities, aggregates, repositories, or working with MyBatis-Plus, MapStruct, validation, Nacos configuration, Outbox pattern, event-driven architecture, or testing strategies. Covers four-layer architecture (Adapter → Application → Domain ← Infrastructure), dependency directions, transaction management, error handling, and performance optimization.
---

# Java Backend Development Guidelines (Papertrace)

## Purpose

Establish consistency and best practices across Papertrace backend microservices (**patra-ingest**, **patra-registry**, **patra-gateway**) using **Hexagonal Architecture + DDD** with Spring Boot 3.5.7, Java 25, and MyBatis-Plus.

**Core Principle**: Domain-driven design with clear layer boundaries, pure domain logic, and dependency inversion.

---

## When to Use This Skill

Automatically activates when working on:

**Adapter Layer:**
- Creating or modifying REST endpoints, APIs (@RestController)
- Building scheduled jobs (XXL-Job)
- Implementing message consumers (RocketMQ, Kafka)

**Application Layer:**
- Implementing orchestrators (use case coordination)
- Creating coordinators (concern separation)
- Managing transactions (@Transactional)
- Cross-aggregate coordination

**Domain Layer:**
- Modeling aggregates, entities, value objects
- Defining port interfaces
- Domain events and business rules
- Factory patterns

**Infrastructure Layer:**
- MyBatis-Plus repositories
- MapStruct DO ↔ Domain mapping
- External API integrations
- Database access patterns

**Cross-Cutting:**
- Validation and error handling
- Configuration management (Nacos)
- Outbox pattern implementation
- Performance optimization
- Testing (Unit, Integration, ArchUnit)

---

## Quick Start

### New Backend Feature Checklist

```
□ Define Use Case in Application Layer
  ├─ Create command/dto classes
  ├─ Implement orchestrator with @Transactional
  └─ (Optional) Create coordinators for concern separation

□ Model Domain Logic
  ├─ Design aggregate root and entities
  ├─ Define value objects (use record for immutables)
  ├─ Create port interfaces
  └─ Implement business rules in domain

□ Build Adapter (Driving)
  ├─ REST Controller with @Valid validation
  ├─ OR Scheduled Job (XXL-Job)
  ├─ OR Message Consumer (RocketMQ)
  └─ Delegate to orchestrator

□ Implement Infrastructure (Driven)
  ├─ Create MyBatis-Plus DO entity
  ├─ Implement repository with MapStruct converter
  ├─ Define XML mapper (for complex queries)
  └─ Never expose DOs outside infra layer

□ Testing
  ├─ Domain unit tests (pure Java, no mocks)
  ├─ Application unit tests (mock ports)
  ├─ Integration tests in boot module (TestContainers)
  └─ ArchUnit tests for dependency rules
```

### New Microservice Checklist

```
□ Module Structure
  ├─ patra-{service}-api (external contracts)
  ├─ patra-{service}-domain (pure Java, Lombok, Hutool)
  ├─ patra-{service}-app (orchestrators, @Transactional)
  ├─ patra-{service}-infra (MyBatis-Plus, MapStruct)
  ├─ patra-{service}-adapter (REST, Jobs, Streams)
  └─ patra-{service}-boot (main app, Spring config)

□ Core Components
  ├─ Nacos configuration
  ├─ Error mapping (ProblemDetail)
  ├─ Validation framework (@Valid)
  ├─ Outbox pattern setup
  ├─ Observability (SLF4J, Micrometer)
  └─ ArchUnit validation

□ Reuse Starters
  ├─ patra-spring-boot-starter-core (base config)
  ├─ patra-spring-boot-starter-web (MVC, validation)
  └─ patra-spring-boot-starter-mybatis (MyBatis-Plus)
```

---

## Architecture Overview

### Hexagonal Architecture (Ports & Adapters) + DDD

**Four Layers** (outer → inner, dependencies flow inward):

```
┌─────────────────────────────────────────────────┐
│  Adapter Layer (Driving - Outermost)           │
│  REST Controllers, Jobs, Message Consumers      │
│  ↓ depends on ↓                                  │
├─────────────────────────────────────────────────┤
│  Application Layer                              │
│  Orchestrators, Coordinators, Use Cases         │
│  ↓ depends on ↓                                  │
├─────────────────────────────────────────────────┤
│  Domain Layer (Core - Pure Java)                │
│  Aggregates, Entities, VOs, Events, Ports       │
│  ↑ implemented by ↑                              │
├─────────────────────────────────────────────────┤
│  Infrastructure Layer (Driven)                  │
│  Repositories, External APIs, DB Access         │
└─────────────────────────────────────────────────┘
```

**Key Principles:**
1. **Dependencies flow inward**: Adapter → App → Domain ← Infra
2. **Domain is pure Java**: NO Spring/framework dependencies (only Lombok, Hutool, patra-common)
3. **Ports & Adapters**: Domain defines ports (interfaces), Infra implements adapters
4. **Orchestrators coordinate**: App layer orchestrates, Domain layer decides

**See:** [architecture-overview.md](resources/architecture-overview.md) for complete details.

---

## Directory Structure

### Multi-Module Maven Project

```
patra-{service}/
├── patra-{service}-api/               # External Contracts
│   ├── dto/                           # Request/Response DTOs
│   ├── error/                         # Error codes
│   └── NO framework dependencies
│
├── patra-{service}-domain/            # Domain Layer (Pure Java)
│   ├── model/
│   │   ├── entity/                    # Aggregates & Entities
│   │   ├── vo/                        # Value Objects (record)
│   │   └── enums/                     # Domain enums
│   ├── port/                          # Port interfaces
│   ├── factory/                       # Domain factories
│   ├── event/                         # Domain events
│   └── Dependencies: patra-common + Lombok + Hutool ONLY
│
├── patra-{service}-app/               # Application Layer
│   ├── usecase/
│   │   └── {feature}/                 # Self-contained use case
│   │       ├── *Orchestrator.java     # Main orchestrator (@Transactional)
│   │       ├── *Coordinator.java      # Coordinators (optional)
│   │       ├── command/               # Commands
│   │       └── dto/                   # Internal DTOs
│   ├── eventhandler/                  # Domain event handlers
│   ├── outbox/                        # Outbox pattern
│   └── Dependencies: domain + patra-common + core starter
│
├── patra-{service}-infra/             # Infrastructure Layer (Driven Adapters)
│   ├── persistence/
│   │   ├── entity/                    # MyBatis-Plus DOs (*DO.java)
│   │   ├── mapper/                    # MyBatis-Plus Mappers
│   │   ├── converter/                 # MapStruct converters
│   │   └── repository/                # Repository implementations (*RepositoryImpl.java)
│   ├── integration/                   # External API adapters
│   ├── messaging/                     # MQ publishers
│   └── Dependencies: domain + mybatis starter + core starter
│
├── patra-{service}-adapter/           # Adapter Layer (Driving Adapters)
│   ├── rest/                          # REST APIs
│   │   ├── *Controller.java           # Spring MVC controllers
│   │   └── dto/                       # Controller DTOs
│   ├── scheduler/                     # Scheduled jobs
│   │   ├── config/                    # XXL-Job config
│   │   ├── job/                       # *Job.java
│   │   └── param/                     # Job parameters
│   └── stream/                        # Message consumers
│       └── *Consumers.java            # RocketMQ/Kafka consumers
│
└── patra-{service}-boot/              # Main Application
    ├── config/                        # Spring configurations
    ├── PatraXxxApplication.java       # @SpringBootApplication
    └── application.yml                # Configuration (local only, use Nacos)
```

**Module Dependencies (MUST FOLLOW):**

```
adapter  →  app + api (+ web starters)
app      →  domain + patra-common + core starter
infra    →  domain + mybatis starter + core starter
domain   →  patra-common + Lombok + Hutool (NO Spring!)
api      →  NO framework dependencies (external contracts)
boot     →  ALL modules + Spring Boot starters
```

**⚠️ Violation of dependency rules is NOT acceptable!**

---

## Naming Conventions

### General Java Naming

| Element | Convention | Example | Notes |
|---------|-----------|---------|-------|
| **Package** | lowercase | `com.patra.ingest.domain` | No separators |
| **Class** | UpperCamelCase | `BatchPlan`, `Provenance` | Noun |
| **Interface** | UpperCamelCase | `ProvenancePort` | No `I` prefix |
| **Method** | lowerCamelCase | `createPlan()` | Verb, boolean: `is/has/can` |
| **Variable** | lowerCamelCase | `provenanceId` | Noun |
| **Constant** | UPPER_SNAKE_CASE | `MAX_BATCH_SIZE` | `static final` |
| **Enum Type** | UpperCamelCase | `BatchStatus` | Noun |
| **Enum Value** | UPPER_SNAKE_CASE | `PENDING`, `RUNNING` | - |

### DDD/Hexagonal Naming Patterns

| Layer | Pattern | Example | Notes |
|-------|---------|---------|-------|
| **Domain Entity** | No suffix | `BatchPlan`, `Provenance` | Aggregate roots and entities |
| **Value Object** | No suffix | `LiteratureId` | Use `record` for immutables |
| **Port Interface** | `Port` | `ProvenancePort`, `LiteraturePort` | Domain-defined interfaces |
| **Domain Event** | `Event` | `PlanCreatedEvent` | Past tense |
| **Factory** | `Factory` | `OutboxMessageFactory` | - |
| **Orchestrator** | `Orchestrator` | `PlanIngestionOrchestrator` | Use case coordinator |
| **Coordinator** | `Coordinator` | `PlanPersistenceCoordinator` | Concern separation |
| **Controller** | `Controller` | `ProvenanceController` | REST adapter |
| **Job** | `Job` | `PubmedHarvestJob` | Scheduled job |
| **Consumer** | `Consumers` | `IngestStreamConsumers` | Message consumer |
| **Repository Impl** | `RepositoryImpl` | `ProvenanceRepositoryImpl` | Port implementation |
| **DO (Data Object)** | `DO` | `ProvenanceDO` | MyBatis-Plus entity |
| **Converter** | `Converter` | `ProvenanceConverter` | MapStruct mapper |
| **Command** | `Command` | `CreateProvenanceCommand` | Request DTO |
| **Response** | `Response` or `Result` | `ProvenanceResponse` | Response DTO |

### File Naming Examples

```
✅ Good:
- ProvenanceController.java           (REST adapter)
- PlanIngestionOrchestrator.java      (Application orchestrator)
- BatchPlan.java                      (Domain aggregate)
- ProvenancePort.java                 (Domain port)
- ProvenanceRepositoryImpl.java       (Infrastructure implementation)
- ProvenanceDO.java                   (MyBatis-Plus entity)
- ProvenanceConverter.java            (MapStruct converter)
- LiteratureId.java (record)          (Value object)
- PlanCreatedEvent.java               (Domain event)

❌ Bad:
- IProvenancePort.java                (No "I" prefix)
- ProvenanceRepositoryImplementation  (Too verbose)
- ProvenanceEntity.java               (Don't use "Entity" suffix in domain)
- ProvenanceDAO.java                  (Don't use "DAO", use "RepositoryImpl")
```

---

## Layer-Specific Guidelines

### Adapter Layer (Driving)

**Purpose:** Receive external triggers and delegate to Application layer

**REST Controllers:**
```java
@RestController
@RequestMapping("/api/v1/provenances")
@RequiredArgsConstructor
public class ProvenanceController {
    private final ProvenanceManagementUseCase provenanceManagementUseCase;

    @PostMapping
    public ResponseEntity<ProvenanceResponse> create(
        @Valid @RequestBody CreateProvenanceCommand command
    ) {
        // Validation happens via @Valid
        // Delegate to orchestrator
        ProvenanceResult result = provenanceManagementUseCase.create(command);
        return ResponseEntity.ok(ProvenanceResponse.from(result));
    }
}
```

**Key Points:**
- ✅ Use `@Valid` for validation
- ✅ Delegate to orchestrator, NO business logic
- ✅ Map results to response DTOs
- ✅ Use ProblemDetail for error responses
- ❌ NO direct calls to Infrastructure layer

**See:** [adapter-layer-patterns.md](resources/adapter-layer-patterns.md)

---

### Application Layer (Orchestration)

**Purpose:** Coordinate use cases, manage transactions, NO business rules

**Orchestrator Pattern:**
```java
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator implements PlanIngestionUseCase {
    private final PlanPersistenceCoordinator persistenceCoordinator;
    private final PlanIdempotencyCoordinator idempotencyCoordinator;
    private final PlanPublishingCoordinator publishingCoordinator;

    @Override
    @Transactional  // Transaction boundary at orchestrator level
    public PlanIngestionResult ingest(PlanIngestionCommand command) {
        // Phase 1: Prepare
        // Phase 2: Validate
        // Phase 3: Assemble (delegate to Domain)
        // Phase 4: Check idempotency
        // Phase 5: Persist (delegate to Coordinator)
        // Phase 6: Publish events (delegate to Coordinator)
    }
}
```

**Key Points:**
- ✅ Orchestrate only, delegate business logic to Domain
- ✅ Manage transactions (@Transactional)
- ✅ Coordinate multiple coordinators
- ❌ NO business rules (belong in Domain)

**See:** [orchestrator-coordinator-patterns.md](resources/orchestrator-coordinator-patterns.md)

---

### Domain Layer (Business Logic)

**Purpose:** Pure business logic, NO framework dependencies

**Aggregate Example:**
```java
@Slf4j
@Data  // Lombok (allowed)
public class BatchPlan {
    private BatchPlanId id;
    private ProvenanceId provenanceId;
    private PlanStatus status;
    private List<Slice> slices;

    // Business logic methods
    public void markAsCompleted() {
        // Business rule validation
        if (this.status == PlanStatus.CANCELLED) {
            throw new BatchPlanException("Cannot complete cancelled plan");
        }
        this.status = PlanStatus.COMPLETED;
        // Emit domain event
        DomainEventPublisher.publish(new PlanCompletedEvent(this.id));
    }
}
```

**Key Points:**
- ✅ Pure Java (Lombok, Hutool allowed)
- ✅ Business rules in domain methods
- ✅ Domain events for cross-aggregate communication
- ❌ NO Spring annotations (@Service, @Autowired, etc.)
- ❌ NO framework dependencies

**See:** [domain-modeling-patterns.md](resources/domain-modeling-patterns.md)

---

### Infrastructure Layer (Driven)

**Purpose:** Implement domain ports, provide data access

**Repository Implementation:**
```java
@Repository
@RequiredArgsConstructor
public class ProvenanceRepositoryImpl implements ProvenancePort {
    private final ProvenanceMapper mapper;
    private final ProvenanceConverter converter;

    @Override
    public Provenance findById(ProvenanceId id) {
        ProvenanceDO dataObject = mapper.selectById(id.getValue());
        return converter.toDomain(dataObject);  // MapStruct conversion
    }

    @Override
    public void save(Provenance provenance) {
        ProvenanceDO dataObject = converter.toDO(provenance);
        mapper.insert(dataObject);
    }
}
```

**Key Points:**
- ✅ Implement domain port interfaces
- ✅ Use MapStruct for DO ↔ Domain conversion
- ✅ MyBatis-Plus for simple queries, XML for complex
- ❌ NEVER expose DOs outside infrastructure layer

**See:** [mybatis-plus-patterns.md](resources/mybatis-plus-patterns.md)

---

## Resource Files

Detailed guides for specific topics (each <500 lines):

### Architecture & Design
- **[architecture-overview.md](resources/architecture-overview.md)** - Complete Hexagonal + DDD architecture
- **[dependency-rules.md](resources/dependency-rules.md)** - Layer dependencies and validation

### Layer-Specific Patterns
- **[adapter-layer-patterns.md](resources/adapter-layer-patterns.md)** - REST, Jobs, Consumers
- **[orchestrator-coordinator-patterns.md](resources/orchestrator-coordinator-patterns.md)** - Application layer organization
- **[domain-modeling-patterns.md](resources/domain-modeling-patterns.md)** - Aggregates, Entities, VOs, Events
- **[mybatis-plus-patterns.md](resources/mybatis-plus-patterns.md)** - Database access and mapping

### Cross-Cutting Concerns
- **[transaction-error-handling.md](resources/transaction-error-handling.md)** - @Transactional, ProblemDetail
- **[validation-patterns.md](resources/validation-patterns.md)** - @Valid, custom validators
- **[nacos-configuration.md](resources/nacos-configuration.md)** - Configuration management
- **[observability-guide.md](resources/observability-guide.md)** - Logging, tracing, metrics

### Advanced Topics
- **[outbox-pattern.md](resources/outbox-pattern.md)** - Reliable event publishing
- **[event-driven-architecture.md](resources/event-driven-architecture.md)** - Domain events, handlers
- **[testing-guide.md](resources/testing-guide.md)** - Unit, Integration, ArchUnit
- **[performance-optimization.md](resources/performance-optimization.md)** - N+1 queries, batch operations

### Complete Examples
- **[complete-examples.md](resources/complete-examples.md)** - Full feature implementation from Papertrace

---

## Common Anti-Patterns to Avoid

### ❌ Domain Layer Violations

```java
// ❌ BAD: Spring annotations in Domain
@Service  // NO! Domain is pure Java
public class BatchPlan {
    @Autowired  // NO! No dependency injection in domain
    private ProvenancePort provenancePort;
}

// ✅ GOOD: Pure Java with Lombok
@Data
public class BatchPlan {
    private ProvenanceId provenanceId;

    // Business logic only
    public void validate() { /* ... */ }
}
```

### ❌ Wrong Dependency Direction

```java
// ❌ BAD: Domain depending on Infrastructure
// domain/model/entity/BatchPlan.java
import com.patra.ingest.infra.persistence.entity.BatchPlanDO;  // NO!

// ✅ GOOD: Infrastructure depending on Domain
// infra/persistence/repository/BatchPlanRepositoryImpl.java
import com.patra.ingest.domain.model.entity.BatchPlan;  // YES!
```

### ❌ Business Logic in Wrong Layer

```java
// ❌ BAD: Business logic in Controller
@PostMapping
public ResponseEntity<ProvenanceResponse> create(@RequestBody CreateCommand command) {
    if (command.getName().length() < 3) {  // NO! This is business logic
        throw new ValidationException("Name too short");
    }
    // ...
}

// ✅ GOOD: Business logic in Domain
// Domain layer
public class Provenance {
    public void setName(String name) {
        if (name.length() < 3) {  // YES! Business rule in domain
            throw new ProvenanceException("Name must be at least 3 characters");
        }
        this.name = name;
    }
}
```

### ❌ Exposing DOs Outside Infrastructure

```java
// ❌ BAD: Returning DO from repository
public interface ProvenancePort {
    ProvenanceDO findById(Long id);  // NO! Never expose DO
}

// ✅ GOOD: Return Domain entity
public interface ProvenancePort {
    Provenance findById(ProvenanceId id);  // YES! Return domain entity
}
```

---

## Quick Decision Tree

**Need to add a new feature?**

1. **Where does the trigger come from?**
   - REST API → Create Controller in `adapter/rest/`
   - Scheduled task → Create Job in `adapter/scheduler/job/`
   - Message queue → Create Consumer in `adapter/stream/`

2. **What orchestrates the use case?**
   - Create Orchestrator in `app/usecase/{feature}/`
   - Add Coordinators if concerns are complex

3. **What are the business rules?**
   - Model in Domain layer (`domain/model/entity/`)
   - Define ports in `domain/port/`

4. **How to persist data?**
   - Create DO in `infra/persistence/entity/`
   - Implement repository in `infra/persistence/repository/`
   - Use MapStruct converter

5. **How to test?**
   - Domain: Pure Java unit tests
   - App: Mock ports, test orchestration
   - Infra: Integration tests in boot module (TestContainers)
   - Adapter: MockMvc for REST, Integration tests for jobs

---

## Getting Help

**Unsure where to put code?**
1. Ask: "Does this code contain business rules?" → Domain
2. Ask: "Does this code coordinate use cases?" → Application
3. Ask: "Does this code receive external triggers?" → Adapter
4. Ask: "Does this code access external resources?" → Infrastructure

**Dependency violation?**
- Check: [dependency-rules.md](resources/dependency-rules.md)
- Run: ArchUnit tests to validate

**Transaction management?**
- Check: [transaction-error-handling.md](resources/transaction-error-handling.md)
- Rule: @Transactional at orchestrator level only

**Performance issues?**
- Check: [performance-optimization.md](resources/performance-optimization.md)
- Watch for: N+1 queries, missing indexes, large batch sizes

---

## Code Style Reference

**All code MUST follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)**

**Key Highlights:**
- 2-space indentation (NOT 4)
- Max line length: 100 characters
- Always use braces for control structures
- One variable per declaration
- Static imports for constants only

**See:** Papertrace's AGENTS-development.md for complete coding standards.

---

**Next Steps:**
1. Browse resource files for deep dives
2. Check [complete-examples.md](resources/complete-examples.md) for full feature implementations
3. Run ArchUnit tests to validate layer dependencies
4. Consult [testing-guide.md](resources/testing-guide.md) for TDD workflow

**This skill auto-activates when editing Java files in patra-* modules or when prompts mention hexagonal, DDD, orchestrator, domain, or related keywords.**
