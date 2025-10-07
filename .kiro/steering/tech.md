---
inclusion: always
---

# Technology Stack

## Core Technologies

- **Java**: 21 (required)
- **Spring Boot**: 3.2.4
- **Spring Cloud**: 2023.0.1
- **Spring Cloud Alibaba**: 2023.0.1.0
- **MyBatis-Plus**: 3.5.12

## Infrastructure

- **MySQL**: 8.0 (primary data store)
- **Redis**: 7.0 (caching, rate limiting)
- **Elasticsearch**: 8.14 (literature indexing)
- **Nacos**: Service registry + configuration management
- **RocketMQ**: Message queue for event-driven architecture
- **SkyWalking**: Distributed tracing
- **XXL-Job**: Scheduling center

## Key Libraries

- **Lombok**: 1.18.38 (code generation)
- **MapStruct**: 1.6.3 (object mapping)
- **Hutool**: 5.8.22 (utilities)
- **Resilience4j**: Resilience library for rate limiting, retry, circuit breaker (used in patra-egress-gateway)
- **Jackson**: JSON/XML processing (used in patra-spring-boot-starter-provenance)
- **Micrometer**: Metrics collection and monitoring

## Build System

Maven is used for dependency management and builds. The repository includes Maven wrapper (`./mvnw`).

### Common Commands

```bash
# Full compilation (skip tests)
./mvnw -q -DskipTests compile

# Clean and test specific module
./mvnw -pl patra-registry -am clean test

# Run specific boot module
./mvnw -pl patra-registry/patra-registry-boot spring-boot:run
./mvnw -pl patra-egress-gateway/patra-egress-gateway-boot spring-boot:run

# Package all modules
./mvnw clean package -DskipTests
```

### Docker Environment

Infrastructure services are managed via Docker Compose:

```bash
# Start all infrastructure services
cd docker/compose
docker compose -f docker-compose.dev.yaml up -d

# Stop services
docker compose -f docker-compose.dev.yaml down
```

## Development Tools

- **Maven Wrapper**: Always use `./mvnw` instead of system Maven
- **Annotation Processing**: Lombok + MapStruct configured in compiler plugin
- **Encoding**: UTF-8 for all source files


## Current Development Modules

### patra-egress-gateway ✅
- **Tech Stack**: Spring Boot 3.2.4, Spring Cloud Gateway, Resilience4j
- **Core Components**: 
  - EgressGatewayClient: Unified external service call client
  - ResilienceConfigManager: Resilience configuration management
  - MetricsCollector: Metrics collection
- **Design Patterns**: Strategy pattern (resilience strategies), Decorator pattern (metrics collection)

### patra-spring-boot-starter-provenance 🔄
- **Tech Stack**: Spring Boot 3.2.4, Jackson, Jackson XML, Micrometer, Lombok, Hutool
- **Core Components**:
  - PubMedClient/EPMCClient: Data source client interfaces
  - GatewayRequestBuilder: Gateway request builder
  - ConfigLoader: Configuration loader (three-level priority)
  - XmlToJsonConverter: XML to JSON converter
  - ProvenanceMetrics: Performance metrics recorder
- **Design Patterns**: Strategy pattern (configuration loading), Builder pattern (request building), Adapter pattern (XML conversion)

## Architecture Design Principles

### 1. Layered Architecture
- **API Layer**: Provides unified interfaces (PubMedClient, EPMCClient)
- **Service Layer**: Business logic processing (ClientImpl)
- **Gateway Layer**: External service calls (patra-egress-gateway)
- **Infrastructure Layer**: Configuration, monitoring, logging (ConfigLoader, ProvenanceMetrics)

### 2. Separation of Concerns
- **Starter Responsibility**: API encapsulation, parameter models, response models, configuration management
- **Gateway Responsibility**: Resilience capabilities, observability, protocol conversion
- **Business Responsibility**: Parameter conversion (Expr → data source parameters), business logic, data processing
- **Configuration Responsibility**: Dynamic configuration management, environment adaptation

### 3. Extensibility
- **Independent Client Design**: Each data source has independent Client, no unified abstraction
- **Configuration-Driven**: Control behavior through configuration (three-level priority)
- **Interface Abstraction**: Interface-oriented programming, reduce coupling
- **Plugin Extension**: Adding new data sources only requires creating new Client and models

## Technical Decision Records

### 1. Why Choose Spring Cloud Gateway?
- **Decision**: Use Spring Cloud Gateway as gateway implementation
- **Rationale**: 
  - Good integration with Spring Boot ecosystem
  - Supports reactive programming model
  - Built-in load balancing and service discovery
  - Rich filter mechanism

