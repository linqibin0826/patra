# AGENTS.md

AI agent instructions for Papertrace – Medical Literature Data Platform.

---

## Project Overview

**Purpose**: Collect, parse, cleanse, and standardize medical literature from 10+ sources (PubMed, EPMC, etc.)

**Architecture**: Microservices (Spring Cloud) + Hexagonal Architecture + DDD + Event-Driven

**Tech Stack**:
- Java 21
- Spring Boot 3.2.4 + Spring Cloud 2023.0.1 + Spring Cloud Alibaba 2023.0.1.0
- Maven (build tool)
- MyBatis-Plus (persistence)
- MapStruct (object mapping)
- Nacos (configuration)

---

## Repository Structure

```
Papertrace-api/
├─ patra-parent/                    # Parent POM
├─ patra-common/                    # Common utilities & base classes
├─ patra-expr-kernel/               # Expression engine
├─ patra-gateway-boot/              # API Gateway
├─ patra-registry/                  # Registry microservice (SSOT)
├─ patra-ingest/                    # Ingestion microservice
├─ patra-spring-boot-starter-*/     # Custom starters
└─ docker/                          # Local infrastructure
```

**Each microservice follows**:
```
patra-{service}/
├─ patra-{service}-boot/       # Spring Boot entry point
├─ patra-{service}-api/        # External API contracts (DTOs/interfaces)
├─ patra-{service}-domain/     # Domain layer (Pure Java)
├─ patra-{service}-app/        # Application layer (use case orchestration)
├─ patra-{service}-infra/      # Infrastructure layer (repositories, adapters)
└─ patra-{service}-adapter/    # Adapter layer (REST/Jobs/MQ)
```

---

## Architecture Rules (CRITICAL)

### Hexagonal Architecture + DDD Layers

**Layer 1: Domain (Pure Java - Innermost)**
- Aggregates, Entities, Value Objects, Domain Events
- Port interfaces (NO implementation details)
- **ONLY** depends on `patra-common`
- **NO** Spring or framework dependencies

**Layer 2: Application (Orchestrator)**
- Use case orchestration via `*Orchestrator` classes
- Transaction boundaries
- Cross-aggregate coordination
- **NO** business logic (delegate to Domain)

**Layer 3: Infrastructure**
- `*RepositoryImpl` implementations
- MyBatis-Plus + MapStruct for DO ↔ Domain mapping
- Database access, caching, external service calls
- JsonNode for JSON fields

**Layer 4: Adapter (Outermost)**
- REST Controllers, Job Schedulers, MQ Listeners
- Input validation with `@Valid`
- Error mapping to HTTP responses
- Trace propagation

### Dependency Direction (MUST FOLLOW)

```
adapter  →  app + api
app      →  domain + patra-common
infra    →  domain + patra-common
domain   →  patra-common ONLY
api      →  NO dependencies
```

**NO reverse dependencies allowed!**

---

## Dos and Don'ts

### Domain Layer
✅ **DO**: Keep pure Java, define business rules, create Port interfaces
❌ **DON'T**: Use `@Service`, `@Autowired`, or any Spring annotations

### Application Layer
✅ **DO**: Orchestrate use cases, manage transactions
❌ **DON'T**: Implement business logic (belongs in Domain)

### Infrastructure Layer
✅ **DO**: Use MyBatis-Plus + MapStruct for mapping
❌ **DON'T**: Expose database entities outside this layer, Use Jpa.

### Adapter Layer
✅ **DO**: Validate inputs with `@Valid`, map errors to ProblemDetail
❌ **DON'T**: Call infrastructure directly (use Application layer)

### General
✅ **DO**: Reuse `patra-*` starters, `patra-common`, Hutool utilities
✅ **DO**: Use `record` for immutables, Lombok for mutable DTOs
✅ **DO**: Use parameterized logging in English, never log sensitive data
✅ **DO**: Ask before acting when information is insufficient
❌ **DON'T**: Hardcode secrets, connection strings, or configs (use Nacos)
❌ **DON'T**: Create N+1 queries, always batch when possible

---

## Build and Test Commands

### Compile check (no tests)
```bash
mvn -q -DskipTests compile
```

