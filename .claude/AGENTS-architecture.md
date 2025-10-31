# Architecture & Design Principles

This file contains architectural patterns, design principles, and layer responsibilities for the Papertrace project.

---

## Hexagonal Architecture + DDD

**Four Layers** (outer → inner):

1. **Adapter** (Outermost): Controllers, Jobs, MQ Listeners → `app` + `api` + web starters
2. **Application**: `*Orchestrator` for use case coordination → `domain` + `patra-common` + core starter
   - **Critical**: Orchestrate only, NO business rules
3. **Domain** (Core): Aggregates, Entities, VOs, Events, Ports → `patra-common` + Lombok + Hutool
   - **Critical**: Pure Java with Lombok/Hutool support, NO Spring/framework dependencies
4. **Infrastructure**: `*RepositoryImpl`, DB access, RPC → `domain` + mybatis starter
   - Tools: MyBatis-Plus + MapStruct

---

## Dependency Direction (Must Follow)

**Rules** (from outer to inner, NO reverse dependencies):

```
adapter  →  app + api (+ web starters)
app      →  domain + patra-common + core starter
infra    →  domain + mybatis starter + core starter
domain   →  patra-common + Lombok + Hutool (NO Spring/framework dependencies)
api      →  NO framework dependencies (external contracts)
```

**Violation of these rules is NOT acceptable!**

---

## Layer Responsibilities & Examples

**Domain** (Pure Java with Lombok/Hutool)
- Aggregates, Entities, VOs, Events, Port interfaces, business rules
- ✅ Pure Java classes, Lombok annotations (@Slf4j, @Data, etc.), Hutool utilities
- ❌ NO Spring framework annotations (`@Entity`, `@Service`, `@Autowired`, etc.)

**Application** (`*Orchestrator`)
- Use case orchestration, transactions, cross-aggregate coordination
- ✅ Delegate to Domain | ❌ NO business rules here

**Infrastructure** (`*RepositoryImpl`)
- MyBatis-Plus repositories, MapStruct mappers, DO ↔ Domain mapping
- ✅ JsonNode for JSON | ❌ Never expose DOs outside

**Adapter** (Controllers/Jobs/Listeners)
- `@Valid` validation, ProblemDetail error mapping, trace propagation
- ✅ Delegate to orchestrators | ❌ NO direct infra calls

---

## Adapter Layer Organization

### Module-Level Separation (Driving vs Driven)

Papertrace uses **module-level boundaries** to separate driving from driven adapters:

```
patra-{service}-adapter/     ← Driving Adapters ONLY (receive external triggers)
├── REST APIs, Scheduled Jobs, Message Consumers
└── Direction: External World → System

patra-{service}-infra/       ← Driven Adapters ONLY (access external resources)
├── DB Repositories, External API Clients, MQ Publishers
└── Direction: System → External Resources
```

**Key Principle**: The module boundary provides clear separation between driving and driven adapters. No need for `inbound/outbound/` packages within the adapter module.

### Package Organization Standard

**Organize by adapter technology type** directly under `adapter/`:

```
com.patra.{service}.adapter/
├── rest/           REST APIs (Spring MVC, OpenAPI)
│   ├── internal/   (optional: microservice-to-microservice APIs)
│   └── public/     (optional: external-facing APIs)
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

1. **Module name provides context**: `patra-{service}-adapter` already indicates this is the adapter layer
2. **Module contract is explicit**: `-adapter` module contains ONLY driving adapters (by architectural design)
3. **Semantic redundancy**: `adapter.inbound.rest` repeats the "inbound" concept unnecessarily
4. **No future conflation**: ALL outbound integrations belong in `-infra` module, never in `-adapter`

### Examples of Correct Adapter Placement

**Driving Adapters** (belong in `-adapter` module):
- REST API receives HTTP request → `adapter/rest/ProvenanceController.java`
- XXL-Job triggers scheduled task → `adapter/scheduler/job/PubmedHarvestJob.java`
- RocketMQ consumer receives message → `adapter/stream/IngestStreamConsumers.java`

**Driven Adapters** (belong in `-infra` module):
- Call external API (PubMed) → `infra/integration/pubmed/PubmedSearchAdapter.java`
- Access database → `infra/repository/ProvenanceRepositoryImpl.java`
- Publish MQ message → `infra/messaging/RocketMqOutboxPublisher.java`
- Send webhook (future) → `infra/integration/webhook/WebhookSender.java`

### Naming Conventions

| Adapter Type | Naming Pattern | Example |
|--------------|----------------|---------|
| REST Controller | `*Controller` or `*EndpointImpl` | `ProvenanceController` |
| Scheduled Job | `*Job` | `PubmedHarvestJob` |
| Message Consumer | `*Consumers` | `IngestStreamConsumers` |
| Job Parameter | `*Param` or `*DTO` | `ProvenanceScheduleJobParam` |

### Documentation Requirements

Each adapter module MUST have:
1. **Module README.md**: Explicitly states "This module contains ONLY driving adapters"
2. **Package-level JavaDoc**: Each adapter type package (scheduler, rest, stream) has `package-info.java`

---

## Application Layer Organization Patterns

The Application layer can be organized using two complementary patterns, each suited to different scenarios.

### Pattern 1: Orchestrator + Coordinators

**Structure:**
```
MainOrchestrator (@Transactional)
  ├─ PersistenceCoordinator    (concern: data persistence)
  ├─ IdempotencyCoordinator    (concern: duplicate detection)
  └─ PublishingCoordinator     (concern: event publishing)
