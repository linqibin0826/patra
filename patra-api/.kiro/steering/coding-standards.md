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

## CQRS Design Principles

### Read-Only Operations (Query Side)
- **Strict CQRS separation**: Clearly separate read (Query) and write (Command) operations
- **Read-only services**: Query services must not contain any command operations (CREATE, UPDATE, DELETE)
- **Query optimization**: All read components optimized for performance and data retrieval
- **Eventual consistency**: Reads from optimized views and cached data structures

### Command-Query Separation
- **No mixed operations**: Services should be either pure Query or pure Command, never mixed
- **Clear naming**: Use `Query` suffix for read operations, `Command` suffix for write operations
- **Separate models**: Use different models for read (Query/View objects) and write (Command objects)

## Contract Module Architecture

### Shared Query/View Objects
- **Contract module placement**: All Query and View objects shared between app and contract modules must be placed in contract module
- **Query objects**: Used by both app module and contract module for consistent data structures
- **View objects**: Used by contract module for external subsystem consumption
- **API integration**: Subsystems interact through API module → Contract module → App module

### Subsystem Integration Pattern
- **Three-layer integration**: API module (Feign clients) → Contract module (Query/View objects) → Subsystem adapters
- **Feign client pattern**: Follow ProvenanceClient pattern for subsystem integration
- **Internal API endpoints**: Use `/_internal/{service}/**` pattern for subsystem access
- **Service discovery**: Configure Feign clients with proper service names and context IDs

## JavaDoc Documentation Standards

### Class-Level Documentation
- **Required annotations**: Every class must have `@author linqibin @since 0.1.0`
- **Class description**: Comprehensive description of class purpose and responsibilities
- **Architecture notes**: Include CQRS role (Query/Command) and layer information
- **Usage examples**: Provide usage examples for complex classes

### Method Documentation
- **Complete JavaDoc**: All public methods must have comprehensive JavaDoc
- **Parameter documentation**: Use `@param` for all method parameters with detailed descriptions
- **Return documentation**: Use `@return` for all return values with type and content description
- **Exception documentation**: Use `@throws` for all checked and important unchecked exceptions
- **Business context**: Include business logic context and constraints

### Field Documentation
- **Field descriptions**: All fields must have detailed comments explaining their purpose
- **Relationship documentation**: Document relationships between fields and external systems
- **Constraint documentation**: Document validation rules and business constraints
- **Lifecycle information**: Document when and how fields are populated/updated

### Example JavaDoc Format
```java
/**
 * Dictionary query application service for CQRS read operations.
 * Orchestrates dictionary query use cases and uses contract Query objects for consistency.
 * This service is strictly read-only and does not support any command operations.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Service
public class DictionaryQueryAppService {
    
    /** Dictionary repository for data access */
    private final DictionaryRepository dictionaryRepository;
    
    /**
     * Find dictionary item by type code and item code.
     * 
     * @param typeCode the dictionary type code, must not be null or empty
     * @param itemCode the dictionary item code, must not be null or empty
     * @return Optional containing the dictionary item query object if found, empty otherwise
     * @throws IllegalArgumentException if typeCode or itemCode is null or empty
     * @throws DictionaryRepositoryException if database access fails
     */
    public Optional<DictionaryItemQuery> findItemByTypeAndCode(String typeCode, String itemCode) {
        // Implementation...
    }
}
```

## Logging Standards

### @Slf4j Usage
- **Required annotation**: All classes requiring logging must use `@Slf4j` annotation
- **Consistent approach**: Use SLF4J API consistently across all components
- **Logger naming**: Rely on @Slf4j automatic logger naming (class-based)

### Log Level Guidelines
- **ERROR**: System errors, exceptions affecting functionality, database connection failures, infrastructure issues
- **WARN**: Business rule violations, validation failures, data integrity issues, deprecated API usage
- **INFO**: Important business operations, API entry/exit points, significant state changes, health status
- **DEBUG**: Detailed execution flow, parameter values, internal state for troubleshooting, performance metrics

### Structured Logging
- **Parameterized logging**: Always use parameterized logging: `log.info("Operation: param={}", value)`
- **Context information**: Include relevant identifiers (typeCode, itemCode, userId) for correlation
- **Consistent format**: Use consistent parameter naming and formatting across components
- **Performance consideration**: Use `log.isDebugEnabled()` for expensive string operations in DEBUG logs

### Logging Best Practices
- **No log flooding**: Avoid logging in tight loops or high-frequency operations
- **No sensitive data**: Never log passwords, tokens, PII, or other sensitive information
- **Exception logging**: Always log exceptions with full stack trace using `log.error("message", exception)`
- **Business context**: Include sufficient business context for troubleshooting
- **Correlation IDs**: Use correlation IDs for tracing requests across services

### Logging Examples
```java
@Slf4j
@Service
public class DictionaryQueryAppService {
    
    public Optional<DictionaryItemQuery> findItemByTypeAndCode(String typeCode, String itemCode) {
        log.debug("Finding dictionary item: typeCode={}, itemCode={}", typeCode, itemCode);
        
        try {
            Optional<DictionaryItem> result = dictionaryRepository.findItemByTypeAndCode(typeCode, itemCode);
            
            if (result.isEmpty()) {
                log.debug("Dictionary item not found: typeCode={}, itemCode={}", typeCode, itemCode);
            } else {
                log.debug("Successfully found dictionary item: typeCode={}, itemCode={}", typeCode, itemCode);
            }
            
            return result.map(dictionaryQueryConverter::toQuery);
            
        } catch (Exception e) {
            log.error("Failed to find dictionary item: typeCode={}, itemCode={}, error={}", 
                     typeCode, itemCode, e.getMessage(), e);
            throw e;
        }
    }
}
```

## Documentation & Maintenance

### Code Documentation
- Document assumptions and trade-offs explicitly
- Prefer clear, short, maintainable implementations
- Add comments for complex business logic
- Follow JavaDoc standards for all public APIs

### Consistency Guidelines
- Changes to common libraries (`patra-common`, starters, `expr-kernel`) must be conservative
- Prioritize stability and backward compatibility
- Follow "nearest wins" principle - local AGENTS.md rules override global ones
- Maintain consistent logging and documentation patterns across all modules