### Run specific service locally
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
mvn clean install                 # Build all modules
mvn clean install -DskipTests     # Skip tests
```

### Code formatting (if configured)
```bash
mvn spotless:apply                # Format code
mvn spotless:check                # Check formatting
```

---

## Naming Conventions

- **Orchestrators**: `*Orchestrator` (e.g., `PlanBatchOrchestrator`)
- **Commands**: `*Command` (e.g., `CreateBatchPlanCommand`)
- **Repository Implementations**: `*RepositoryImpl` (e.g., `ProvenanceRepositoryImpl`)
- **Domain Ports**: `*Port` (e.g., `ProvenanceQueryPort`)
- **Value Objects**: Descriptive names (e.g., `BatchSize`, `ProvenanceId`)

---

## Error Handling Pattern

```
Domain Exception  →  Application Exception  →  HTTP Error Response
   (domain/)            (app/)                   (adapter/)
```

- Domain layer throws domain-specific exceptions
- Application layer translates to application exceptions
- Adapter layer maps to HTTP status codes via ProblemDetail

---

## Development Workflow (7 Steps)

1. **Confirm inputs**: Target module/package, contracts, ports, DTOs, use case signatures
2. **Define/improve Domain**: Pure Java entities, aggregates, value objects, ports
3. **Implement App orchestration**: Transaction boundaries, coordinate domain operations
4. **Implement Infra**: MyBatis-Plus repositories, MapStruct mappers
5. **Implement Adapter**: Controllers/Jobs with `@Valid`, error mapping
6. **Self-check**: Run `mvn -q -DskipTests compile` to verify compilation
7. **Handoff**: Submit minimal diff for review

---

## Testing Guidelines

- **Unit Tests**: Test domain logic in isolation (pure Java)
- **Integration Tests**: Test infrastructure layer with TestContainers(in patra-{service}-boot)
- **API Tests**: Test adapter layer with MockMvc or RestAssured
- **Coverage**: Aim for 80%+ on domain and application layers

---

## Code Style

- **Indentation**: 4 spaces (Java), 2 spaces (XML/YAML)
- **Line length**: 120 characters max
- **Imports**: Organize imports, remove unused
- **Comments**: JavaDoc for public APIs, inline for complex logic
- **Logging**: Use SLF4J with parameterized messages

---

## Security Considerations

- Never hardcode credentials, API keys, or secrets
- Use Nacos or environment variables for configuration
- Validate all external inputs
- Sanitize user-generated content
- Follow principle of least privilege

---

## Commit Guidelines

- Use meaningful commit messages
- Reference issue/ticket numbers when applicable
- Run `mvn -q -DskipTests compile` before committing
- Keep commits small and focused

---

## Available Tools & Resources

### MCP Tools (Model Context Protocol)

**sequential-thinking**
- Deep analysis and step-by-step problem solving
- Use for: Complex multi-step tasks, architectural decisions, debugging

**context7**
- Fetch up-to-date documentation for libraries/frameworks
- Use for: API references, version-specific docs, best practices

**serena**
- Semantic code navigation and intelligent editing
- Use for: Understanding codebase structure, finding symbols, analyzing dependencies
- Key capabilities: Overview files, find symbols by name path, search patterns, trace references, edit by symbol

### Starters & Common Libraries

- **patra-common**: Base classes, utilities, shared domain concepts
- **patra-spring-boot-starter-core**: Core Spring Boot configurations
- **patra-spring-boot-starter-web**: Web-specific configurations
- **patra-spring-boot-starter-mybatis**: MyBatis-Plus configurations
- **Hutool**: Java utility library (use for common operations)

---

## Performance Tips

- Use batch operations for bulk inserts/updates
- Implement proper database indexing
- Cache frequently accessed data
- Use async processing for non-critical operations
- Monitor N+1 query patterns

---

## Additional Resources

- Architecture docs: `docs/ARCHITECTURE.md`
- Development guide: `docs/DEV-GUIDE.md`
- Module-specific READMEs in each `patra-*/README.md`

---

**Note**: This file provides guidance for universal AI coding assistants (OpenAI Codex, GitHub Copilot, Google Gemini, etc.). For Claude-specific instructions, see `CLAUDE.md`.
