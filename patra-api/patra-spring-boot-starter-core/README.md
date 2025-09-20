# Patra Spring Boot Starter Core

Core error handling infrastructure for the Patra microservices platform. This starter provides foundational error handling capabilities, configuration management, and SPI interfaces for customization.

## Features

- **Zero-configuration error handling** with sensible defaults
- **Error resolution algorithm** with configurable strategies
- **SPI interfaces** for customization and extension
- **Distributed tracing integration** with automatic trace ID propagation
- **Performance optimized** with class-level caching and cause chain traversal

## Quick Start

### 1. Add Dependency

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-core</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. Configure Context Prefix

```yaml
patra:
  error:
    context-prefix: REG  # Required: Your service's error code prefix
```

That's it! The starter will auto-configure all necessary components.

## Configuration Properties

### Core Error Properties (`patra.error`)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable/disable error handling |
| `context-prefix` | String | **Required** | Error code prefix (e.g., REG, ORD, INV) |
| `map-status.strategy` | String | `suffix-heuristic` | HTTP status mapping strategy |

### Tracing Properties (`patra.tracing`)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `header-names` | List<String> | `[traceId, X-B3-TraceId, traceparent]` | Trace ID header names to check |

### Example Configuration

```yaml
patra:
  error:
    enabled: true
    context-prefix: REG
    map-status:
      strategy: suffix-heuristic
  tracing:
    header-names:
      - traceId
      - X-B3-TraceId
      - traceparent
      - X-Trace-Id
```

## SPI Interfaces

The core starter provides several SPI interfaces for customization:

### StatusMappingStrategy

Maps error codes to HTTP status codes:

```java
@Component
public class CustomStatusMappingStrategy implements StatusMappingStrategy {
    
    @Override
    public int mapToHttpStatus(ErrorCodeLike errorCode, Throwable exception) {
        String code = errorCode.code();
        
        // Custom mapping logic
        if (code.endsWith("-QUOTA")) {
            return 429; // Too Many Requests
        }
        
        // Fallback to default strategy
        return 422;
    }
}
```

### ErrorMappingContributor

Provides fine-grained exception to error code mapping:

```java
@Component
public class CustomErrorMappingContributor implements ErrorMappingContributor {
    
    @Override
    public Optional<ErrorCodeLike> mapException(Throwable exception) {
        if (exception instanceof DataIntegrityViolationException) {
            return Optional.of(MyErrorCode.DATA_INTEGRITY_VIOLATION);
        }
        
        return Optional.empty();
    }
}
```

### ProblemFieldContributor

Adds custom fields to ProblemDetail responses:

```java
@Component
public class CustomFieldContributor implements ProblemFieldContributor {
    
    @Override
    public void contribute(Map<String, Object> fields, Throwable exception) {
        fields.put("serviceVersion", "1.0.0");
        fields.put("environment", environment.getActiveProfiles()[0]);
    }
}
```

### TraceProvider

Custom trace ID provider:

```java
@Component
public class CustomTraceProvider implements TraceProvider {
    
    @Override
    public Optional<String> getCurrentTraceId() {
        // Custom trace ID extraction logic
        return Optional.ofNullable(MyTracingContext.getCurrentTraceId());
    }
}
```

## Error Resolution Algorithm

The core starter implements a deterministic error resolution algorithm:

1. **ApplicationException**: Direct error code extraction
2. **ErrorMappingContributor**: Explicit exception mappings
3. **HasErrorTraits**: Semantic trait-based mapping
4. **Naming Convention**: Heuristic-based mapping (*NotFound→404, *Conflict→409)
5. **Fallback**: Default error codes (422 for client errors, 500 for server errors)

### Performance Features

- **Class-level caching**: Resolution results are cached by exception class
- **Cause chain traversal**: Handles wrapped exceptions (max 10 levels)
- **Lazy initialization**: Components are created only when needed

## Integration with Other Starters

This core starter is designed to work with:

- **patra-spring-boot-starter-web**: REST API error handling
- **patra-spring-cloud-starter-feign**: Feign client error decoding
- **patra-spring-boot-starter-mybatis**: Database layer error mapping

## Testing

### Unit Testing

```java
@SpringBootTest
class ErrorResolutionServiceTest {
    
    @Autowired
    private ErrorResolutionService errorResolutionService;
    
    @Test
    void shouldResolveApplicationException() {
        ApplicationException ex = new ApplicationException(
            MyErrorCode.VALIDATION_FAILED, 
            "Validation failed"
        );
        
        ErrorResolution resolution = errorResolutionService.resolve(ex);
        
        assertThat(resolution.errorCode()).isEqualTo(MyErrorCode.VALIDATION_FAILED);
        assertThat(resolution.httpStatus()).isEqualTo(422);
    }
}
```

### Integration Testing

```java
@SpringBootTest
@TestPropertySource(properties = {
    "patra.error.context-prefix=TEST",
    "patra.error.enabled=true"
})
class CoreErrorAutoConfigurationTest {
    
    @Autowired
    private ErrorResolutionService errorResolutionService;
    
    @Test
    void shouldAutoConfigureErrorResolutionService() {
        assertThat(errorResolutionService).isNotNull();
    }
}
```

## Troubleshooting

### Common Issues

1. **Missing context-prefix**: Ensure `patra.error.context-prefix` is configured
2. **Custom beans not working**: Check component scanning includes your custom SPI implementations
3. **Performance issues**: Verify error resolution caching is working properly

### Debug Logging

Enable debug logging to see error resolution details:

```yaml
logging:
  level:
    com.patra.starter.core.error: DEBUG
```

## Migration from Manual Error Handling

See the [Migration Guide](../docs/MIGRATION_GUIDE.md) for step-by-step instructions on migrating from manual exception handling to the Patra Error Handling System.