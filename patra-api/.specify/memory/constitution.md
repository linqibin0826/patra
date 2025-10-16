<!--
Sync Impact Report
==================
Version change: (initial) → 1.0.0
Modified principles: N/A (initial creation)
Added sections:
  - All core principles (I-VII)
  - Architecture Constraints section
  - Development Workflow section
  - Governance section
Removed sections: N/A
Templates requiring updates:
  ✅ plan-template.md - validated (Constitution Check section aligns)
  ✅ spec-template.md - validated (requirements structure aligns)
  ✅ tasks-template.md - validated (phase organization aligns)
Follow-up TODOs: None
-->

# Papertrace Constitution

## Core Principles

### I. Hexagonal Architecture + DDD (NON-NEGOTIABLE)

All microservices MUST follow strict four-layer architecture from outer to inner:

1. **Adapter Layer** (outermost): Controllers, Jobs, MQ Listeners, Feign client implementations
2. **Application Layer**: `*Orchestrator` classes for use case coordination only
3. **Domain Layer** (core): Aggregates, Entities, Value Objects, Events, Port interfaces
4. **Infrastructure Layer**: `*RepositoryImpl`, MyBatis-Plus mappers, external service adapters

**Rationale**: Isolating domain logic as pure Java ensures testability without framework dependencies, enables easy technology migration, and enforces clear separation of concerns. This is the architectural foundation that ALL code MUST respect.

### II. Dependency Direction Rules (NON-NEGOTIABLE)

Dependencies MUST flow unidirectionally from outer to inner layers. Inner layers MUST have NO knowledge of outer layers:

- `adapter` → `app` + `api` (+ web starters)
- `app` → `domain` + `patra-common` + core starter
- `infra` → `domain` + mybatis starter + core starter
- `domain` → ONLY `patra-common` + SLF4J API (slf4j-api) (NO Spring/framework implementation dependencies)
- `api` → NO framework dependencies (pure contracts)

**SLF4J Exception**: The SLF4J API (slf4j-api) is permitted in the domain layer as it provides an interface-only logging facade with no implementation dependencies. This is the ONLY logging dependency allowed in domain. Use plain `Logger` declarations (no Lombok @Slf4j annotation in domain).

**Critical constraints**:
- Domain layer = Pure Java (no `@Entity`, `@Service`, `@Autowired`, `@Component`)
- Business rules MUST live in domain aggregates, NOT in app/infra/adapter
- Port interfaces defined in domain, implemented in infra

**Rationale**: Dependency inversion ensures domain logic remains independent of technical concerns. Violating this rule creates tight coupling that makes the system unmaintainable and untestable. This rule is enforced through ArchUnit tests.

### III. Event-Driven Architecture with Outbox Pattern

All asynchronous communication between services MUST use the Outbox pattern:

1. Domain aggregates raise events via `addDomainEvent()`
2. Events persisted to `outbox_message` table in same transaction as domain changes
3. Outbox relay publishes events to MQ asynchronously
4. Consuming services process events idempotently

**Rationale**: Guarantees at-least-once event delivery without distributed transactions, enables temporal independence between services, and supports eventual consistency. Direct MQ publishing can fail after DB commit, causing data inconsistency.

### IV. Idempotency & Safe Retries

All operations that can be retried MUST be idempotent:

- **Plan-level**: `planKey` = hash(source + operation + window + strategy)
- **Task-level**: `idempotentKey` ensures duplicate tasks are detected
- **API operations**: Use idempotency keys for write operations
- **Event handlers**: Track processed events to prevent duplicate processing

**Rationale**: Distributed systems experience transient failures. Idempotency enables safe automatic retries without data corruption or duplicate work. Without idempotency, retries can cause double-charging, duplicate records, or inconsistent state.

### V. Temporal Configuration with Effective Time Ranges

All operational configurations MUST have temporal slices:

- Configurations have `effective_from` and `effective_until` timestamps
- Queries specify `Instant at` to retrieve configuration valid at that moment
- Tasks capture configuration snapshot at plan creation time
- Configuration changes do NOT affect in-flight operations

**Rationale**: Changing API rate limits or retry policies mid-flight breaks running tasks. Temporal configuration provides audit trail, enables safe updates without impacting active jobs, and supports gradual rollout and A/B testing.

### VI. Test-First Development

Testing strategy follows this hierarchy:

1. **Domain layer tests** (mandatory): Pure Java unit tests, no mocks needed
   - Test aggregate behavior, business rules, value object validation
   - Example: `ProvenanceTest`, `BatchPlanTest`

