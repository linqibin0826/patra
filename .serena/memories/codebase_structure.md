# Codebase Structure

## Repository Root Structure
```
Papertrace-api/
├── patra-parent/              # Parent POM (dependency/plugin management)
├── patra-common/              # Common utilities & base classes
├── patra-expr-kernel/         # Expression engine (AST & normalization)
├── patra-gateway-boot/        # API Gateway (routing, auth)
├── patra-registry/            # SSOT registry microservice
├── patra-ingest/              # Collection/ingestion microservice
├── patra-egress-gateway/      # Egress gateway (outbound adapters)
├── patra-spring-boot-starter-core/        # Core starter (error resolution, tracing)
├── patra-spring-boot-starter-web/         # Web starter (ProblemDetail, REST)
├── patra-spring-boot-starter-mybatis/     # MyBatis starter (pagination, config)
├── patra-spring-boot-starter-expr/        # Expression starter
├── patra-spring-boot-starter-provenance/  # Provenance context management
├── patra-spring-cloud-starter-feign/      # Feign starter (error decoder, tracing)
├── docker/                    # Local infrastructure (Docker Compose)
├── docs/                      # Documentation
│   ├── overview/              # Architecture overview
│   ├── process/               # Business process docs
│   ├── modules/               # Module-specific deep-dives
│   ├── operations/            # Operations & troubleshooting
│   ├── standards/             # Standards & conventions
│   └── templates/             # Documentation templates
├── .claude/                   # Claude Code configuration
├── .serena/                   # Serena memories (this folder)
├── pom.xml                    # Root aggregator POM
├── CLAUDE.md                  # Main agent handbook
└── README.md                  # Project overview
```

## Microservice Module Structure
Each microservice (e.g., patra-registry, patra-ingest) follows this structure:
```
patra-{service}/
├── patra-{service}-boot/      # Executable entry point (main class, application.yaml)
├── patra-{service}-api/       # External API contract (DTOs, interfaces)
├── patra-{service}-domain/    # Domain layer
│   ├── model/                 # Entities, aggregates, value objects
│   │   ├── read/              # Read models
│   │   └── write/             # Write models (aggregates)
│   ├── port/                  # Port interfaces (outbound)
│   ├── event/                 # Domain events
│   └── exception/             # Domain exceptions
├── patra-{service}-app/       # Application layer
│   ├── {usecase}/             # Self-contained use cases
│   │   ├── *Orchestrator.java # Use case orchestrator
│   │   ├── *Command.java      # Command object
│   │   └── supporting files   # DTOs, helpers for this use case
│   └── port/                  # Application ports (if any)
├── patra-{service}-infra/     # Infrastructure layer
│   ├── repository/            # Repository implementations
│   │   ├── mybatis/           # MyBatis mappers
│   │   │   ├── mapper/        # Mapper interfaces
│   │   │   └── dataobject/    # Database objects (DOs)
│   │   └── *RepositoryImpl.java # Repository implementations
│   ├── rpc/                   # Remote procedure calls (Feign clients)
│   └── mq/                    # Message queue adapters (future)
├── patra-{service}-adapter/   # Adapter layer (inbound)
│   ├── rest/                  # REST controllers
│   ├── scheduler/             # XXL-Job schedulers
│   └── listener/              # MQ listeners (future)
├── pom.xml                    # Module aggregator POM
└── README.md                  # Module documentation
```

## Key Directories

### Domain Layer (domain/)
- Pure Java, no framework dependencies
- Contains business logic, invariants, domain events
- Ports define contracts for infrastructure
- Only depends on patra-common

### Application Layer (app/)
- Use case orchestration
- Transaction boundaries
- Depends on domain + patra-common + core starter
- Self-contained: each use case directory has all its components

### Infrastructure Layer (infra/)
- Implements domain ports
- MyBatis-Plus for database access
- Feign clients for RPC
- MapStruct for DO ↔ Domain mapping

### Adapter Layer (adapter/)
- Inbound adapters only (REST, jobs, MQ)
- Input validation (@Valid)
- Error mapping to ProblemDetail
- Depends on app + api + web starters

### API Layer (api/)
- External contracts (DTOs, interfaces)
- No framework dependencies (for client reuse)
- Error code definitions

### Boot Layer (boot/)
- Spring Boot main class
- Configuration files (application.yaml)
- Error configuration imports
- Dependency wiring

## Documentation Structure
- `docs/overview/`: Architecture overview and design principles
- `docs/process/`: Business process flows (e.g., ingest-dataflow.md)
- `docs/modules/`: Module-specific deep-dives (e.g., registry/deep-dive.md)
- `docs/operations/`: Troubleshooting and operations guides
- `docs/standards/`: Platform standards (e.g., error handling, cross-service patterns)
- `docs/templates/`: Documentation templates

## Important Files
- `CLAUDE.md`: Main agent handbook (this file is the source of truth)
- `README.md`: Project overview and quick start
- `pom.xml`: Root aggregator POM (lists all modules)
- `patra-parent/pom.xml`: Parent POM (dependency and plugin management)
- `docker/compose/docker-compose.dev.yaml`: Local infrastructure setup