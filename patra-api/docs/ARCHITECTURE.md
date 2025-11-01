# Architecture

## Overview

Papertrace is a **medical literature data platform** built with **Microservices**, **Hexagonal Architecture (Ports & Adapters)**, and **Domain-Driven Design (DDD)**. The system collects literature from 10+ external sources (PubMed, EPMC, Crossref, etc.), parses and standardizes the data, and provides a foundation for search and intelligent analysis.

---

## Architectural Principles

### 1. Hexagonal Architecture + DDD

All microservices follow a strict **four-layer architecture** from outer to inner:

```
┌─────────────────────────────────────────────────┐
│  Adapter (Inbound/Outbound)                     │  ← External interfaces
│  • REST Controllers / Feign Clients             │
│  • Job Schedulers / MQ Listeners                │
│  • Protocol translation & validation            │
├─────────────────────────────────────────────────┤
│  Application (Orchestration)                    │  ← Use case coordination
│  • *Orchestrator / *AppService classes          │
│  • Transaction boundaries                       │
│  • Cross-aggregate coordination                 │
├─────────────────────────────────────────────────┤
│  Domain (Pure Java - NO frameworks)             │  ← Business logic
│  • Aggregates, Entities, Value Objects          │
│  • Domain Events & Business Rules               │
│  • Port interfaces (no implementation)          │
├─────────────────────────────────────────────────┤
│  Infrastructure (Technical concerns)             │  ← Persistence & I/O
│  • *RepositoryImpl (MyBatis-Plus)               │
│  • External service adapters                    │
│  • Caching, messaging, file storage             │
└─────────────────────────────────────────────────┘
```

### 2. Dependency Direction Rules ⚠️ MUST FOLLOW

**Strict unidirectional dependencies** (inner layers have NO knowledge of outer layers):

```
adapter    →  app + api (+ web starters)
app        →  domain + patra-common + core starter
infra      →  domain + mybatis starter + core starter
domain     →  ONLY patra-common (NO Spring/framework deps)
api        →  NO framework dependencies (pure contracts)
```

**Critical constraints:**
- `domain` layer = **Pure Java** (no Spring, no Hibernate, no Jackson annotations)
- Business rules MUST live in domain, NOT in app/infra/adapter
- Port interfaces defined in domain, implemented in infra

### 3. Module Structure Pattern

Each microservice follows this structure:

```
patra-{service}/
├─ patra-{service}-boot/       # Executable (main class)
├─ patra-{service}-api/        # External contracts (Feign clients, DTOs)
├─ patra-{service}-domain/     # Pure Java domain model
├─ patra-{service}-app/        # Use case orchestration
├─ patra-{service}-infra/      # Repository implementations
└─ patra-{service}-adapter/    # Inbound/outbound adapters
```

### 4. Core Design Patterns

#### 4.1 CQRS (Command-Query Responsibility Segregation)
- **Write side**: Aggregates enforce invariants and emit domain events
- **Read side**: Query models (`*Query` objects) optimized for specific views
- Example: `ProvenanceConfiguration` (aggregate) vs `ProvenanceConfigQuery` (read model)

#### 4.2 Event-Driven Architecture
- **Domain Events**: Raised by aggregates when state changes (e.g., `TaskQueuedEvent`)
- **Outbox Pattern**: Transactional event publishing via `OutboxMessage` table
- **Async Communication**: Services communicate via events (reliability over latency)

#### 4.3 Temporal Configuration
- Configurations have **effective time ranges** (`effective_from`, `effective_until`)
- Queries specify `Instant at` to retrieve configuration valid at that moment
- Supports gradual rollout and A/B testing

#### 4.4 Idempotency
- **Plan-level**: `planKey` = hash(source + operation + window + strategy)
- **Task-level**: `idempotentKey` ensures duplicate tasks are detected
- Supports safe retries and compensation flows

---

## System Architecture

