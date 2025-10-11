# patra-spring-boot-starter-mybatis

This starter provides foundational MyBatis-Plus configurations, a plugin chain, and error mappings aligned with Papertrace conventions.

## 1. Module's Role
- **Purpose**: Standardizes pagination, optimistic locking, protection against full table updates/deletes, audit field population, and mapping of database exceptions to platform-specific error codes.
- **Primary Consumers**: `patra-*-infra` layers and any microservices requiring database access.
- **Architectural Boundary**: This starter offers configuration and interceptors only. Business-specific SQL and Mapper definitions remain the responsibility of their respective modules.

## 2. Core Capabilities
- **Plugin Chain**: Includes plugins for pagination, optimistic locking, prevention of full table updates/deletes, and automatic field population.
- **Error Mapping**: The `DataLayerErrorMappingContributor` translates driver exceptions into standardized error codes.
- **Base Entity**: `BaseDO` provides audit fields, logical deletion, and an optimistic locking version number.
- **JSON Field Handling**: Offers TypeHandlers for `JsonNode` and `Map<String, Object>`.
- **Mapper Scanning Convention**: Automatically scans for Mappers within the `infra` package.

This README serves as the definitive documentation for this module. For comparison with other starters, please refer to their respective READMEs (`patra-spring-boot-starter-*`, `patra-spring-cloud-starter-feign`).

## 3. Layered Structure and Dependencies
- **Core Packages**: `config` (auto-configuration), `error`, `handler`.
- **Dependencies**: `patra-common`, MyBatis-Plus, Spring Boot DataSource, Jackson.

## 4. Usage and Configuration
- **Maven Dependency**:
  ```xml
  <dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-mybatis</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </dependency>
  ```
- **Auto-configuration Prerequisites**: The project must have a declared data source (e.g., Hikari) and have MyBatis Mapper scanning enabled.
- **Tunable Properties (Excerpt)**: Maximum page size for pagination, slow query logging, and toggles for auto-filling (see in-depth documentation for details).

## 5. Observability and Operations
- Database errors are converted to unified error codes by the core starter, which can be monitored and aggregated on dashboards.
- It is recommended to enable MyBatis-Plus SQL logging and a slow query threshold (in conjunction with Micrometer).
- Be aware that the plugin for preventing full table operations may intercept legitimate batch processes. It can be disabled for migration scenarios if necessary.

## 6. Testing Strategy
- Use H2 or Testcontainers to verify Mapper and plugin behavior.
- Cover scenarios for auto-filling, optimistic locking, and protection against full table updates.
- Simulate unique key conflicts and foreign key constraints to confirm correct error code mapping.

## 7. Roadmap and Risks
| Feature | Status | Risks/Notes |
|---|---|---|
| Configurable Plugin Chain | Planned | Allow services to disable specific plugins on demand, requires handling order dependencies. |
| SQL Observability Metrics | Planned | Integrate Micrometer to record execution time and row counts. |
| JSON Schema Validation | Planned | Add optional validation for JSON fields before writing to the database. |

**Risks**: Conflicts between auto-filled fields and business logic, serialization failures due to unregistered TypeHandlers, and accidental blocking of batch operations by the full-table-update prevention plugin.

## 8. References
- **Other Starters**: `patra-spring-boot-starter-core/README.md`, `patra-spring-boot-starter-web/README.md`, `patra-spring-cloud-starter-feign/README.md`, `patra-spring-boot-starter-expr/README.md`
- **Error Specification**: `docs/standards/platform-error-handling.md`
- **Registry/Ingest Infra Implementation**: See the `infra` package in the respective modules.