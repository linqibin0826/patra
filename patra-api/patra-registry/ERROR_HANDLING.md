# Dictionary Error Handling Documentation

## Overview

This document describes the comprehensive error handling and logging implementation for the dictionary read pipeline in the patra-registry service. The implementation follows CQRS read-only patterns and provides structured error responses with appropriate HTTP status codes.

## Error Handling Architecture

### Exception Hierarchy

```
RuntimeException
├── DictionaryDomainException (Base domain exception)
│   ├── DictionaryNotFoundException (404 - Resource not found)
│   └── DictionaryValidationException (400 - Business rule violations)
└── DictionaryRepositoryException (500 - Infrastructure failures)
```

### Exception Types

#### 1. DictionaryDomainException
- **Package**: `com.patra.registry.domain.exception`
- **Purpose**: Base exception for domain-level business rule violations
- **HTTP Status**: 400 Bad Request
- **Context**: Includes typeCode and itemCode for debugging

#### 2. DictionaryNotFoundException
- **Package**: `com.patra.registry.domain.exception`
- **Purpose**: Thrown when requested dictionary resources don't exist
- **HTTP Status**: 404 Not Found
- **Usage**: Missing types, items, or disabled/deleted resources

#### 3. DictionaryValidationException
- **Package**: `com.patra.registry.domain.exception`
- **Purpose**: Validation failures with detailed error messages
- **HTTP Status**: 400 Bad Request
- **Features**: Supports multiple validation errors in a single exception

#### 4. DictionaryRepositoryException
- **Package**: `com.patra.registry.domain.exception`
- **Purpose**: Infrastructure-level failures (database, network, etc.)
- **HTTP Status**: 500 Internal Server Error
- **Context**: Includes operation name and full context for debugging

## Global Exception Handler

### DictionaryExceptionHandler
- **Package**: `com.patra.registry.adapter.rest.advice`
- **Annotation**: `@RestControllerAdvice`
- **Features**:
  - Structured error responses with consistent format
  - Trace ID extraction for request correlation
  - Appropriate logging levels for different error types
  - Context preservation for debugging

### Error Response Format

```json
{
  "timestamp": "2025-09-19T10:30:00",
  "status": 404,
  "error": "Not Found",
  "message": "Dictionary item not found: typeCode=USER_TYPE, itemCode=ADMIN",
  "path": "/_internal/dictionaries/types/USER_TYPE/items/ADMIN",
  "traceId": "trace-1726747800000",
  "typeCode": "USER_TYPE",
  "itemCode": "ADMIN",
  "validationErrors": ["Item is disabled", "Item is deleted"]
}
```

## Logging Strategy

### Log Levels

| Level | Usage | Examples |
|-------|-------|----------|
| **ERROR** | System failures, infrastructure issues | Database connection failures, unexpected exceptions |
| **WARN** | Business rule violations, data integrity issues | Validation failures, multiple default items |
| **INFO** | Important business operations, API operations | API entry/exit, health status changes |
| **DEBUG** | Detailed execution flow, internal state | Parameter values, query results |

### Structured Logging

All log messages follow consistent patterns:
- Parameterized logging: `log.info("Operation: param={}", value)`
- Context inclusion: typeCode, itemCode, operation name
- Correlation IDs for request tracing
- No sensitive data in log messages

### High-Frequency Operation Handling

To prevent log flooding, high-frequency operations use DEBUG level:
- `findItemByTypeAndCode`
- `validateReference`
- `existsByTypeCode`

## Error Handling Utilities

### DictionaryErrorHandler
- **Package**: `com.patra.registry.app.util`
- **Purpose**: Centralized error handling patterns
- **Features**:
  - Standardized exception wrapping
  - Parameter validation
  - Consistent logging patterns
  - Exception factory methods

### DictionaryLoggingConfig
- **Package**: `com.patra.registry.adapter.config`
- **Purpose**: Logging configuration and guidelines
- **Features**:
  - Centralized logging patterns
  - Log level recommendations
  - Structured message formatting

## Usage Examples

### Service Layer Error Handling

