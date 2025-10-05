---
inclusion: always
---

# Project Structure

## Architecture Pattern

The project follows **Hexagonal Architecture (Ports & Adapters) + Domain-Driven Design (DDD)** with strict layering.

### Layer Dependencies

```
adapter → app → domain ← infra
```

- **Domain layer**: Pure Java, no framework dependencies. Expresses business invariants through aggregates, value objects, and domain events.
- **Application layer**: Orchestrates use cases, transactions, idempotency control. Depends only on domain ports.
- **Adapter layer**: Handles inbound interactions (REST, scheduling). Responsible for protocol conversion and error mapping. **Inbound only** - no outbound adapters.
- **Infrastructure layer**: Implements outbound secondary ports (repositories, messaging, Feign). Constrained by domain port contracts.
- **Boot layer**: Integrates configuration and enforces dependency direction.

## Module Organization

### Top-Level Modules

- `patra-parent`: Maven parent POM for unified dependency management
- `patra-common`: Cross-service domain base classes, error models, JSON utilities
- `patra-expr-kernel`: Expression AST and normalization engine
- `patra-gateway-boot`: API gateway service
- `patra-registry`: Configuration SSOT service
- `patra-ingest`: Collection planning and task assembly service
- `patra-spring-boot-starter-*`: Auto-configuration starters (core, web, mybatis, expr)
- `patra-spring-cloud-starter-feign`: Feign client auto-configuration

### Service Module Structure

Each service follows a consistent sub-module pattern:

```
patra-{service}/
├── patra-{service}-api/          # Error codes, external DTOs
├── patra-{service}-adapter/      # Inbound adapters (REST, scheduling)
├── patra-{service}-app/          # Use case orchestration
│   └── usecase/                  # Use case packages
│       ├── {usecase}/            # Self-contained use case
│       │   ├── *UseCase.java     # Use case interface
│       │   ├── *Orchestrator.java # Main orchestrator
│       │   ├── command/          # Command objects
│       │   ├── dto/              # Result DTOs
│       │   └── ...               # Supporting components
├── patra-{service}-domain/       # Aggregates, entities, domain ports
│   ├── model/
│   │   ├── aggregate/            # Aggregate roots (with Aggregate suffix)
│   │   └── vo/                   # Value objects
│   ├── event/                    # Domain events
│   └── port/                     # Domain ports (interfaces)
├── patra-{service}-infra/        # Outbound implementations
│   ├── persistence/              # MyBatis-Plus DO, Mapper, Repository
│   ├── rpc/                      # Feign clients
│   └── messaging/                # Message publishers
└── patra-{service}-boot/         # Spring Boot application
```

## Naming Conventions

### Aggregates
- All aggregate roots use `Aggregate` suffix (e.g., `PlanAggregate`, `TaskAggregate`)
- Rationale: Explicit identification of aggregate boundaries in hexagonal architecture

### Use Case Components
- `*UseCase`: Use case interface
- `*Orchestrator`: Main orchestrator implementation
- `*Command`: Command objects
- `*Result` / `*Report`: Result DTOs
- `*Impl`: Implementation suffix for interfaces

### Persistence
- `*DO`: Data Object (MyBatis-Plus entity)
- `*Mapper`: MyBatis-Plus mapper interface
- `*Converter`: MapStruct converter for DO ↔ Domain mapping

## Key Directories

- `docs/`: Comprehensive documentation
  - `overview/`: Architecture and system overview
  - `process/`: End-to-end business flows
  - `modules/`: Deep-dive module documentation
  - `standards/`: Cross-service conventions
  - `operations/`: Deployment and troubleshooting
  - `templates/`: Documentation templates
- `docker/`: Infrastructure service configurations
  - `compose/`: Docker Compose files
  - `mysql/`, `redis/`, `es/`, etc.: Service-specific data and config

## Configuration Management

- **Nacos**: Primary configuration source for all services
- **Local Config**: `application.yaml` + `application-local.yaml` for development
- **Error Config**: Separate error configuration files (e.g., `ingest-error-config.yaml`)
- **Import Pattern**: Use `spring.config.import` to include additional config files

## Documentation Standards

- Module README: High-level overview, quick start, key capabilities
- Deep-dive docs: Located in `docs/modules/{module}/deep-dive.md`
- Keep module README concise, refer to deep-dive for details
- All documentation in Chinese (业务文档) or English (technical specs)
