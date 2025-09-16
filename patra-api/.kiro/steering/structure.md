# Project Structure

## Root Level Organization

```
Papertrace/
├── patra-parent/                    # Parent POM with dependency management
├── patra-common/                    # Shared utilities and base classes
├── patra-expr-kernel/               # Expression evaluation engine
├── patra-gateway-boot/              # API Gateway service
├── patra-registry/                  # Registry microservice (SSOT)
├── patra-ingest/                    # Data ingestion microservice
├── patra-spring-boot-starter-*/     # Custom Spring Boot starters
└── docker/                          # Infrastructure setup
```

## Microservice Module Structure

Each business microservice follows hexagonal architecture with these sub-modules:

### Standard Module Layout
```
patra-[service]/
├── patra-[service]-boot/           # Executable application (main class)
├── patra-[service]-api/            # External API contracts (DTOs, interfaces)
├── patra-[service]-contract/       # Internal contracts (QueryPort, ReadModels)
├── patra-[service]-domain/         # Domain layer (entities, value objects, ports)
├── patra-[service]-app/            # Application layer (use cases, commands)
├── patra-[service]-infra/          # Infrastructure layer (repositories, adapters)
└── patra-[service]-adapter/        # Adapters (REST controllers, schedulers, MQ)
```

## Layer Dependencies (Hexagonal Architecture)

### Dependency Rules
- **adapter** → app + api (optional: web starters)
- **app** → domain + contract + common + core starter
- **infra** → domain + contract + mybatis starter + core starter  
- **domain** → common only (NO Spring/framework dependencies)
- **api** → No dependencies (pure contracts)
- **contract** → No framework dependencies

### Package Structure Within Modules

#### Adapter Layer (`com.patra.[service].adapter`)
```
rest/
  controller/          # REST endpoints
  dto/                # REST-specific DTOs
scheduler/             # Scheduled tasks (XXL-Job)
mq/
  consumer/           # Message consumers
  producer/           # Message producers
config/               # Adapter configuration
```

#### Application Layer (`com.patra.[service].app`)
```
service/              # Application services (use case orchestration)
usecase/
  command/            # Command objects
  query/              # Query objects
mapping/              # App↔Domain mapping (MapStruct)
security/             # Permission checking
event/                # Integration events
tx/                   # Transaction utilities
config/               # App configuration
```

#### Domain Layer (`com.patra.[service].domain`)
```
model/
  aggregate/          # Aggregate roots and entities
  vo/                 # Value objects
  event/              # Domain events
  enums/              # Domain enums (used across all layers)
port/                 # Repository and external service ports
```

#### Infrastructure Layer (`com.patra.[service].infra`)
```
persistence/
  entity/             # Database entities (inherit BaseEntity)
  mapper/             # MyBatis-Plus mappers
  repository/         # Repository implementations
mapstruct/            # Entity↔Aggregate converters
config/               # Infrastructure configuration
```

## Naming Conventions

### REST API Endpoints
- Prefix: `/api/[service]/**`
- Resources: Use plural nouns
- Actions: Use colon suffix for commands (e.g., `POST /provenances/{id}:sync`)

### Java Classes
- Controllers: `[Resource]Controller`
- Services: `[Domain]AppService`
- Repositories: `[Domain]Repository` (interface), `[Domain]RepositoryMpImpl` (implementation)
- Entities: `[Service][Domain]Entity`
- Aggregates: `[Domain]` (no suffix)
- Value Objects: `[Domain]Id`, `[Domain]Code`
- Commands: `[Action][Domain]Cmd`
- Queries: `Get[Domain]By[Criteria]Qry`

## Shared Modules

### Custom Spring Boot Starters
- `patra-spring-boot-starter-core`: Base configuration and utilities
- `patra-spring-boot-starter-web`: Web layer enhancements
- `patra-spring-boot-starter-mybatis`: Database layer with BaseEntity
- `patra-spring-boot-starter-expr`: Expression evaluation integration
- `patra-spring-cloud-starter-feign`: Feign client configuration

### Common Module
- Shared utilities and base classes
- Cross-cutting concerns
- Should be lightweight and framework-agnostic

## Infrastructure Setup

### Docker Services
Located in `docker/` with service-specific subdirectories:
- `compose/`: Docker Compose configurations
- `mysql/`: MySQL configuration and data
- `redis/`: Redis configuration and data  
- `es/`: Elasticsearch data
- `nacos/`: Nacos configuration and logs
- `otel/`: OpenTelemetry collector configuration

### Development Environment
Use `docker/compose/docker-compose.dev.yml` for local development infrastructure.