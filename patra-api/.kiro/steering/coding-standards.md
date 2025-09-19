# Coding Standards & Conventions

## Core Principles
- **When unclear → Ask first**: Ask questions when requirements are unclear
- **Reuse over reinvent**: Reuse existing capabilities before creating new ones
- **Respect boundaries**: Respect layer boundaries and dependency directions

## Data Object Rules

### Entity & Enum Conventions
- **No Java enums in DO classes**: Use string/int fields instead of Java enums in database entities
- **JSON fields use Jackson JsonNode**: For database columns storing JSON data
- **Entity naming**: Use `{Service}{Table}DO` pattern (e.g., `RegProvenanceDO`)

### POJO Design Patterns
- **Immutable objects use record**: For value objects and immutable data structures
- **Mutable objects use Lombok + class**: When mutability is needed, use Lombok annotations
- **Avoid boilerplate code**: Never write manual getters/setters/toString/equals/hashCode

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
- **No hardcoded credentials**: Never hardcode credentials, API keys, or sensitive data
- **Environment variables/config center**: Use environment variables or configuration center injection
- **Parameterized SQL only**: All SQL must be parameterized to prevent injection attacks
- **Log data masking**: Avoid logging sensitive fields, implement data masking

### Data Processing Standards
- **Idempotent design**: All collection/parsing/cleaning processes must be re-entrant
- **Deduplication strategy**: Implement idempotency keys or deduplication strategies for critical steps
- **Transaction boundaries**: Orchestrate transactions at app layer, avoid framework dependencies in domain

## Performance & Scalability

### Query Optimization
- **Avoid N+1 queries**: Use batch processing and proper join strategies
- **Pagination handling**: Implement pagination for large datasets
- **Async processing**: Use async processing where appropriate
- **Streaming processing**: Prefer memory-friendly streaming approaches

### Infrastructure Considerations
- **Rate limiting/circuit breakers**: Add rate limiting and circuit breakers for external calls
- **Index strategy**: Plan database indexes and routing keys before implementation
- **Caching strategy**: Use Redis appropriately for session and frequently accessed data

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
- Follow "nearest wins" principle - local AGENTS.md rules override global ones