### Microservices Overview

```
┌──────────────────────────────────────────┐
│  patra-gateway-boot (Ingress Gateway)    │
│  → Routing, Auth, Rate Limiting          │
└────────┬─────────────────────────────────┘
         │
    ┌────┴──────────────────────┐
    │                            │
┌───▼─────────────┐      ┌──────▼──────────────┐
│ patra-registry  │      │  patra-ingest       │
│  (SSOT)         │◀─────│  (Collector)        │
└─────────────────┘      └──────┬──────────────┘
  • Provenance metadata          │
  • Configuration slicing        │
  • Expression metadata          │
  • Dictionary management        │
                                 ▼
                         External APIs
                      (PubMed, EPMC, etc.)
```

**Key Services**:
- **patra-gateway-boot**: Northbound/ingress gateway (external clients → microservices)
- **patra-registry**: SSOT for provenance configs, expressions, dictionaries
- **patra-ingest**: Plan orchestration, task generation, outbox relay
  

### Data Flow

```
1. Scheduler triggers ingestion
   ↓
2. patra-ingest: Create Plan → Slices → Tasks
   ↓
3. Query patra-registry for Provenance configs (via Feign)
   ↓
4. Generate Tasks (idempotent), persist to DB
   ↓
5. Publish TaskQueuedEvent to Outbox
   ↓
6. Outbox relay publishes to MQ
   ↓
7. Task workers consume from MQ
   ↓
8. Task workers call external provider APIs (via provenance starter HTTP clients)
   ↓
9. External APIs return data
   ↓
10. Task workers parse, cleanse, store data
   ↓
11. Update task status (SUCCEEDED/FAILED)
```

**Key Integration Points**:
- **patra-ingest → patra-registry**: Fetch config snapshots (Feign)
 

---

## Key Technical Decisions

### 1. Why Hexagonal Architecture?

**Problem**: Traditional layered architectures couple domain logic to frameworks (Spring, MyBatis), making code hard to test and migrate.

**Solution**: Hexagonal isolates domain as pure Java, with ports for external dependencies.

**Benefits**:
- Domain logic testable without Spring context
- Easy to swap persistence (MyBatis → JPA) or protocols (REST → gRPC)
- Clear separation of concerns

### 2. Why Event-Driven + Outbox?

**Problem**: Distributed systems need reliable event delivery, but directly publishing to MQ can fail (2PC not practical).

**Solution**: Outbox pattern — persist events in same transaction as domain changes, relay asynchronously.

**Benefits**:
- Guaranteed delivery (at-least-once)
- Decouples services (temporal independence)
- Supports eventual consistency

### 3. Why Temporal Configuration?

**Problem**: Changing API rate limits or retry policies mid-flight breaks running tasks.

**Solution**: Configurations have time slices; tasks capture config snapshot at plan creation time.

**Benefits**:
- Safe config updates without impacting active jobs
- Audit trail of config changes
- Supports gradual rollout

### 4. Why MyBatis-Plus over JPA/Hibernate?

**Problem**: Complex queries (multi-table joins, JSON fields, temporal slicing) are verbose in JPA.

**Solution**: MyBatis-Plus provides SQL control with annotation-based convenience.

**Trade-off**: More boilerplate (mappers, DO classes), but better performance and flexibility.

### 5. Why Centralized Feign Client Scanning?

**Problem**: Each service manually configuring `@EnableFeignClients` leads to:
- Scattered configuration across services
- Inconsistent scanning patterns
- Duplication of Feign client setup

**Solution**: Centralized scanning in `patra-spring-cloud-starter-feign` with convention-based discovery.

**Pattern**:
- Feign starter scans all `@FeignClient` interfaces under `com.patra` package
- Convention: Place RPC clients in `com.patra.{module}.api.rpc.client` packages
- Services automatically discover all Feign clients without manual configuration

