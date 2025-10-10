# Papertrace Agent Handbook

> Agent handbook for Papertrace (Medical Literature Data Platform).

---

## 0. Quick Reference

### Your Role

**Java Developer for Papertrace-api**
Implement code across Domain/App/Infra/Adapter layers, follow **Hexagonal Architecture + DDD**, deliver high-quality compilable code.

### Core Principles

**✅ Do**
- Strictly adhere to **dependency directions** and **layer boundaries** (see Section 2)
- **Ask before acting**: Ask when information is insufficient; prioritize reusing `patra-*` starters, `patra-common`, Hutool
- Output **small changes/small diffs**; document assumptions and trade-offs for key decisions

**❌ Don't**
- `domain` layer must NOT introduce any framework dependencies (Pure Java)
- Do NOT hardcode secrets/connection strings/variable configurations (use Nacos/environment variables)

---

## 1. Project Overview

**Name**: Papertrace – Medical Literature Data Platform

**Goals**:
1. Collect 10+ medical literature sources (PubMed, EPMC…)
2. Use SSOT (`patra-registry`) to manage Provenance configurations/dictionaries/metadata
3. Parse, cleanse, and standardize raw literature data
4. Provide search and intelligent analysis in the future

**Architecture**: Microservices + Hexagonal Architecture + Event-Driven (async communication)
**Current Focus**: Ensure reliable data landing (Collection → Parsing/Cleansing → Storage)

---

## 2. Architecture & Design Principles

### 2.1 Hexagonal Architecture + DDD Core

**Four-Layer Architecture** (from outer to inner):

**Layer 1: Adapter (Inbound/Outbound)**
- Inbound: REST Controllers, Job Schedulers, MQ Listeners
- Outbound: Feign Clients, External API calls
- Purpose: Handle external communication and protocol translation

**Layer 2: Application (Orchestrator)**
- Use case orchestration and coordination
- Transaction boundary management
- Cross-aggregate coordination

**Layer 3: Domain (Pure Java)**
- Core business logic: Aggregates, Entities, Value Objects
- Domain Events and business rules
- Port interfaces (no implementation details)

**Layer 4: Infrastructure**
- Repository implementations
- Database access (MyBatis-Plus)
- External service adapters
- Technical concerns (caching, messaging, etc.)

### 2.2 Dependency Direction (Must Follow)

**Rules** (from outer to inner, NO reverse dependencies):
- `adapter` → `app` + `api` (+ web starters)
- `app` → `domain` + `patra-common` + core starter
- `infra` → `domain` + mybatis starter + core starter
- `domain` → **ONLY** `patra-common` (NO Spring/framework dependencies)
- `api` → NO framework dependencies (external contracts)

### 2.3 Layer Responsibilities

**Domain Layer**
- Responsibilities: Aggregates/Entities/Value Objects/Domain Events; Define Port interfaces
- Key Requirements: Pure Java, NO framework dependencies; Business rules cohesive

**Application Layer**
- Responsibilities: `*Orchestrator` use case orchestration; Transaction boundaries
- Key Requirements: Orchestrate ONLY, do NOT carry business rules

**Infrastructure Layer**
- Responsibilities: `*RepositoryImpl`; DO ↔ Domain mapping; RPC calls
- Key Requirements: MyBatis-Plus + MapStruct; JsonNode for JSON fields

**Adapter Layer**
- Responsibilities: Controller/Job/Listener; Input validation; Error mapping
- Key Requirements: `@Valid` + ProblemDetail; Trace propagation

### 2.4 Design Principles

- **Self-contained**: Each use case directory contains complete command/dto/core logic/supporting components (refer to `patra-ingest/app/plan`)
- **Unified Naming**: `*Orchestrator` (orchestrator), `*Command` (command), `*Impl` (implementation)

---

## 3. Tech Stack & Codebase Structure

### 3.1 Tech Stack Versions

**Language/Build**
- Java 21
- Maven (multi-module)
- UTF-8

**Core Frameworks**
- Spring Boot 3.2.4
- Spring Cloud 2023.0.1
- Spring Cloud Alibaba 2023.0.1.0

**Data Persistence**
- MyBatis-Plus 3.5.12
- MySQL 8.0
- Redis 7.0
- Elasticsearch 8.14

**Infrastructure**
- Nacos (registry/config)
- SkyWalking 10.2 (APM)
- XXL-Job 3.2.0 (scheduling)
- Docker Compose (local)

**Tools/Mapping**
- Lombok 1.18.38
- MapStruct 1.6.3
- Hutool 5.8.22

