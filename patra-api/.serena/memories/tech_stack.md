# Technology Stack

## Language & Build
- **Java**: 21 (required)
- **Build Tool**: Maven 3.x (use `./mvnw` wrapper)
- **Encoding**: UTF-8

## Core Frameworks
- **Spring Boot**: 3.2.4
- **Spring Cloud**: 2023.0.1
- **Spring Cloud Alibaba**: 2023.0.1.0

## Data Persistence
- **MyBatis-Plus**: 3.5.12 (ORM layer)
- **MySQL**: 8.0 (primary storage)
- **Redis**: 7.0 (cache, planned)
- **Elasticsearch**: 8.14 (literature indexing, future)

## Infrastructure
- **Nacos**: Service registry + configuration management
- **SkyWalking**: 10.2 APM and distributed tracing
- **XXL-Job**: 3.2.0 Job scheduling
- **Docker Compose**: Local infrastructure orchestration

## Tools & Utilities
- **Lombok**: 1.18.38 (code generation)
- **MapStruct**: 1.6.3 (object mapping)
- **Hutool**: 5.8.22 (common utilities)

## Testing
- **JUnit**: 5 (Jupiter)
- **AssertJ**: Fluent assertions
- **Mockito**: Mocking framework
- **Testcontainers**: Integration testing (planned)

## Custom Components
- **patra-spring-boot-starter-core**: Error resolution engine, tracing, metrics
- **patra-spring-boot-starter-web**: ProblemDetail adapter, global exception handler
- **patra-spring-boot-starter-mybatis**: MyBatis-Plus configuration, pagination
- **patra-spring-boot-starter-provenance**: Provenance context management
- **patra-spring-cloud-starter-feign**: ProblemDetail error decoder, trace propagation
- **patra-expr-kernel**: Expression AST and normalization engine