**Benefits**:
- **DRY**: No repeated `@EnableFeignClients` annotations
- **Convention over Configuration**: Follow package structure → automatic discovery
- **Consistency**: All services use same pattern
- **Maintainability**: Add new Feign clients without updating service configurations

**Example**:
```java
// Client definition (in patra-registry-api)
package com.patra.registry.api.rpc.client;

@FeignClient(name = "patra-registry", contextId = "provenanceClient")
public interface ProvenanceClient extends ProvenanceEndpoint {}

// Usage (in any service - automatically injected)
@Component
@RequiredArgsConstructor
public class MyService {
    private final ProvenanceClient client;  // Auto-discovered, no config needed!
}
```

---

## Anti-Patterns to Avoid

### ❌ DON'T: Put business logic in Application layer
```java
// BAD: Business rule in Orchestrator
if (plan.getStatus() == PlanStatus.DRAFT) {
    plan.setStatus(PlanStatus.SLICING);  // Direct setter
}
```

### ✅ DO: Encapsulate in domain aggregate
```java
// GOOD: Business rule in aggregate
plan.startSlicing();  // Validates state machine, throws if invalid
```

### ❌ DON'T: Domain depends on framework
```java
// BAD: Domain using Spring annotations
@Component  // ❌
public class ProvenanceConfiguration { ... }
```

### ✅ DO: Keep domain pure Java
```java
// GOOD: Pure Java record
public record ProvenanceConfiguration(...) { }
```

### ❌ DON'T: Bypass repository ports
```java
// BAD: Direct MyBatis mapper call in app layer
@Autowired
private ProvenanceMapper mapper;  // ❌

public void someUseCase() {
    mapper.selectById(1L);  // Violates hexagonal boundary
}
```

### ✅ DO: Use domain ports
```java
// GOOD: Depend on port interface
private final ProvenanceConfigRepository repository;  // ✅

public void someUseCase() {
    repository.findProvenanceByCode(code);
}
```

---

## Module Dependency Graph

```
patra-gateway-boot (ingress)
    ↓
patra-registry (api, boot)
    ↓
patra-ingest (api, boot)
    ↓
patra-common
    ↓
patra-spring-boot-starter-* (core, web, mybatis, feign, provenance, expr)
patra-spring-cloud-starter-feign
    ↓
patra-expr-kernel
    ↓
patra-parent (root POM)
```

**Key points**:
- `patra-gateway-boot`: Ingress gateway (external → services)
 
- `patra-common`: Shared base classes (AggregateRoot, DomainEvent, ErrorCodes)
- `patra-spring-boot-starter-*`: Custom auto-configuration (Jackson, error handling, MyBatis)
- `patra-expr-kernel`: Expression engine (for dynamic API parameter mapping)
- `patra-parent`: Maven parent POM (dependency management)

---

## Technology Stack

| Layer           | Technology                        |
|-----------------|-----------------------------------|
| **Framework**   | Spring Boot 3.5.7, Spring Cloud 2025.0.0 |
| **Language**    | Java 25                           |
| **Persistence** | MyBatis-Plus, MySQL 8.x           |
| **Messaging**   | RocketMQ / Kafka (planned)        |
| **API**         | REST (Feign for RPC)              |
| **Gateway**     | Spring Cloud Gateway              |
| **Build**       | Maven 3.9+                        |
| **Utilities**   | Hutool, Lombok, MapStruct         |

---

## References

- [Domain-Driven Design (Eric Evans)](https://www.domainlanguage.com/)
- [Hexagonal Architecture (Alistair Cockburn)](https://alistair.cockburn.us/hexagonal-architecture/)
- [Outbox Pattern (Chris Richardson)](https://microservices.io/patterns/data/transactional-outbox.html)
- [CQRS Pattern](https://martinfowler.com/bliki/CQRS.html)

---

**Last Updated**: 2025-10-14
**Author**: Claude (via Papertrace documentation project)