**Custom Starters**
- patra-spring-boot-starter-core
- patra-spring-boot-starter-web
- patra-spring-boot-starter-mybatis
- patra-spring-cloud-starter-feign

**Expression Engine**
- patra-expr-kernel

### 3.2 Codebase Structure

```
Papertrace/
├─ patra-parent/                    # Parent POM (dependency/plugin management)
├─ patra-common/                    # Common utilities & base classes
├─ patra-expr-kernel/               # Expression engine
├─ patra-gateway-boot/              # API Gateway
├─ patra-registry/                  # SSOT registry microservice
├─ patra-ingest/                    # Collection/ingestion microservice
├─ patra-spring-boot-starter-*/     # Custom starters
└─ docker/                          # Local infrastructure
```

### 3.3 Microservice Module Common Sub-structure

```
patra-{service}/
├─ patra-{service}-boot/       # Executable entry point
├─ patra-{service}-api/        # External API contract (DTOs/interfaces)
├─ patra-{service}-domain/     # Domain (entities/aggregates/enums/ports)
├─ patra-{service}-app/        # Application (use case orchestration)
├─ patra-{service}-infra/      # Infrastructure (repositories)
└─ patra-{service}-adapter/    # Adapter layer (REST/scheduling/MQ)
```

---

## 4. Development Guide

### 4.1 Code Conventions

#### POJO Forms
- **Immutable/Value Objects**: Prefer `record`
- **Mutability needed**: Use Lombok + `class` (don't use Lombok inside records)

#### Lombok Usage
- Don't write boilerplate code (getter/setter/toString/equals/hashCode) manually; use `@Data` or combined annotations

#### JSON Field Handling
- Database JSON fields should use Jackson `JsonNode` or define POJOs in DOs, **NOT** Map or String

#### Utility Reuse
- **Don't reinvent the wheel**: Prioritize using utilities from Hutool and `patra-common`/starters; search before adding new ones

### 4.2 Infrastructure Usage

#### Nacos (Registry/Config)
- Centralized configuration management; **DO NOT** hardcode sensitive information in code

#### XXL-Job (Scheduling)
- Jobs in `adapter/scheduler`; pay attention to idempotency, retry, rate limiting

#### SkyWalking (APM/Tracing)
- Pass trace/correlation ID in logs

### 4.3 Error Handling & Logging

- **Logging**: `@Slf4j` English parameterized logging; don't log sensitive information
- **Business Identifiers**: Key business identifiers (planId/sourceId/batchId) traced throughout
- **Exception Layering**: domain exceptions → application exceptions → HTTP exception mapping (ProblemDetail)

### 4.4 Performance & Consistency

- **Pagination/Batch Processing**: Avoid N+1; align indexes
- **Caching**: Use as designed; connection pool parameters (Hikari)
- **Consistency**: Integrate Outbox and eventual consistency strategies per convention (don't add new architecture)
- **Idempotency**: Idempotency keys/deduplication strategies/reentrant processes

### 4.5 Standard Development Process

**7-Step Process**:

1. **Confirm inputs**: Target module/package, contracts/ports/DTOs/use case signatures
2. **Define/improve Domain**: Pure Java (no framework dependencies)
3. **Implement App orchestration**: Transaction boundaries (don't carry business rules)
4. **Implement Infra**: MyBatis-Plus + MapStruct; JsonNode handling
5. **Implement Adapter**: Validation (`@Valid`)/error mapping/trace propagation
6. **Self-check**: `mvn -q -DskipTests compile`; necessary English comments
7. **Handoff**: Submit minimal Diff for review

### 4.6 Layer Implementation Details

#### Domain Layer
- Port interfaces clearly defined, avoid leaking implementation details upward
- Encapsulate domain logic, keep business rules cohesive

#### Application Layer
- `*Orchestrator` and `*Command` implementation: **orchestrate only**
- Transaction boundary declaration; exception translation; consistency semantics
- Cross-aggregate coordination; invoke infrastructure through ports

#### Infrastructure Layer
- Repository implementation (`*RepositoryImpl`); proper use of LambdaQuery/UpdateWrapper
- DO ↔ Domain/DTO mapping (MapStruct)
- Pagination/batch processing/bulk writes; avoid N+1; align indexes
- RPC adapter implementation (Feign Client invocation and error handling)

#### Adapter Layer
- Controller/Job/Listener: input validation (`@Valid`) and error mapping (ProblemDetail)
- Trace propagation (trace/correlation ID); CORS/Content-Type/Charset configuration alignment
- DTO conversion and boundary protection
