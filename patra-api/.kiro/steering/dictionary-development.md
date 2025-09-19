---
inclusion: manual
---

# Dictionary Development Standards

This steering document contains specific standards and requirements for dictionary-related development in the patra-registry service.

## Dictionary-Specific Architecture

### CQRS Read Pipeline Requirements
- **Read-only operations**: Dictionary pipeline is strictly CQRS Query side - no write operations
- **Contract module integration**: All Query/View objects must be placed in contract module for sharing
- **Subsystem integration**: Follow ProvenanceClient pattern for Feign client integration
- **Internal API pattern**: Use `/_internal/dictionaries/**` endpoints for subsystem access

### Module Structure
```
patra-registry-contract/    # Query/View objects shared with app module
patra-registry-api/         # Feign clients for subsystem integration  
patra-registry-app/         # Application services using contract Query objects
patra-registry-infra/       # Repository implementations with entity mapping
patra-registry-adapter/     # REST controllers implementing HTTP API contracts
```

## Dictionary Domain Rules

### Business Logic Constraints
- **Unique defaults**: Each dictionary type can have at most one default item
- **Soft delete**: Items are soft-deleted (deleted=1) rather than physically removed
- **Enable/disable**: Items can be disabled (enabled=0) without deletion
- **Stable keys**: `item_code` values must remain stable once created
- **Type safety**: Application layer validates item_code belongs to correct type_code

### Data Access Patterns
- **Optimized views**: Use `v_sys_dict_item_enabled` view for enabled item queries
- **Batch operations**: Prefer batch validation over individual item validation
- **Alias resolution**: Support external system integration through alias mappings
- **Health monitoring**: Provide system health status for operational monitoring

## Contract Objects Design

### Query Objects (Shared between App and Contract)
```java
/**
 * Dictionary item query object for CQRS read operations.
 * Shared between app module and contract module for consistent data structure.
 * 
 * @param typeCode dictionary type code
 * @param itemCode dictionary item code  
 * @param displayName human-readable display name
 * @param description detailed description
 * @param isDefault whether this is the default item for its type
 * @param sortOrder numeric sort order for display
 * @param enabled whether the item is currently enabled
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryItemQuery(
    String typeCode,
    String itemCode, 
    String displayName,
    String description,
    boolean isDefault,
    int sortOrder,
    boolean enabled
);
```

### View Objects (Contract Module for External Consumption)
```java
/**
 * Dictionary item view object for external subsystem consumption.
 * Used in contract module for clean API boundaries.
 * 
 * @param typeCode dictionary type code
 * @param itemCode dictionary item code
 * @param displayName human-readable display name
 * @param description detailed description
 * @param isDefault whether this is the default item
 * @param sortOrder numeric sort order
 * @author linqibin
 * @since 0.1.0
 */
public record DictionaryItemView(
    String typeCode,
    String itemCode,
    String displayName, 
    String description,
    boolean isDefault,
    int sortOrder
);
```

## Subsystem Integration Pattern

### Feign Client Implementation
```java
/**
 * Feign client for dictionary service integration.
 * Subsystems inject this client to access dictionary capabilities via service discovery.
 * Inherits all methods from DictionaryHttpApi for consistent API access.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@FeignClient(
    name = "patra-registry",
    contextId = "dictionaryClient"
)
public interface DictionaryClient extends DictionaryHttpApi {
    // Inherits all methods from DictionaryHttpApi
    // Provides service discovery and load balancing through Spring Cloud
}
```

### Subsystem Adapter Pattern
```java
/**
 * Example subsystem adapter for dictionary integration.
 * Demonstrates proper usage of DictionaryClient and conversion to subsystem models.
 * 
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Component
public class PatraRegistryDictionaryPortImpl implements PatraRegistryDictionaryPort {
    
    private final DictionaryClient dictionaryClient;
    private final DictionaryAclConverter converter;
    
    @Override
    public EndpointConfigValidationResult validateEndpointConfig(EndpointConfig config) {
        log.info("Validating endpoint config: httpMethod={}, usage={}", 
                config.getHttpMethodCode(), config.getEndpointUsageCode());
        
        List<DictionaryReference> references = List.of(
            new DictionaryReference("http_method", config.getHttpMethodCode()),
            new DictionaryReference("endpoint_usage", config.getEndpointUsageCode())
        );
        
        List<DictionaryValidationQuery> results = dictionaryClient.validateReferences(references);
        return converter.toValidationResult(results);
    }
}
```

## Dictionary-Specific Logging

### Business Operation Logging
- **Validation operations**: INFO level for validation requests, WARN for validation failures
- **Health checks**: INFO level for health status requests, WARN for integrity issues  
- **Item lookups**: DEBUG level for individual lookups, INFO for batch operations
- **Alias resolution**: DEBUG level for alias mapping operations

### Error Scenarios
- **Item not found**: DEBUG level (expected business scenario)
- **Type not found**: DEBUG level (expected business scenario)
- **Multiple defaults**: WARN level (data integrity issue)
- **Database errors**: ERROR level (infrastructure failure)
- **Validation failures**: WARN level (business rule violation)

### Structured Logging Context
```java
// Include relevant dictionary context in all log messages
log.info("Dictionary validation: typeCode={}, itemCode={}, result={}", typeCode, itemCode, isValid);
log.warn("Multiple default items found: typeCode={}, count={}", typeCode, defaultCount);
log.error("Dictionary repository error: operation={}, typeCode={}, error={}", operation, typeCode, e.getMessage(), e);
```

## Performance Considerations

### Query Optimization
- **View usage**: Always use `v_sys_dict_item_enabled` for enabled item queries
- **Batch operations**: Implement batch validation to reduce database round trips
- **Caching strategy**: Consider caching frequently accessed dictionary data
- **Index optimization**: Ensure proper indexes on typeCode and itemCode columns

### Memory Management
- **Streaming results**: Use streaming for large result sets
- **Pagination**: Implement pagination for type listings and bulk operations
- **Connection pooling**: Rely on MyBatis-Plus connection pooling configuration
- **Resource cleanup**: Ensure proper resource cleanup in repository implementations

## Testing Requirements

### Unit Testing
- **Domain logic**: Test all business rules and validation logic
- **Query conversion**: Test MapStruct converters with edge cases
- **Logging verification**: Verify appropriate log levels and messages
- **Exception handling**: Test all error scenarios and exception propagation

### Integration Testing  
- **Database integration**: Test with real database using TestContainers
- **Feign client testing**: Test client integration with WireMock
- **API contract testing**: Verify HTTP API contract compliance
- **Health endpoint testing**: Test system health monitoring endpoints

### Test Data Management
- **Seed data**: Use provided dictionary seed data for consistent testing
- **Test isolation**: Ensure tests don't interfere with each other
- **Edge cases**: Test boundary conditions and data integrity scenarios
- **Performance testing**: Verify query performance with realistic data volumes