# Technology Stack

## Core Technologies

- **Java**: Version 21
- **Spring Boot**: 3.2.4
- **Spring Cloud**: 2023.0.1
- **Spring Cloud Alibaba**: 2023.0.1.0 (Nacos, Sentinel, etc.)
- **Maven**: Multi-module project structure

## Key Dependencies

### Persistence & Data
- **MyBatis Plus**: 3.5.12 (ORM framework)
- **MySQL**: 8.0.36 (Primary database)
- **Redis**: 7.0.15 (Caching)
- **Elasticsearch**: 8.14.3 (Search and analytics)

### Development Tools
- **Lombok**: 1.18.38 (Code generation)
- **MapStruct**: 1.6.3 (Object mapping)
- **Hutool**: 5.8.22 (Utility library - required in domain layer)

### Monitoring & Observability
- **SkyWalking**: 10.2.0 (APM and distributed tracing)
- **OpenTelemetry**: Integrated via SkyWalking

### Service Discovery & Configuration
- **Nacos**: Service registry and configuration management

### Scheduling
- **XXL-Job**: 1.0.2 (Distributed task scheduling)

## Build System

### Maven Commands

```bash
# Clean and compile all modules
./mvnw clean compile

# Run tests
./mvnw test

# Package all modules
./mvnw clean package

# Install to local repository
./mvnw clean install

# Skip tests during build
./mvnw clean package -DskipTests

# Build specific module
./mvnw clean package -pl patra-registry

# Build module and its dependencies
./mvnw clean package -pl patra-registry -am
```

### Development Environment

```bash
# Start infrastructure services
cd docker/compose
docker-compose -f docker-compose.dev.yml up -d

# Check service health
docker-compose -f docker-compose.dev.yml ps

# View logs
docker-compose -f docker-compose.dev.yml logs -f [service-name]

# Stop services
docker-compose -f docker-compose.dev.yml down
```

## Code Generation

The project uses annotation processors for:
- **Lombok**: Automatic getter/setter/builder generation
- **MapStruct**: Type-safe object mapping between layers
- Both are configured in the parent POM with proper processor paths

## Dependency Management

- All versions managed in `patra-parent/pom.xml`
- Use BOM imports for Spring Boot, Spring Cloud, and Spring Cloud Alibaba
- Internal module versions use `${project.version}` property
- Annotation processors configured globally in parent POM