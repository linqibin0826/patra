# Coding Standards & Conventions

## Core Principles
- **不明确 → 先问**: Ask questions when requirements are unclear
- **能复用 → 不重造**: Reuse existing capabilities before creating new ones
- **不越层 → 不破边界**: Respect layer boundaries and dependency directions

## Data Object Rules

### Entity & Enum Conventions
- **DO中不要使用Java enum**: Use string/int fields instead of Java enums in database entities
- **JSON字段统一使用Jackson JsonNode**: For database columns storing JSON data
- **实体命名**: Use `{Service}{Table}DO` pattern (e.g., `RegProvenanceDO`)

### POJO Design Patterns
- **不可变对象优先使用record**: For value objects and immutable data structures
- **可变对象使用Lombok + class**: When mutability is needed, use Lombok annotations
- **避免手写样板代码**: Never write manual getters/setters/toString/equals/hashCode

### Lombok Usage
- Use `@Data` or combination annotations (`@Getter`/`@Setter`/`@ToString`)
- Avoid Lombok in `record` types (unnecessary)
- Choose appropriate annotations to avoid over-annotation

## Tool & Library Priority

### Utility Selection Order
1. **Hutool**: First choice for common utilities (approved for domain layer)
2. **patra-common**: Project-specific shared utilities
3. **patra-*-starters**: Framework-specific capabilities
4. **New utilities**: Only create if none of the above provide the functionality

### Framework Integration
- **MapStruct**: For all entity ↔ domain object mapping
- **MyBatis-Plus**: Database access with BaseDO inheritance
- **Jackson**: JSON processing, especially JsonNode for database JSON fields

## Security & Data Handling

### Security Requirements
- **严禁硬编码凭据**: Never hardcode credentials, API keys, or sensitive data
- **环境变量/配置中心**: Use environment variables or configuration center injection
- **SQL全面参数化**: All SQL must be parameterized to prevent injection attacks
- **日志脱敏**: Avoid logging sensitive fields, implement data masking

### Data Processing Standards
- **幂等设计**: All collection/parsing/cleaning processes must be re-entrant
- **去重策略**: Implement idempotency keys or deduplication strategies for critical steps
- **事务边界**: Orchestrate transactions at app layer, avoid framework dependencies in domain

## Performance & Scalability

### Query Optimization
- **避免N+1查询**: Use batch processing and proper join strategies
- **分页处理**: Implement pagination for large datasets
- **异步处理**: Use async processing where appropriate
- **流式处理**: Prefer memory-friendly streaming approaches

### Infrastructure Considerations
- **限流/熔断**: Add rate limiting and circuit breakers for external calls
- **索引策略**: Plan database indexes and routing keys before implementation
- **缓存策略**: Use Redis appropriately for session and frequently accessed data

## Code Organization

### Package Structure Compliance
- Follow the established package structure for each layer
- Respect dependency direction rules strictly
- Keep layer boundaries clean and well-defined

### Naming Conventions
- **Controllers**: `{Resource}Controller`
- **Services**: `{Aggregate}AppService`
- **Repositories**: `{Aggregate}Repository` (interface), `{Aggregate}RepositoryMpImpl` (implementation)
- **Mappers**: `{Entity}Mapper` (MyBatis), `{Entity}Converter` (MapStruct)

## Documentation & Maintenance

### Code Documentation
- Document assumptions and trade-offs explicitly
- Prefer clear, short, maintainable implementations
- Add comments for complex business logic

### Consistency Guidelines
- Changes to common libraries (`patra-common`, starters, `expr-kernel`) must be conservative
- Prioritize stability and backward compatibility
- Follow "就近优先" principle - local AGENTS.md rules override global ones