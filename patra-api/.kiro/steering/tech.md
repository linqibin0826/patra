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