2. **Application layer tests** (mandatory): Test orchestration logic
   - Mock domain and infrastructure ports
   - Verify transaction boundaries and error translation

3. **Integration tests** (boot module only): Use TestContainers for DB/Redis/MQ
   - Test repository implementations and DO ↔ Domain mapping
   - Verify `@Valid` constraints and error mapping
   - Use MockMvc or RestAssured for API tests

4. **ArchUnit tests** (recommended): Enforce layer dependency rules
   - Verify no framework dependencies in domain layer
   - Check naming conventions

**Rationale**: Testing at each layer with appropriate tools ensures reliability. Domain tests are fast and independent. Integration tests verify infrastructure adapters work correctly. ArchUnit prevents architectural violations.

### VII. Simplicity & YAGNI (You Aren't Gonna Need It)

Code and design decisions MUST prioritize simplicity:

- Solve current problem, avoid over-engineering
- Use simplest pattern that solves the problem
- Refactor when needed: abstract after 3rd duplication (Rule of Three)
- Choose patterns that fit team skills and project constraints
- Every pattern has complexity cost vs. flexibility benefit - evaluate trade-offs

**Examples**:
- ✅ DO: Inline logic for one-off operations
- ❌ DON'T: Create abstraction layers for hypothetical future requirements
- ✅ DO: Use direct repository calls when single data source
- ❌ DON'T: Add CQRS when read/write models are identical

**Rationale**: Premature abstraction creates unnecessary complexity that slows development and obscures intent. Build for today's requirements; refactor when actual patterns emerge.

## Architecture Constraints

### Module Structure

Each microservice MUST follow this structure:

```
patra-{service}/
├── patra-{service}-boot/       # Executable (main class + integration tests)
├── patra-{service}-api/        # External contracts (Feign clients, DTOs)
├── patra-{service}-domain/     # Pure Java domain model + unit tests
├── patra-{service}-app/        # Use case orchestration + unit tests
├── patra-{service}-infra/      # Repository implementations
└── patra-{service}-adapter/    # Inbound/outbound adapters
```

**Rationale**: Consistent structure across all services reduces cognitive load and enables developers to navigate any service quickly.

### Naming Conventions

Code MUST follow these naming conventions:

- Aggregates: `*Aggregate` (e.g., `PlanAggregate`, `TaskAggregate`)
- Orchestrators: `*Orchestrator` (e.g., `CreatePlanOrchestrator`)
- Repository implementations: `*RepositoryMpImpl` (e.g., `PlanRepositoryMpImpl`)
- Repository ports: `*Repository` (e.g., `PlanRepository`)
- Domain events: `*Event` (e.g., `TaskQueuedEvent`, `PlanCompletedEvent`)
- Data objects: `*DO` (e.g., `PlanDO`, `TaskDO`)
- Commands: `*Command` (e.g., `CreatePlanCommand`)
- Results: `*Result` (e.g., `CreatePlanResult`)

**Rationale**: Consistent naming enables instant recognition of component roles and layer boundaries.

### Technology Stack Standards

Core technologies are standardized across all services:

- **Language**: Java 21 with modern features (`record`, pattern matching, sealed classes)
- **Framework**: Spring Boot 3.2.4 + Spring Cloud 2023.0.1
- **Persistence**: MyBatis-Plus + MySQL 8.x
- **Build**: Maven 3.9+
- **Messaging**: RocketMQ (Kafka planned)
- **API**: REST with Feign for inter-service RPC
- **Utilities**: Hutool, Lombok, MapStruct
- **Config**: Nacos for centralized configuration

**Rationale**: Technology standardization reduces operational complexity, enables code reuse through starters, and allows developers to move between services without context switching.

### Security Requirements

All code MUST adhere to these security standards:

- NO hardcoded secrets (use Nacos/environment variables)
- Validate ALL inputs at adapter layer using `@Valid`
- Sanitize user-generated content before storage/display
- Log all security events (authentication, authorization, access control)
- Use parameterized logging to prevent log injection
- Apply rate limiting at gateway level
- Implement circuit breakers for external API calls

**Rationale**: Security vulnerabilities can expose sensitive medical literature data or enable service abuse. Security MUST be built in from the start, not added later.

## Development Workflow

### Standard 7-Step Process

ALL new features MUST follow this workflow:

