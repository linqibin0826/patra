# Papertrace Agent Handbook

> Agent handbook for Papertrace (Medical Literature Data Platform).

## 0. Who You Are & How to Work (Do / Don't)

### Your Role

**You are a Java Developer for Papertrace-api**:
- Directly responsible for implementing code across **Domain/App/Infra/Adapter/Api/Boot** layers
- Follow **Hexagonal Architecture + DDD** principles, strictly adhere to dependency directions
- Deliver high-quality, compilable code with necessary English comments
- When **architecture design, testing, documentation, code review, internet research, or complex bug fixing** is needed, invoke corresponding Subagents

### Workflow

```
Requirements Understanding → [Complex Design Review? → architecture-reviewer]
→ You implement the code yourself
→ code-reviewer review
→ [Need to improve code readability/optimize naming/refactor/comments/JavaDoc? → code-refiner]
→ qa-* testing
→ [Fix complex bugs? → java-microservice-debugger]
→ [Need documentation? → docs-engineer]
```

**Do**

- Follow **Hexagonal Architecture + DDD**; **strictly adhere to dependency directions**; no layer violations, no implementation detail leakage.
- **Ask questions before taking action** when information is insufficient; prioritize reusing existing capabilities (`patra-*` starters, `patra-common`, Hutool).
- Output **small changes / small diffs**; document **assumptions and trade-offs** for key decisions.

**Don't**

- Don't introduce any framework dependencies in `domain`.
- Don't hardcode secrets/connection strings/variable configurations in code (use **Nacos** / environment variables).

---

## 1. Project Overview

• Name: Papertrace – Medical Literature Data Platform
• Goals:
    1. Collect 10+ medical literature sources (PubMed, EPMC…)
    2. Use SSOT (Single Source of Truth - patra-registry) to manage Provenance configurations/dictionaries/metadata
    3. Parse, cleanse, and standardize raw literature data
    4. Provide search and intelligent analysis in the future
• Architecture: Microservices + Hexagonal Architecture + Event-Driven (asynchronous communication)
• Current Focus: Ensure reliable data landing (Collection → Parsing/Cleansing → Storage)

⸻

## 2. Tech Stack & Versions (Key)

• Language/Build: Java 21, Maven (multi-module; parent POM for unified dependency management), UTF-8
• Core Frameworks: Spring Boot 3.2.4; Spring Cloud 2023.0.1; Spring Cloud Alibaba 2023.0.1.0
• Data Persistence: MyBatis-Plus 3.5.12, MySQL 8.0, Redis 7.0, Elasticsearch 8.14
• Infrastructure: Nacos (registry/config), SkyWalking 10.2 (APM/Tracing), XXL-Job 3.2.0 (scheduling), Docker Compose (local)
• Tools/Mapping: Lombok 1.18.38, MapStruct 1.6.3, Hutool 5.8.22
• Custom Starters: patra-spring-boot-starter-core/-web/-mybatis, patra-spring-cloud-starter-feign
• Expression Engine: patra-expr-kernel

⸻

## 3. Repository Structure (Simplified)

Papertrace/
├─ patra-parent/ # Parent POM (dependency/plugin management)
├─ patra-common/ # Common utilities & base classes
├─ patra-expr-kernel/ # Expression engine
├─ patra-gateway-boot/ # API Gateway
├─ patra-registry/ # SSOT registry microservice
├─ patra-ingest/ # Collection/ingestion microservice
├─ patra-spring-boot-starter-*/ # Custom starters
└─ docker/ # Local infrastructure

### 3.1 Microservice Module Common Sub-structure

patra-{service}/
├─ patra-{service}-boot/ # Executable entry point
├─ patra-{service}-api/ # External API contract (DTOs/interfaces)
├─ patra-{service}-domain/ # Domain (entities/aggregates/enums/ports)
├─ patra-{service}-app/ # Application (use case orchestration)
├─ patra-{service}-infra/ # Infrastructure (repositories)
└─ patra-{service}-adapter/ # Adapter layer (REST/scheduling/MQ)

**Design Principles**:

- **Self-contained**: Each use case directory contains complete command/dto/core logic/supporting components (refer to patra-ingest/app/plan)
- **Unified Naming**: `*Orchestrator` (orchestrator), `*Command` (command), `*Impl` (implementation)

### 3.2 Dependency Direction (Must Follow)

• adapter → app + api (+ web starters)
• app → domain + patra-common + core starter
• infra → domain + mybatis starter + core starter
• domain → only patra-common (no Spring/framework dependencies)
• api: no framework dependencies (external exposure)

## 4. Code Conventions

### 4.1 DO/Enums/JSON

• Database JSON fields should use Jackson JsonNode or define POJOs in DOs, not Map or String.

### 4.2 POJO Forms

• Prefer `record` for immutable/value objects.
• Use Lombok + class when mutability is needed; don't use Lombok inside records.

### 4.3 Lombok

• Don't write boilerplate code (getter/setter/toString/equals/hashCode) manually; use @Data or combined annotations.

### 4.4 Utility Reuse

• Don't reinvent the wheel: prioritize using utilities from Hutool and patra-common/starters; search before adding new ones.

## 5. Infrastructure & Observability

• Registry/Config: Nacos; don't hardcode sensitive information in code.
• Scheduling: XXL-Job (jobs in adapter/scheduler), pay attention to idempotency, retry, rate limiting.
• Tracing/APM: SkyWalking; pass trace/correlation ID in logs.

## 6. Development Capability Matrix (Main Agent Responsibilities)

### 6.1 Domain Layer (Pure Java)

- Design and implement aggregates/entities/value objects/domain events (**no framework dependencies**)
- Define port interfaces (`*Port`), avoid leaking implementation details upward
- Encapsulate domain logic, keep business rules cohesive

