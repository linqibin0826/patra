# Code Style and Conventions

## Layer Structure (Hexagonal + DDD)
Each microservice follows this structure:
- **domain**: Pure Java, no framework dependencies, only depends on patra-common
- **app**: Application orchestration, transaction boundaries, depends on domain + patra-common + core starter
- **infra**: Infrastructure implementations (repositories, RPC), depends on domain + mybatis/feign starters
- **adapter**: Inbound adapters (REST, jobs, MQ listeners), depends on app + api + web starters
- **api**: External contracts (DTOs, interfaces), no framework dependencies
- **boot**: Executable entry point, wires everything together

## Dependency Rules (MUST FOLLOW)
```
adapter → app + api (+ web starters)
app → domain + patra-common + core starter
infra → domain + mybatis starter + core starter
domain → only patra-common (NO Spring/framework dependencies)
api → no framework dependencies
```

## Naming Conventions
- **Orchestrators**: `*Orchestrator` (application layer use case orchestration)
- **Commands**: `*Command` (command objects for use cases)
- **Implementations**: `*Impl` (interface implementations)
- **Ports**: `*Port` (domain port interfaces)
- **Repositories**: `*Repository` interface (domain), `*RepositoryImpl` (infra)
- **Mappers**: `*Mapper` (MyBatis mappers), `*MapStruct` (MapStruct converters)

## POJO Conventions
- **Prefer `record`**: For immutable/value objects
- **Use Lombok + class**: When mutability is needed
- **Never mix**: Don't use Lombok annotations inside records
- **Database JSON fields**: Use Jackson `JsonNode` or define POJOs in DOs, NOT `Map` or `String`

## Lombok Usage
- Use `@Data`, `@Getter`, `@Setter`, `@Builder`, etc. to avoid boilerplate
- Don't write manual getters/setters/toString/equals/hashCode
- Lombok is configured in `patra-parent` POM with annotation processors

## MapStruct
- Used for DO ↔ Domain/DTO conversions
- Configured with Lombok binding in annotation processor path
- Define mappers in infra layer for repository conversions

## Utility Reuse
- **DON'T reinvent the wheel**: Check Hutool and patra-common/starters first
- **Search before adding**: Use existing utilities whenever possible
- Common utilities: Hutool (cn.hutool.*), patra-common base classes

## Logging
- Use `@Slf4j` annotation
- **English only** for log messages
- **Parameterized logging**: `log.info("Processing plan {}", planId)`
- **Include trace context**: planId, sourceId, batchId, traceId
- **Never log sensitive data**: passwords, tokens, PII

## Error Handling
- Domain layer: Throw domain exceptions (e.g., `PlanValidationException`)
- Application layer: Catch domain exceptions, handle transaction semantics
- Adapter layer: Map exceptions to ProblemDetail via error resolution engine
- Use `HttpStdErrors.Group` for HTTP-aligned error codes (0xxx segment)
- Format: `<context-prefix>-<http-suffix>` (e.g., REG-0404, ING-1503)

## Comments
- **English only** for code comments and JavaDoc
- Document complex business logic, assumptions, and trade-offs
- Use JavaDoc for public APIs
- Avoid obvious comments

## Configuration
- **Never hardcode**: secrets, connection strings, variable configs
- Use **Nacos** or environment variables
- Configuration files: `application.yaml`, `application-local.yaml`
- Error config: separate files (e.g., `registry-error-config.yaml`)

## Code Quality
- **Small diffs**: Make incremental, focused changes
- **Compilation check**: Run `./mvnw -q -DskipTests compile` before submitting
- **Self-check**: Verify basic quality, necessary comments
- **Delegate reviews**: Use code-reviewer agent for thorough review