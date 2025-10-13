# Architecture & Design Principles

This file contains architectural patterns, design principles, and layer responsibilities for the Papertrace project.

---

## Hexagonal Architecture + DDD

**Four Layers** (outer → inner):

1. **Adapter** (Outermost): Controllers, Jobs, MQ Listeners → `app` + `api` + web starters
2. **Application**: `*Orchestrator` for use case coordination → `domain` + `patra-common` + core starter
   - **Critical**: Orchestrate only, NO business rules
3. **Domain** (Core): Aggregates, Entities, VOs, Events, Ports → **ONLY** `patra-common`
   - **Critical**: Pure Java, NO framework dependencies
4. **Infrastructure**: `*RepositoryImpl`, DB access, RPC → `domain` + mybatis starter
   - Tools: MyBatis-Plus + MapStruct

---

## Dependency Direction (Must Follow)

**Rules** (from outer to inner, NO reverse dependencies):

```
adapter  →  app + api (+ web starters)
app      →  domain + patra-common + core starter
infra    →  domain + mybatis starter + core starter
domain   →  ONLY patra-common (NO Spring/framework dependencies)
api      →  NO framework dependencies (external contracts)
```

**Violation of these rules is NOT acceptable!**

---

## Layer Responsibilities & Examples

**Domain** (Pure Java)
- Aggregates, Entities, VOs, Events, Port interfaces, business rules
- ✅ Pure Java classes | ❌ NO `@Entity`, `@Service`, `@Autowired`

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