```

**Characteristics:**
- **Separation by Concern**: Split by business concerns (persistence, idempotency, publishing)
- **Unified Transaction**: Main Orchestrator holds `@Transactional`, all Coordinators inherit the same transaction boundary
- **Lightweight Delegation**: Coordinators have NO interfaces, serve only as helper classes
- **Linear Flow**: Suitable for sequential flows (prepare → validate → assemble → persist → publish)

**When to Use:**
- ✅ Entire flow within a single transaction
- ✅ No external API calls (or minimal calls)
- ✅ Concern separation more important than phase isolation
- ✅ Components have no reuse requirements

**Example:** `PlanIngestionOrchestrator`
```java
@Service
@RequiredArgsConstructor
public class PlanIngestionOrchestrator implements PlanIngestionUseCase {
  private final PlanPersistenceCoordinator persistenceCoordinator;
  private final PlanIdempotencyCoordinator idempotencyCoordinator;
  private final PlanPublishingCoordinator publishingCoordinator;

  @Override
  @Transactional  // Single transaction boundary for entire flow
  public PlanIngestionResult ingestPlan(PlanIngestionCommand request) {
    // Orchestrate 6 phases: prepare → validate → assemble → idempotency → persist → publish
  }
}
```

---

### Pattern 2: Main Orchestrator + Sub-UseCases

**Structure:**
```
MainOrchestrator (NO @Transactional)
  ├─ PrepareUseCase     (phase: preparation, isolated transaction if needed)
  ├─ ExecuteUseCase     (phase: execution, NO transaction - external API calls)
  └─ CompleteUseCase    (phase: completion, @Transactional for final updates)
```

**Characteristics:**
- **Phase Isolation**: Split by execution phases (prepare → execute → complete)
- **Independent Reuse**: Each Sub-UseCase has an interface, can be tested and reused independently
- **Distributed Transactions**: Each Sub-UseCase may have its own transaction boundary (as needed)
- **Error Isolation**: Each phase handles exceptions independently (e.g., prepare failure returns immediately without affecting other phases)

**When to Use:**
- ✅ Multi-phase transactions (only some phases need transactions)
- ✅ Contains external API calls (should NOT be in transactions)
- ✅ Clear lifecycle and state transitions across phases
- ✅ Sub-UseCases may be reused by other flows

**Example:** `TaskExecutionUseCaseImpl`
```java
@Service
@RequiredArgsConstructor
public class TaskExecutionUseCaseImpl implements TaskExecutionUseCase {
  private final PrepareTaskExecutionUseCase prepareUseCase;      // NO @Transactional - fast checks
  private final ExecuteTaskBatchesUseCase executeUseCase;        // NO @Transactional - external API calls
  private final CompleteTaskExecutionUseCase completeUseCase;    // @Transactional - final updates

