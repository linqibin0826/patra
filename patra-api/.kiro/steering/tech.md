# Technology Stack & Build System

## Build System
- **Maven**: Multi-module project with parent POM for dependency management
- **Java 21**: Latest LTS version with modern language features
- **Encoding**: UTF-8 throughout the project

## Core Framework Stack
- **Spring Boot 3.2.4**: Application framework and auto-configuration
- **Spring Cloud 2023.0.1**: Microservices infrastructure
- **Spring Cloud Alibaba 2023.0.1.0**: Nacos, Sentinel integration

## Data & Persistence
- **MyBatis-Plus 3.5.12**: ORM with enhanced MyBatis features
- **MySQL 8.0**: Primary database
- **Redis 7.0**: Caching and session storage
- **Elasticsearch 8.14**: Search and analytics engine

## Infrastructure & Monitoring
- **Nacos**: Service discovery and configuration management
- **SkyWalking 10.2**: APM and distributed tracing
- **XXL-Job 3.2.0**: Distributed task scheduling
- **Docker Compose**: Local development environment

## Code Generation & Mapping
- **Lombok 1.18.38**: Boilerplate code reduction
- **MapStruct 1.6.3**: Type-safe bean mapping
- **Hutool 5.8.22**: Java utility library (domain layer only)

## Custom Framework Components
- **patra-spring-boot-starter-core**: Core utilities and configurations
- **patra-spring-boot-starter-web**: Web layer enhancements
- **patra-spring-boot-starter-mybatis**: Database layer with BaseEntity
- **patra-spring-cloud-starter-feign**: Service-to-service communication
- **patra-expr-kernel**: Expression evaluation engine

## Common Build Commands

### Development Setup
```bash
# Start infrastructure services
cd docker/compose
docker-compose -f docker-compose.dev.yml up -d

# Build entire project
mvn clean compile

# Build specific module
cd patra-registry
mvn clean compile

# Run tests
mvn test

# Package applications
mvn clean package -DskipTests
```

### Service Ports (Development)
- **MySQL**: 13306
- **Redis**: 16379
- **Elasticsearch**: 9200
- **Nacos**: 8848 (console: 4000)
- **SkyWalking UI**: 8088
- **XXL-Job Admin**: 7070

### Maven Profiles & Properties
- All modules inherit from `patra-parent`
- Version managed centrally: `0.1.0-SNAPSHOT`
- Annotation processors configured for Lombok + MapStruct
- Compiler release level: Java 21

## Development Workflow & Standards

### Local Infrastructure Setup
```bash
# Start all required services
cd docker/compose
docker-compose -f docker-compose.dev.yml up -d

# Verify services are healthy
docker-compose ps
```

### Key Configuration Points

#### Database & Persistence
- **MyBatis-Plus**: Enhanced MyBatis with BaseDO pattern
- **Transaction Management**: Orchestrated at app layer
- **Entity Mapping**: MapStruct for entity ↔ domain conversion
- **JSON Fields**: Use Jackson JsonNode for database JSON columns

#### Observability & Monitoring
- **SkyWalking**: APM with trace propagation (traceId/spanId)
- **Nacos**: Service discovery and configuration management
- **XXL-Job**: Distributed scheduling with idempotency requirements

#### Security Standards
- No hardcoded credentials (use env vars/config center)
- Parameterized SQL only (prevent injection)
- PII/sensitive data minimization and masking in logs

#### Performance Guidelines
- Avoid N+1 queries, use batch processing
- Implement pagination and async processing
- Memory-friendly and streaming approaches preferred
- Add circuit breakers and rate limiting where needed