```java
@Slf4j
@Service
public class DictionaryQueryAppService {
    
    public Optional<DictionaryItemQuery> findItemByTypeAndCode(String typeCode, String itemCode) {
        log.debug("Finding dictionary item: typeCode={}, itemCode={}", typeCode, itemCode);
        
        try {
            // Validate parameters
            if (typeCode == null || typeCode.trim().isEmpty()) {
                throw new IllegalArgumentException("Dictionary type code cannot be null or empty");
            }
            
            // Perform operation
            Optional<DictionaryItem> result = dictionaryRepository.findItemByTypeAndCode(typeCode, itemCode);
            
            if (result.isEmpty()) {
                log.debug("Dictionary item not found: typeCode={}, itemCode={}", typeCode, itemCode);
                return Optional.empty();
            }
            
            log.debug("Successfully found dictionary item: typeCode={}, itemCode={}", typeCode, itemCode);
            return Optional.of(converter.toQuery(result.get()));
            
        } catch (Exception e) {
            log.error("Failed to find dictionary item: typeCode={}, itemCode={}, error={}", 
                     typeCode, itemCode, e.getMessage(), e);
            throw e; // Let global handler deal with it
        }
    }
}
```

### Repository Layer Error Handling

```java
@Slf4j
@Repository
public class DictionaryRepositoryMpImpl implements DictionaryRepository {
    
    @Override
    public Optional<DictionaryItem> findItemByTypeAndCode(String typeCode, String itemCode) {
        log.debug("Repository: Finding dictionary item: typeCode={}, itemCode={}", typeCode, itemCode);
        
        try {
            RegSysDictItemDO entity = itemMapper.selectByTypeAndItemCode(typeCode, itemCode);
            if (entity == null) {
                return Optional.empty();
            }
            
            DictionaryItem domainItem = entityConverter.toDomain(entity);
            return Optional.of(domainItem);
            
        } catch (Exception e) {
            log.error("Repository: Error finding dictionary item: typeCode={}, itemCode={}, error={}", 
                     typeCode, itemCode, e.getMessage(), e);
            throw new DictionaryRepositoryException(
                "Failed to find dictionary item", 
                "findItemByTypeAndCode", 
                typeCode, 
                itemCode, 
                e
            );
        }
    }
}
```

### Controller Layer Error Handling

```java
@Slf4j
@RestController
public class DictionaryApiImpl implements DictionaryHttpApi {
    
    @Override
    public DictionaryItemQuery getItemByTypeAndCode(String typeCode, String itemCode) {
        log.info("API: Getting dictionary item: typeCode={}, itemCode={}", typeCode, itemCode);
        
        try {
            Optional<DictionaryItemQuery> result = dictionaryQueryAppService.findItemByTypeAndCode(typeCode, itemCode);
            
            if (result.isEmpty()) {
                log.info("API: Dictionary item not found: typeCode={}, itemCode={}", typeCode, itemCode);
                return null; // Return null for 404 handling by Feign
            }
            
            log.info("API: Successfully returned dictionary item: typeCode={}, itemCode={}", typeCode, itemCode);
            return result.get();
            
        } catch (IllegalArgumentException e) {
            log.warn("API: Invalid parameters: typeCode={}, itemCode={}, error={}", 
                    typeCode, itemCode, e.getMessage());
            throw e; // Let exception handler deal with it
        } catch (Exception e) {
            log.error("API: Failed to get dictionary item: typeCode={}, itemCode={}, error={}", 
                     typeCode, itemCode, e.getMessage(), e);
            throw e; // Let exception handler deal with it
        }
    }
}
```

## Testing

### Unit Tests
- Exception handler behavior verification
- Error response structure validation
- HTTP status code correctness

### Integration Tests
- End-to-end error handling flow
- Structured error response format
- Trace ID propagation

## Monitoring and Observability

### Health Checks
- Dictionary system health status endpoint
- Integrity issue detection and reporting
- Performance metrics and error rates

### Trace Correlation
- Trace ID extraction from headers
- SkyWalking integration support
- Request correlation across services

## Best Practices

1. **Always use parameterized logging** to avoid string concatenation
2. **Include relevant context** (typeCode, itemCode) in all log messages
3. **Use appropriate log levels** based on severity and audience
4. **Avoid logging sensitive data** (PII, credentials, tokens)
5. **Handle high-frequency operations** with DEBUG level to prevent log flooding
6. **Preserve exception context** when wrapping or re-throwing exceptions
7. **Use structured error responses** for consistent API behavior
8. **Include trace IDs** for request correlation and debugging

## Configuration

### Log Level Configuration (application.yml)
```yaml
logging:
  level:
    com.patra.registry.domain: DEBUG
    com.patra.registry.app: INFO
    com.patra.registry.infra: INFO
    com.patra.registry.adapter: INFO
    com.patra.registry.adapter.rest.advice: WARN
```

### Exception Handler Configuration
The global exception handler is automatically registered via `@RestControllerAdvice` and handles all dictionary-related exceptions across the application.