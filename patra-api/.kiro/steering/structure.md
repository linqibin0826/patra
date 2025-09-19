# Project Structure & Organization

## Root Level Structure
```
Papertrace/                          # Root aggregator POM
├── patra-parent/                    # Parent POM with dependency management
├── patra-common/                    # Shared utilities and base classes
├── patra-expr-kernel/               # Expression evaluation engine
├── patra-gateway-boot/              # API Gateway service
├── patra-registry/                  # Registry microservice (SSOT)
├── patra-ingest/                    # Data ingestion microservice
├── patra-spring-boot-starter-*/     # Custom framework starters
└── docker/                          # Development infrastructure
```

## Microservice Module Structure
Each microservice follows hexagonal architecture with these sub-modules:

### Standard Module Layout
```
patra-{service}/
├── patra-{service}-boot/            # Executable application entry point
├── patra-{service}-api/             # External API contracts (DTOs, interfaces)
├── patra-{service}-contract/        # Internal contracts (QueryPorts, ReadModels)
├── patra-{service}-domain/          # Domain layer (entities, aggregates, ports, enums)
├── patra-{service}-app/             # Application layer (use cases, services)
├── patra-{service}-infra/           # Infrastructure layer (repositories, adapters)
└── patra-{service}-adapter/         # Adapter layer (controllers, schedulers, MQ)
```

## Layer Dependencies & Constraints

### Dependency Direction Rules
- **adapter** → app + api (+ optional web starters)
- **app** → domain + contract (+ patra-common, core starter)
- **infra** → domain + contract (+ mybatis starter, core starter)
- **domain** → patra-common ONLY (no Spring/frameworks)
- **api** → no dependencies (pure contracts)
- **contract** → no framework dependencies

### Package Structure Standards

#### Domain Layer (`com.patra.{service}.domain`)
```
model/
  aggregate/{name}/                  # Aggregate roots and entities
  vo/                               # Value objects
  event/                            # Domain events
  enums/                            # Domain enums (shared across layers)
port/                               # Repository and service ports
```

#### Application Layer (`com.patra.{service}.app`)
```
service/                            # Application services (use case orchestration)
usecase/
  command/                          # Command objects
  query/                            # Query objects
mapping/                            # Domain ↔ App mapping (MapStruct)
security/                           # Permission checkers
event/                              # Integration events and publishers
tx/                                 # Transaction utilities (idempotency, locks)
config/                             # App-specific configuration
```

#### Infrastructure Layer (`com.patra.{service}.infra`)
```
persistence/
  entity/                           # Database entities (inherit BaseDO)
  mapper/                           # MyBatis-Plus mappers
  repository/                       # Repository implementations
mapstruct/                          # Entity ↔ Domain converters
config/                             # Infrastructure configuration
```

#### Adapter Layer (`com.patra.{service}.adapter`)
```
rest/
  controller/                       # REST controllers
  dto/                              # REST-specific DTOs (if needed)
scheduler/                          # XXL-Job schedulers
mq/
  consumer/                         # Message consumers
  producer/                         # Message producers
config/                             # Adapter configuration
```

## Naming Conventions

### REST API Patterns
- Base path: `/api/{service}/**`
- Resource names: plural nouns
- Command actions: colon suffix (e.g., `POST /provenances/{id}:sync`)

### Class Naming
- **Controllers**: `{Resource}Controller`
- **Services**: `{Aggregate}AppService`
- **Repositories**: `{Aggregate}Repository` (interface), `{Aggregate}RepositoryMpImpl` (impl)
- **Entities**: `{Service}{Table}DO`
- **Mappers**: `{Entity}Mapper` (MyBatis), `{Entity}Converter` (MapStruct)

### File Organization
- All entities inherit from `BaseDO` (audit fields, version, id)
- MapStruct converters handle entity ↔ domain mapping
- Repository implementations handle aggregate persistence as single units

## Critical Coding Rules

### Data Objects & Enums
- **No Java enums in DO classes** - Use string/int fields instead
- **JSON fields use Jackson JsonNode** - For database JSON columns
- **Immutable objects use record** - For value objects and immutable data
- **Mutable objects use Lombok + class** - Avoid manual getters/setters

### Tool Usage Priority
1. **Hutool** - First choice for utilities (domain layer approved)
2. **patra-common** - Project-specific shared utilities  
3. **patra-*-starters** - Framework capabilities
4. Only create new utilities if none exist

### Security & Performance
- **No hardcoded credentials** - Use environment variables/config center
- **Parameterized SQL only** - No string concatenation, prevent injection
- **Idempotent design** - All collection/parsing/cleaning must be re-entrant
- **Batch processing preferred** - Avoid N+1 queries, use pagination/async where possible