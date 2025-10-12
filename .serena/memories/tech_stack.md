# Tech Stack & Versions

- Language/Platform: Java 21, Maven (wrapper `./mvnw`)
- Spring: Spring Boot 3.2.4; Spring Cloud 2023.0.1; Spring Cloud Alibaba 2023.0.1.0
- Persistence: MyBatis-Plus 3.5.12; Flyway; MySQL 8.x; Redis 7.x
- Messaging/Events: RocketMQ 5.x (Outbox relay)
- Search: Elasticsearch 8.14.x
- Config/Discovery: Nacos 3.x
- Observability: SkyWalking 10.x; Micrometer; SLF4J
- Scheduling: XXL-Job 3.2.x
- Resilience: Resilience4j 2.2.x (planned for sync calls)
- Mapping/POJOs: MapStruct 1.6.x; Lombok 1.18.x

Local Infra via Docker Compose: `docker/compose/docker-compose.dev.yaml`.