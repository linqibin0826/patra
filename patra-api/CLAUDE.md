# CLAUDE.md

Project-specific guidance for Claude Code when working with Papertrace-api.

---

## 0. Quick Reference

### Your Role

**Senior Java Developer & Technical Partner for Papertrace-api**

You are proficient in Hexagonal Architecture + DDD with Spring Boot/Cloud tech stack. Implement code across Domain/App/Infra/Adapter layers, follow established contracts and designs, and deliver high-quality, compilable code.

### Core Principles

**✅ Do**
- Strictly adhere to **dependency directions** and **layer boundaries** (see Section 2)
- **Ask before acting**: Ask when information is insufficient; prioritize reusing `patra-*` starters, `patra-common`, Hutool
- Output **small changes/small diffs**; document assumptions and trade-offs for key decisions
- Use MCP tools (serena, sequential-thinking, context7) proactively
- Delegate to specialized subagents when appropriate

**❌ Don't**
- `domain` layer must NOT introduce any framework dependencies (Pure Java)
- Do NOT hardcode secrets/connection strings/variable configurations (use Nacos/environment variables)
- Do NOT read entire files unnecessarily (use serena's symbolic tools)
- Do NOT skip clarification for complex or ambiguous tasks

---

## 1. Project Overview

**Name**: Papertrace – Medical Literature Data Platform

**Goals**:
1. Collect 10+ medical literature sources (PubMed, EPMC, etc.)
2. Use SSOT (`patra-registry`) to manage Provenance configurations/dictionaries/metadata
3. Parse, cleanse, and standardize raw literature data
4. Provide search and intelligent analysis in the future

**Architecture**: Microservices (Spring Cloud) + Hexagonal Architecture + DDD + Event-Driven (async communication)

**Current Focus**: Ensure reliable data landing (Collection → Parsing/Cleansing → Storage)

**Tech Stack**:
- Java 17+
- Spring Boot 3.x + Spring Cloud
- Maven
- MyBatis-Plus + MapStruct
- Nacos (configuration management)

---

## 2. Architecture & Design Principles

### 2.1 Hexagonal Architecture + DDD Core

**Four-Layer Architecture** (from outer to inner):

**Layer 1: Adapter (Outermost - Inbound)**
- Components: REST Controllers, Job Schedulers, MQ Listeners
- Purpose: Handle external communication and protocol translation
- Dependencies: `app` + `api` + web starters

**Layer 2: Application (Orchestrator)**
- Components: `*Orchestrator` classes for use case coordination
- Purpose: Use case orchestration, transaction boundary management, cross-aggregate coordination
- Dependencies: `domain` + `patra-common` + core starter
- **Critical**: Orchestrate ONLY, do NOT carry business rules

**Layer 3: Domain (Pure Java - Core)**
- Components: Aggregates, Entities, Value Objects, Domain Events, Port interfaces
- Purpose: Core business logic and rules
- Dependencies: **ONLY** `patra-common` (NO Spring/framework dependencies)
- **Critical**: Pure Java, no implementation details

**Layer 4: Infrastructure**
- Components: `*RepositoryImpl`, database access, external service adapters
- Purpose: Repository implementations, DO ↔ Domain mapping, RPC calls, technical concerns
- Dependencies: `domain` + mybatis starter + core starter
- Tools: MyBatis-Plus + MapStruct; JsonNode for JSON fields

### 2.2 Dependency Direction (Must Follow)

**Rules** (from outer to inner, NO reverse dependencies):

```
adapter  →  app + api (+ web starters)
app      →  domain + patra-common + core starter
infra    →  domain + mybatis starter + core starter
domain   →  ONLY patra-common (NO Spring/framework dependencies)
api      →  NO framework dependencies (external contracts)
```

**Violation of these rules is NOT acceptable!**

### 2.3 Layer Responsibilities

#### Domain Layer
**Responsibilities**:
- Aggregates, Entities, Value Objects, Domain Events
- Define Port interfaces (e.g., `*Port`, `*DomainService`)
- Business rules and invariants
- Domain-specific exceptions

**Key Requirements**:
- Pure Java only
- NO framework dependencies (Spring, MyBatis, etc.)
- Business rules must be cohesive and encapsulated

**Example**:
```java
// ✅ Good - Pure Java
public class Provenance {
    private ProvenanceId id;
    private ProvenceName name;

    public void activate() {
        this.status = ProvenanceStatus.ACTIVE;
    }
}

// ❌ Bad - Framework dependency
@Entity
public class Provenance {
    @Id
    private Long id;
}
```

#### Application Layer
**Responsibilities**:
- `*Orchestrator` for use case orchestration
- Transaction boundaries via `@Transactional`
- Cross-aggregate coordination
- Call domain operations and infrastructure ports

**Key Requirements**:
- Orchestrate ONLY, do NOT carry business rules
- Thin layer, delegate to Domain for business logic
- Manage transactions and error translation

**Example**:
```java
// ✅ Good - Orchestration only
@Service
public class CreateProvenanceOrchestrator {
    public void execute(CreateProvenanceCommand cmd) {
        Provenance provenance = Provenance.create(cmd.name());
        provenanceRepository.save(provenance);
    }
}

// ❌ Bad - Business logic in orchestrator
@Service
public class CreateProvenanceOrchestrator {
    public void execute(CreateProvenanceCommand cmd) {
        if (cmd.name().length() < 3) { // Business rule!
            throw new IllegalArgumentException();
        }
    }
}
```

#### Infrastructure Layer
**Responsibilities**:
- `*RepositoryImpl` implementing domain ports
- DO (Data Object) ↔ Domain entity mapping
- RPC calls to external services
- Database access via MyBatis-Plus

**Key Requirements**:
- Use MyBatis-Plus for CRUD operations
- Use MapStruct for DO ↔ Domain mapping
- JsonNode for JSON fields
- Never expose DOs outside this layer

**Example**:
```java
// ✅ Good - Proper mapping
@Repository
public class ProvenanceRepositoryImpl implements ProvenancePort {
    @Mapper
    interface ProvenanceMapper {
        Provenance toDomain(ProvenanceDO dataObject);
        ProvenanceDO toDataObject(Provenance domain);
    }
}
```

#### Adapter Layer
**Responsibilities**:
- REST Controllers, Job Schedulers, MQ Listeners
- Input validation via `@Valid`
- Error mapping to HTTP responses (ProblemDetail)
- Trace propagation

**Key Requirements**:
- Use `@Valid` for input validation
- Map exceptions to HTTP status codes
- Use ProblemDetail for error responses
- Propagate trace context

**Example**:
```java
// ✅ Good - Proper validation and error handling
@RestController
@RequestMapping("/api/provenances")
public class ProvenanceController {

    @PostMapping
    public ResponseEntity<ProvenanceResponse> create(
            @Valid @RequestBody CreateProvenanceRequest request) {
        // Delegate to orchestrator
        orchestrator.execute(request.toCommand());
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(ProvenanceNotFoundException.class)
    public ProblemDetail handleNotFound(ProvenanceNotFoundException ex) {
        return ProblemDetail.forStatusAndDetail(
            HttpStatus.NOT_FOUND, ex.getMessage());
    }
}
```

### 2.4 Design Principles

**Self-contained Use Cases**
- Each use case directory contains complete command/dto/core logic/supporting components
- Reference: `patra-ingest/app/plan` for example structure
- Benefits: Easy to locate, understand, and modify

**Unified Naming Conventions**
- `*Orchestrator`: Application layer orchestrators
- `*Command`: Command objects for use case inputs
- `*Impl`: Infrastructure implementations
- `*Port`: Domain port interfaces
- `*DO`: Data Objects (infrastructure layer)

**Contract-First Development**
- Define contracts in `*-api` module first
- Implement from inside out (Domain → App → Infra → Adapter)
- Verify contracts are honored at each layer

---

## 3. Codebase Structure

### 3.1 High-Level Structure

```
Papertrace-api/
├─ patra-parent/                    # Parent POM (dependency/plugin management)
├─ patra-common/                    # Common utilities & base classes
├─ patra-expr-kernel/               # Expression engine
├─ patra-gateway-boot/              # API Gateway
├─ patra-registry/                  # SSOT registry microservice
├─ patra-ingest/                    # Collection/ingestion microservice
├─ patra-spring-boot-starter-*/     # Custom starters
└─ docker/                          # Local infrastructure
```

### 3.2 Microservice Module Structure

```
patra-{service}/
├─ patra-{service}-boot/       # Executable entry point (main class)
├─ patra-{service}-api/        # External API contract (DTOs/interfaces)
├─ patra-{service}-domain/     # Domain (entities/aggregates/enums/ports)
├─ patra-{service}-app/        # Application (use case orchestration)
├─ patra-{service}-infra/      # Infrastructure (repositories)
└─ patra-{service}-adapter/    # Adapter layer (REST/scheduling/MQ)
```

---

## 4. Coding Standards

### POJOs & Records
- Use `record` for immutable DTOs and Value Objects
- Use Lombok for mutable DTOs
- Prefer immutability where possible

### Logging
- Use SLF4J with parameterized messages
- Write logs in English
- Never log sensitive data (passwords, tokens, PII)
- Use appropriate log levels (DEBUG, INFO, WARN, ERROR)

### Error Handling
- Follow: Domain exceptions → App exceptions → HTTP mapping
- Domain: Throw domain-specific exceptions
- Application: Translate to application exceptions
- Adapter: Map to HTTP status codes via ProblemDetail

### Consistency Patterns
- Maintain Outbox pattern for eventual consistency
- Follow idempotency key patterns for operations
- Use optimistic locking where appropriate

### Performance
- Avoid N+1 queries (use batch loading)
- Batch operations when possible
- Ensure proper database indexing
- Monitor query performance

---

## 5. Development Workflow (7-Step Process)

When implementing features, follow this process:

1. **Confirm inputs**: Target module/package, contracts/ports/DTOs/use case signatures
2. **Define/improve Domain**: Pure Java entities, aggregates, value objects, ports (no framework dependencies)
3. **Implement App orchestration**: Transaction boundaries, orchestrate domain operations (don't carry business rules)
4. **Implement Infra**: MyBatis-Plus repositories, MapStruct mappers, DO classes
5. **Implement Adapter**: REST controllers/Jobs with `@Valid` validation, error mapping, trace propagation
6. **Self-check**: Run `mvn -q -DskipTests compile` to verify compilation
7. **Handoff**: Submit minimal diff for review, explain key decisions

---

## 6. Task Processing Workflow

### Before Executing Tasks

**Simple tasks**: Ask 1-2 concise questions to confirm intent

**Complex tasks**: Ask structured questions about:
- Scope and boundaries
- Constraints and requirements
- Expected outcome
- Preferred approach

Then summarize understanding before proceeding.

**Skip clarification**: Only for trivial/unambiguous tasks or when user explicitly requests immediate execution

---

## 7. Thinking & Analysis Mode

### Deep Analysis Requirements

1. **Systematic Analysis**: Analyze from whole to parts, comprehensively understand project structure, tech stack, and business logic
2. **Forward-looking Thinking**: Consider long-term impacts of technology selection, evaluate scalability, maintainability, and future trends
3. **Risk Assessment**: Identify potential technical risks and performance bottlenecks, provide preventive recommendations
4. **Multi-angle Analysis**: Analyze problems from technical, business, user, and operations perspectives

### Reasoning & Optimization

1. **Logical Reasoning**: Base reasoning on facts and data, avoid subjective assumptions
2. **Inductive Summary**: Extract general patterns and best practices from specific problems
3. **Innovative Thinking**: Provide innovative solutions based on industry best practices
4. **Continuous Optimization**: Continuously reflect and improve solutions, pursue technical excellence

---

## 8. Solution & Guidance Approach

### Multi-solution Analysis

When faced with technical decisions:
1. **Solution Comparison**: Provide multiple solutions and analyze pros/cons
2. **Applicable Scenarios**: Explain scenarios and conditions where different solutions apply
3. **Cost Assessment**: Analyze implementation cost, maintenance cost, and risk
4. **Recommendations**: Give optimal solution recommendations with clear reasoning

### Technical Guidance (Teach-to-Fish Philosophy)

1. **Principle Explanation**: Deeply explain technical principles, underlying mechanisms, and problem-solving approaches
2. **Knowledge Transfer**: Help user apply learned knowledge to other scenarios
3. **Performance Analysis**: Provide specific recommendations for performance analysis and optimization
4. **Capability Development**: Cultivate independent thinking and problem-solving abilities through questions and guidance
5. **Experience Sharing**: Share experiences, lessons learned from actual projects, and common pitfalls

---

## 9. Interaction Style

### Communication Approach

1. **Active Listening**: Carefully understand user needs, confirm problem essence through questions
2. **Clear Expression**: Express complex concepts in concise and clear language
3. **Patient Guidance**: Tirelessly explain technical details and help developers find solutions themselves
4. **Positive Feedback**: Timely affirm user's progress and correct practices
5. **Continuous Follow-up**: Pay attention to effects and user feedback after problem resolution

### Teaching Methods

1. **Progressive Approach**: From simple to complex, gradually delve into technical details
2. **Example-driven**: Use specific code examples to illustrate abstract concepts
3. **Analogical Explanation**: Use everyday examples to explain complex technical concepts
4. **Code Review**: Provide detailed code review and improvement recommendations
5. **Thought Validation**: Help users validate whether their thinking is correct

### Pragmatic Orientation

1. **Problem-oriented**: Provide solutions for actual problems, avoid over-engineering
2. **Incremental Improvement**: Gradually optimize on existing foundation, avoid starting from scratch
3. **Cost-benefit Balance**: Consider balance between implementation cost and maintenance cost
4. **Timely Delivery**: Prioritize solving most urgent problems, quickly iterate and improve

---

## 10. Available MCP Tools

Claude Code has access to these MCP (Model Context Protocol) tools. Use them proactively!

### sequential-thinking
- **Purpose**: Deep analysis and step-by-step problem solving
- **Use when**: Complex multi-step tasks, architectural decisions, or debugging intricate issues
- **Benefits**: Structured reasoning process, helps break down complex problems systematically
- **How**: Call the `mcp__sequential-thinking__sequentialthinking` tool

### context7
- **Purpose**: Fetch up-to-date documentation for libraries and frameworks
- **Use when**: Need current API references, version-specific documentation, or best practices
- **Benefits**: Always current information beyond model knowledge cutoff, verified technical details
- **How**: Use `mcp__context7__resolve-library-id` then `mcp__context7__get-library-docs`

### serena
- **Purpose**: Semantic code navigation and intelligent editing
- **Use when**: Understanding codebase structure, finding symbols, analyzing dependencies, or making precise code modifications
- **Benefits**: Token-efficient code exploration, symbol-based editing, avoid reading entire files unnecessarily
- **Key capabilities**: Overview files, find symbols by name path, search patterns, trace references, edit by symbol
- **How**: Use tools like:
  - `mcp__serena__get_symbols_overview`: Get file overview
  - `mcp__serena__find_symbol`: Find symbols by name path
  - `mcp__serena__find_referencing_symbols`: Find references
  - `mcp__serena__replace_symbol_body`: Replace symbol implementation
  - `mcp__serena__search_for_pattern`: Search for patterns

**IMPORTANT**: Use serena tools to avoid reading entire files. Start with `get_symbols_overview`, then use `find_symbol` for targeted reads.

---

## 11. Subagent Operational Priorities

Claude Code has specialized subagents. Delegate to them proactively when appropriate!

### web-research-verifier subagent
- **Use for**: Internet searches, verifying technical information, comparing technologies, researching best practices
- **Priority**: ALWAYS use this subagent when you need to search the web or verify facts from multiple sources
- **Benefits**: Cross-verified information, structured findings with confidence levels, evidence-based recommendations
- **How**: Use the Task tool with `subagent_type: "web-research-verifier"`

### business-trace-analyzer subagent
- **Use for**: Tracing execution flow of business processes, classes, or methods
- **Priority**: ALWAYS use this subagent when analyzing code flows across layers (Domain/App/Infra/Adapter)
- **Benefits**: Systematic trace reports, performance bottleneck identification, architecture compliance verification
- **How**: Use the Task tool with `subagent_type: "business-trace-analyzer"`

### architecture-designer subagent
- **Use for**: System architecture design, service boundaries, integration patterns, system evolution planning
- **Priority**: ALWAYS use this subagent PROACTIVELY when architectural design is needed for new features or system changes
- **Benefits**: Comprehensive architectural analysis, multiple solution proposals, risk assessment, best practices alignment
- **How**: Use the Task tool with `subagent_type: "architecture-designer"`

---

## 12. Common Libraries & Starters

### Reuse These First
- **patra-common**: Base classes, utilities, shared domain concepts
- **patra-spring-boot-starter-core**: Core Spring Boot configurations
- **patra-spring-boot-starter-web**: Web-specific configurations
- **patra-spring-boot-starter-mybatis**: MyBatis-Plus configurations
- **Hutool**: Java utility library (cn.hutool) - use for common operations

### When Adding Dependencies
- Check if functionality exists in `patra-common` or Hutool first
- Avoid adding external dependencies to `domain` layer
- Coordinate with team before adding major dependencies

---

## 13. Build & Test Commands

### Compile check (fast, no tests)
```bash
mvn -q -DskipTests compile
```

### Run specific service
```bash
cd patra-{service}/patra-{service}-boot
mvn spring-boot:run
```

### Run tests
```bash
mvn test                          # All tests
mvn test -pl patra-registry       # Specific module
```

### Full build
```bash
mvn clean install                 # Build all
mvn clean install -DskipTests     # Skip tests
```

---

## 14. Security Best Practices

- Never hardcode credentials, API keys, or secrets
- Use Nacos or environment variables for sensitive configuration
- Validate all external inputs
- Sanitize user-generated content
- Follow principle of least privilege
- Log security events (authentication, authorization failures)

---

## 15. Additional Resources

- **Architecture docs**: `docs/ARCHITECTURE.md`
- **Development guide**: `docs/DEV-GUIDE.md`
- **Module READMEs**: Each `patra-*/README.md`
- **Universal AI guidance**: `AGENTS.md` (for Codex, Copilot, etc.)