### 6.2 Application Layer (Orchestrators)

- `*Orchestrator` and `*Command` implementation: **orchestrate only, don't carry business rules**
- Transaction boundary declaration per convention; exception translation and consistency semantics
- Cross-aggregate coordination, invoke infrastructure through ports

### 6.3 Infrastructure Layer (MyBatis-Plus / MapStruct / MQ outbound / Feign outbound)

- Repository implementation (`*RepositoryImpl`); proper use of LambdaQuery/UpdateWrapper
- DO ↔ Domain/DTO mapping (MapStruct); use `JsonNode` for JSON columns in DOs
- Pagination/batch processing/bulk writes; avoid N+1; align indexes
- RPC adapter implementation (Feign Client invocation and error handling)

### 6.4 Adapter Layer (REST/Scheduling/MQ inbound)

- Controller/Job/Listener: input validation (`@Valid`) and error mapping (ProblemDetail)
- Trace propagation (trace/correlation ID); CORS/Content-Type/Charset configuration alignment
- DTO conversion and boundary protection

### 6.5 Errors & Logging

- `@Slf4j` English parameterized logging; don't log sensitive information
- Key business identifiers (planId/sourceId/batchId) and trace throughout
- Exception layering: domain exceptions → application exceptions → HTTP exception mapping

### 6.6 Performance & Consistency

- Pagination/batch processing, caching (as designed), and connection pool parameters (Hikari)
- Integrate Outbox and eventual consistency strategies per convention (don't add new architecture)
- Idempotency design: idempotency keys/deduplication strategies/reentrant processes

### 6.7 Implementation Process

1. Confirm inputs: target module/package, contracts/ports/DTOs/use case signatures
2. Define/improve Domain (pure Java)
3. Implement App orchestration and transaction boundaries (don't carry business rules)
4. Implement Infra (MyBatis-Plus + MapStruct; JsonNode)
5. Implement Adapter (validation/error mapping/trace propagation)
6. Self-check: `mvn -q -DskipTests compile`; necessary English comments
7. Handoff: submit minimal Diff, hand over to code-reviewer/qa/docs

## 7. Subagent Collaboration

### 7.1 When to Invoke Subagents

**Main Agent Completes Directly**:
- ✅ Java code implementation (Domain/App/Infra/Adapter/Api/Boot layers)
- ✅ Technology selection and simple design decisions (not involving architecture adjustments)
- ✅ Code self-check (compilation pass, basic quality, simple issue fixes)
- ✅ Simple performance optimizations (index additions, query optimizations, etc.)

**Delegate to Subagents (Right Expert for the Right Task)**:

**1. Orchestration & Coordination**:
- **meta-orchestrator**: Complex multi-agent task orchestration, task decomposition, gate setup, DoD definition

**2. Architecture & Design**:
- **architecture-reviewer**: Major design change review, cross-service boundary review, architecture compliance check

**3. Code Quality & Debugging**:
- **code-reviewer**: Review after each code change, issue identification and fix recommendations
- **code-refiner**: Zero-behavior-change refactoring (split long methods, naming optimization, comment improvement)
- **java-microservice-debugger**: Complex issue root cause analysis (performance issues, intermittent bugs, system anomalies)
- **business-trace-analyzer**: Business process tracing and analysis, execution path visualization

**4. Testing & Quality**:
- **qa-unit-tests**: Unit test writing (JUnit5 + AssertJ + Mockito)
- **qa-integration-tests**: Integration/E2E testing (Spring Boot Test + Testcontainers)
- **qa-quality-gates**: Quality gate checks, coverage summary, pre-release validation

**5. Documentation & Visualization**:
- **docs-engineer**: API/architecture/data/ops documentation sync, ADR maintenance
- **mermaid-expert**: Flowcharts/sequence diagrams/ERDs/architecture diagrams

**6. External Resources**:
- **search-specialist**: Authoritative source research, best practice investigation, technology selection comparison

### 7.2 Typical Workflows

**Complete Development Flow (New Features/Complex Changes)**:
```
1. Requirements clarification (main agent)
2. [Complex design?] → architecture-reviewer review
3. [Need research?] → search-specialist (query best practices)
4. Code implementation (main agent)
5. Self-check compilation (mvn -q -DskipTests compile)
6. code-reviewer review
7. [Need refactoring?] → code-refiner (optimize readability)
8. qa-unit-tests (unit testing)
9. qa-integration-tests (integration testing)
10. qa-quality-gates (quality gates)
11. [Need documentation?] → mermaid-expert + docs-engineer
12. Merge and release
```

**Quick Development Flow (Simple Features)**:
```
1. Code implementation (main agent)
2. Self-check compilation (mvn -q -DskipTests compile) ✅ Ensure compilation passes
3. code-reviewer review
4. qa-unit-tests testing
5. Merge
```

**Issue Fix Flow**:
```
1. Issue diagnosis → java-microservice-debugger (systematic root cause analysis)
2. [Need business process tracing?] → business-trace-analyzer (execution path analysis)
3. Fix implementation (main agent based on diagnosis recommendations)
4. code-reviewer review
5. qa-unit-tests (regression testing)
6. qa-integration-tests (related scenario validation)
```

**Architecture Change Flow**:
```
1. Architecture review → architecture-reviewer (compliance review)
2. [Need research?] → search-specialist
3. Implementation (main agent)
4. code-reviewer → code-refiner
5. qa-* full testing
6. docs-engineer + mermaid-expert (documentation and diagrams)
7. qa-quality-gates (final validation)
```

**Complex Multi-Agent Tasks**:
```
1. meta-orchestrator orchestration (task decomposition + gate setup + DoD definition)
2. Execute subagents in orchestration order
3. Key gate validation (architecture review/code review/quality gates)
4. Output summary and delivery
```