1. **Confirm inputs**: Module, contracts, ports, DTOs, signatures
2. **Domain first**: Pure Java entities, aggregates, VOs, ports (NO frameworks)
3. **Application orchestration**: Coordinate domain + repositories, manage transactions (NO business rules)
4. **Infrastructure**: MyBatis-Plus repos, MapStruct mappers, DO ↔ Domain mapping
5. **Adapter**: Controllers/Jobs with `@Valid`, ProblemDetail error mapping, trace propagation
6. **Self-check**: Run `mvn -q -DskipTests compile` to verify compilation
7. **Handoff**: Submit minimal diff with key decisions documented

**Rationale**: This workflow ensures architectural compliance, prevents business logic leakage, and maintains consistency across all features.

### Code Style Standards

All code MUST follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html) with these additions:

- **POJOs**: Use `record` for immutables, Lombok for mutables
- **Error Handling**: Domain exceptions → App exceptions → HTTP (ProblemDetail)
- **Logging**: SLF4J parameterized English logs, no sensitive data
  - ERROR: failures requiring immediate attention
  - WARN: recoverable issues (degraded mode, fallback used)
  - INFO: key events (startup/shutdown, auth events, business operations)
  - DEBUG: detailed flow for troubleshooting (method entry/exit, decision branches)
  - TRACE: diagnostics (variable states, loop iterations)
- **Null safety**: Use `Optional` for repository queries, `@NonNull` for required params
- **Comments**: Javadoc for public APIs, inline comments for complex logic

**Rationale**: Consistent style reduces cognitive load during code reviews and debugging. Structured logging enables efficient troubleshooting in production.

### Documentation Requirements

Every module MUST have a README.md covering:

- Module purpose and responsibilities
- Key domain concepts and aggregates
- Use case inventory with brief descriptions
- Port interfaces and their implementations
- External dependencies and integration points
- Configuration parameters
- Example usage patterns

**Critical**: Developers MUST read the target module's README.md BEFORE any code reading or modification.

**Rationale**: Up-to-date module documentation enables faster onboarding and prevents architectural misunderstandings that lead to costly rework.

### Dependency Management

Adding dependencies MUST follow this process:

1. Check if functionality exists in `patra-common` or Hutool FIRST
2. For domain layer: NEVER add framework dependencies (Pure Java only)
3. For shared functionality: Consider adding to appropriate `patra-*` starter
4. For major dependencies: Coordinate with team to ensure consistency
5. Document rationale in commit message

**Rationale**: Uncontrolled dependency growth increases attack surface, build times, and maintenance burden. Reusing existing libraries reduces duplication.

## Governance

### Constitution Authority

This constitution supersedes all other practices and guidelines. When conflicts arise between this constitution and other documentation, the constitution takes precedence.

### Amendment Process

Amendments to this constitution require:

1. **Proposal**: Document proposed change with rationale and impact analysis
2. **Discussion**: Review with team, identify affected systems
3. **Approval**: Consensus from technical leads
4. **Migration Plan**: Define steps to bring existing code into compliance
5. **Version Bump**: Increment version according to semantic versioning rules
6. **Propagation**: Update all dependent templates and documentation

### Version Semantics

Constitution version follows semantic versioning:

- **MAJOR**: Backward incompatible governance or principle removals/redefinitions
- **MINOR**: New principle/section added or materially expanded guidance
- **PATCH**: Clarifications, wording, typo fixes, non-semantic refinements

### Compliance Review

All pull requests MUST verify compliance with this constitution:

- ArchUnit tests enforce dependency direction rules
- Code reviews check naming conventions and layer boundaries
- Integration tests validate idempotency and error handling
- Domain tests ensure business logic stays in domain layer

**Violation of NON-NEGOTIABLE principles will result in PR rejection.**

### Complexity Justification

Any deviation from simplicity principles (Principle VII) MUST be justified:

- Document the specific problem being solved
- Explain why simpler alternatives are insufficient
- Estimate maintenance cost vs. flexibility benefit
- Get approval before introducing new abstractions

### Guidance Files

For runtime development guidance beyond this constitution, refer to:

- `/CLAUDE.md` - AI assistant instructions
- `/.claude/AGENTS-architecture.md` - Architecture patterns and design principles
- `/.claude/AGENTS-development.md` - Development workflow and coding standards
- `/.claude/AGENTS-testing.md` - Testing strategy and organization
- `/docs/ARCHITECTURE.md` - Deep dive on architectural decisions
- `/docs/DEV-GUIDE.md` - Code recipes for common tasks

**Version**: 1.0.0 | **Ratified**: 2025-01-15 | **Last Amended**: 2025-01-15