  @Override
  public void execute(TaskReadyCommand command) {
    // Phase 0: Prepare (may fail fast due to lease acquisition failure)
    // Phase 1: Execute (calls external APIs like PubMed/EPMC)
    // Phase 2: Complete (updates cursor and status atomically)
  }
}
```

---

### Naming Conventions

| Pattern | Naming | Responsibility | Interface | Transaction |
|---------|--------|---------------|-----------|-------------|
| **Sub-UseCase** | `*UseCase` / `*UseCaseImpl` | Complete business phase, can execute independently | ✅ Has | ✅ Independent |
| **Coordinator** | `*Coordinator` | Assists main Orchestrator with concern separation | ❌ None | ❌ Inherits |
| **Orchestrator** | `*Orchestrator` | Main flow orchestration, holds transaction boundary | ✅ Has (usually) | ✅ Controls |
| **Service** | `*Service` | Independent technical service (e.g., LeaseManagementService) | ✅ Has (optional) | ⚠️ Context-dependent |

---

### Golden Rules for Transaction Management

**⚠️ Critical Principles:**

1. **NEVER call external APIs within `@Transactional` methods**
   - External API calls can take 10+ seconds, holding DB connections and locks
   - Use Pattern 2 (Sub-UseCases) to isolate external calls from transactions

2. **Minimize transaction scope**
   - Only include operations that MUST be atomic
   - Keep transactions short-lived to reduce lock contention

3. **Example of WRONG approach:**
   ```java
   @Transactional  // ❌ BAD: Long transaction holding DB locks
   public void execute() {
     prepare();           // Quick DB check
     callPubMedAPI();    // ⚠️ 10+ seconds, blocks transaction!
     saveResults();      // DB update
   }
   ```

4. **Example of CORRECT approach:**
   ```java
   public void execute() {
     prepare();              // NO transaction - quick checks
     callPubMedAPI();       // NO transaction - external API
     complete();            // @Transactional - only final DB updates
   }
   ```

---

### Pattern Comparison

| Dimension | Orchestrator + Coordinators | Main + Sub-UseCases |
|-----------|----------------------------|---------------------|
| **Split Strategy** | By Concern | By Phase |
| **Component Interface** | None (internal helpers) | Yes (independent UseCases) |
| **Transaction Boundary** | Unified (main Orchestrator) | Distributed (each Sub-UseCase) |
| **Component Size** | Small (100-200 lines) | Large (200-300 lines) |
| **Reusability** | Low (tightly coupled) | High (interface isolation) |
| **Testing Granularity** | Requires full orchestration | Can test each phase independently |
| **Use Case** | Single transaction, complex internal flow | Multi-phase, needs independent transactions |

---

### Decision Tree

```
Does the flow contain external API calls?
  ├─ YES → Use Pattern 2 (Main + Sub-UseCases)
  │         Reason: Avoid long transactions
  └─ NO  → Does the flow need multiple independent transactions?
            ├─ YES → Use Pattern 2 (Main + Sub-UseCases)
            │         Reason: Phase isolation with independent transaction boundaries
            └─ NO  → Use Pattern 1 (Orchestrator + Coordinators)
                      Reason: Simpler, unified transaction boundary
```

---

## Design Patterns Reference

### DDD Patterns (Domain-Driven Design)
- **Aggregate**: Consistency boundary with root entity, enforces invariants
- **Entity**: Identity-based objects (ID-driven equality)
- **Value Object**: Immutable, equality by value (use `record`)
- **Domain Event**: Captures business facts, triggers cross-aggregate reactions
- **Repository**: Collection-like interface for aggregate persistence
- **Factory**: Complex aggregate creation logic
- **Specification**: Reusable business rule predicates
- **Domain Service**: Stateless operation spanning multiple aggregates

### GoF Patterns (Gang of Four - Common in this codebase)
- **Strategy**: Multiple algorithm implementations (e.g., different parsers per source)
- **Factory**: Object creation (e.g., Provenance creation)
- **Template Method**: Algorithm skeleton with hook points
- **Observer**: Event-driven reactions (domain events)
- **Decorator**: Add behavior dynamically
- **Adapter**: Convert interfaces (ACL between contexts)
- **Facade**: Simplified interface to complex subsystem

### Enterprise Patterns (Fowler)
- **Service Layer**: Application service orchestrators
- **Repository**: Data access abstraction (port/adapter)
- **Data Mapper**: DTO/DO ↔ Domain mapping (MapStruct)
- **Unit of Work**: Transaction boundary management
- **Lazy Load**: Defer loading until needed
- **Identity Map**: Cache to ensure single instance per ID

### Integration Patterns
- **Outbox Pattern**: Reliable event publishing with DB transaction
- **Idempotency Key**: Prevent duplicate processing
- **Circuit Breaker**: Fail fast, auto-recovery (Sentinel/Resilience4j)
- **Retry with Backoff**: Transient failure recovery
- **Anti-Corruption Layer (ACL)**: Protect domain from external models

### Data Patterns
- **Aggregate Persistence**: Save entire aggregate atomically
- **Optimistic Locking**: Version-based concurrency control
- **Eventual Consistency**: Async cross-aggregate updates via events
- **Cache-Aside**: Lazy load cache, app manages invalidation
- **CQRS**: Separate read/write models (when complexity justifies)

---

## Design Principles & Philosophy

### Core Principles
- **Self-contained use cases**: Each use case dir has command/dto/logic (see `patra-ingest/app/plan`)
- **Naming conventions**: `*Orchestrator`, `*Command`, `*Impl`, `*Port`, `*DO`
- **Contract-first**: Define `*-api` contracts → implement Domain → App → Infra → Adapter
- **Simplicity first**: Solve current problem, avoid over-engineering
- **YAGNI**: You Aren't Gonna Need It - don't build for hypothetical futures
- **Fail fast**: Validate early, make errors obvious

### Pattern Selection Guidelines
- **Start simple**: Use simplest pattern that solves the problem
- **Refactor when needed**: Abstract after 3rd duplication (Rule of Three)
- **Match context**: Choose patterns that fit team skills and project constraints
- **Consider trade-offs**: Every pattern has complexity cost vs. flexibility benefit