### 2. Why Choose Resilience4j?
- **Decision**: Use Resilience4j to implement resilience capabilities
- **Rationale**:
  - Lightweight, no external dependencies
  - Supports multiple resilience patterns (retry, circuit breaker, rate limiting, isolation)
  - Good integration with Spring Boot
  - Rich monitoring metrics

### 3. Why Not Create Unified Data Source Abstraction?
- **Decision**: Each data source has independent Client interface, no unified abstraction
- **Rationale**:
  - Different data sources have very different APIs (parameters, response formats, pagination methods, etc.)
  - Unified abstraction would lead to complex and difficult-to-use interfaces
  - Independent Clients are clearer and easier to extend

### 4. Why Load Configuration Dynamically Every Time?
- **Decision**: Dynamically load configuration on each API call, no caching
- **Rationale**:
  - Support configuration hot reload (Nacos dynamic refresh)
  - Avoid cache consistency issues
  - Configuration loading overhead is small (prioritize using configuration passed by caller)

### 5. Why Use Record to Define Models?
- **Decision**: Use Java Record to define Request and Response objects
- **Rationale**:
  - Immutability, thread-safe
  - Concise syntax, reduce boilerplate code
  - Automatically generate equals, hashCode, toString
  - Support compact constructor for parameter validation

## Development Standards

### Code Standards
- **Naming**: Use meaningful English naming, follow Java naming conventions
- **Comments**: Key business logic must have comments, use Javadoc format
- **Exception Handling**: Unified exception handling, use ProvenanceClientException
- **Logging**: Use SLF4J + @Slf4j, unified log prefix `[PROVENANCE][LAYER]`

### Testing Standards
- **Unit Tests**: Core business logic must have unit tests, coverage > 80%
- **Integration Tests**: Key interfaces must have integration tests
- **Test Data**: Use Mock data, do not depend on external services
- **First Phase Constraint**: Unit tests and integration tests will be added in future iterations

### Documentation Standards
- **API Documentation**: Use Javadoc comments, generate API documentation
- **Architecture Documentation**: Use Markdown format, include architecture diagrams and flowcharts
- **Usage Documentation**: Module README.md, include configuration examples and usage examples
- **Specification Documentation**: Use EARS format to define requirement validation standards

## Module Tech Stack Comparison

| Module | Status | Core Technologies | Special Dependencies | Design Patterns |
|--------|--------|-------------------|---------------------|-----------------|
| patra-egress-gateway | ✅ Completed | Spring Cloud Gateway, Resilience4j | WebFlux, Reactor | Strategy pattern, Decorator pattern |
| patra-spring-boot-starter-provenance | 🔄 In Development | Jackson, Micrometer | Jackson XML, Hutool | Strategy pattern, Builder pattern, Adapter pattern |

## Performance Metrics Design

### patra-egress-gateway
- **Request Metrics**: Total requests, success rate, response time distribution
- **Resilience Metrics**: Retry count, circuit breaker status, rate limiting trigger count
- **Resource Metrics**: Connection pool usage, memory usage

### patra-spring-boot-starter-provenance
- **API Call Metrics**: 
  - Timer: `provenance.client.api.duration` (grouped by provenance, api)
  - Counter: `provenance.client.api.success` / `provenance.client.api.failure`
- **Configuration Loading Metrics**: Configuration source distribution (caller passed, database, local config)
- **Conversion Metrics**: XML to JSON success rate and duration

## Unified Observability Standards

### Log Format
```
[MODULE][LAYER] message: key1=value1 key2=value2 traceId=xxx
```

### Module Log Prefixes
- **patra-egress-gateway**: `[EGRESS-GATEWAY][LAYER]`
- **patra-spring-boot-starter-provenance**: `[PROVENANCE][LAYER]`

### Layer Identifiers
- **CORE**: Core business layer
- **INTERNAL**: Internal implementation layer  
- **BOOT**: Auto-configuration layer
- **GATEWAY**: Gateway call layer

### TraceId Propagation
- Depends on SkyWalking Agent for automatic injection
- Get via MDC: `MDC.get("traceId")`
- All logs must include traceId

## Unified Configuration Management Standards

### Configuration Prefix Standards
- **Gateway Configuration**: `patra.egress.gateway`
- **Data Source Configuration**: `patra.provenance`

### Configuration Priority
1. **Runtime Passed** (highest priority)
2. **Database Configuration** (patra-registry)
3. **Local Configuration** (Nacos / application.yml)

### Configuration Hot Reload
- Support Nacos dynamic configuration refresh
- Configuration changes take effect automatically (no restart required)
