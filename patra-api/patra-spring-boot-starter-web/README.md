# Patra Spring Boot Starter Web

Web error handling starter that automatically converts all exceptions to RFC 7807 ProblemDetail responses. Provides global exception handling for REST APIs with zero configuration.

## Features

- **Automatic ProblemDetail conversion** for all exceptions
- **RFC 7807 compliance** with standard and extension fields
- **Validation error formatting** with sensitive data masking
- **Proxy-aware path extraction** for load balancer environments
- **Data layer exception handling** (DuplicateKey, DataIntegrity, OptimisticLocking)
- **Sensitive data masking** in error messages and validation errors

## Quick Start

### 1. Add Dependencies

```xml
<dependencies>
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-boot-starter-core</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-boot-starter-web</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

### 2. Configure Properties

```yaml
patra:
  error:
    context-prefix: REG  # Required in core starter
  web:
    problem:
      enabled: true
      type-base-url: "https://errors.example.com/"
      include-stack: false
```

### 3. Use in Controllers

```java
@RestController
@RequestMapping("/api/registry")
public class NamespaceController {
    
    @GetMapping("/namespaces/{id}")
    public NamespaceDto getNamespace(@PathVariable String id) {
        // Just throw domain exceptions - they'll be handled automatically
        throw new NamespaceNotFoundException(id);
    }
    
    @PostMapping("/namespaces")
    public NamespaceDto createNamespace(@Valid @RequestBody CreateNamespaceRequest request) {
        // Validation errors are automatically formatted
        // Domain exceptions are automatically converted to ProblemDetail
        return namespaceService.create(request);
    }
}
```

## Configuration Properties

### Web Error Properties (`patra.web.problem`)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable/disable web error handling |
| `type-base-url` | String | `https://errors.example.com/` | Base URL for ProblemDetail type field |
| `include-stack` | boolean | `false` | Include stack trace in responses (dev only) |

### Example Configuration

```yaml
patra:
  web:
    problem:
      enabled: true
      type-base-url: "https://docs.mycompany.com/errors/"
      include-stack: false  # Never enable in production
```

## ProblemDetail Response Format

### Standard Response

```json
{
  "type": "https://errors.example.com/reg-1001",
  "title": "REG-1001",
  "status": 404,
  "detail": "Namespace not found: test-namespace",
  "code": "REG-1001",
  "traceId": "abc123def456",
  "path": "/api/registry/namespaces/test-namespace",
  "timestamp": "2025-09-20T10:30:00Z"
}
```

### Validation Error Response

```json
{
  "type": "https://errors.example.com/reg-0422",
  "title": "REG-0422",
  "status": 422,
  "detail": "Validation failed",
  "code": "REG-0422",
  "traceId": "abc123def456",
  "path": "/api/registry/namespaces",
  "timestamp": "2025-09-20T10:30:00Z",
  "errors": [
    {
      "field": "name",
      "rejectedValue": "***",
      "message": "Name must not be empty"
    },
    {
      "field": "description",
      "rejectedValue": null,
      "message": "Description is required"
    }
  ]
}
```

## Handled Exception Types

### Domain Exceptions

All domain exceptions extending `DomainException` are automatically handled:

```java
// Automatically mapped to 404
public class NamespaceNotFoundException extends RegistryNotFound {
    public NamespaceNotFoundException(String namespaceId) {
        super("Namespace not found: " + namespaceId);
    }
}

// Automatically mapped to 409
public class NamespaceAlreadyExists extends RegistryConflict {
    public NamespaceAlreadyExists(String namespaceId) {
        super("Namespace already exists: " + namespaceId);
    }
}
```

### Application Exceptions

Exceptions with explicit error codes:

```java
throw new ApplicationException(
    RegistryErrorCode.REG_1001, 
    "Custom error message"
);
```

### Validation Exceptions

JSR-380 validation errors are automatically formatted:

```java
@PostMapping("/namespaces")
public NamespaceDto create(@Valid @RequestBody CreateNamespaceRequest request) {
    // MethodArgumentNotValidException is automatically handled
    return service.create(request);
}
```

### Data Layer Exceptions

Database exceptions are automatically mapped:

- `DuplicateKeyException` → 409 Conflict
- `DataIntegrityViolationException` → 422 Unprocessable Entity
- `OptimisticLockingFailureException` → 409 Conflict

## Customization

### Custom Field Contributors

Add custom fields to all ProblemDetail responses:

```java
@Component
public class CustomWebFieldContributor implements WebProblemFieldContributor {
    
    @Override
    public void contribute(Map<String, Object> fields, Throwable exception, HttpServletRequest request) {
        fields.put("userAgent", request.getHeader("User-Agent"));
        fields.put("clientIp", extractClientIp(request));
    }
    
    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return forwarded != null ? forwarded.split(",")[0] : request.getRemoteAddr();
    }
}
```

### Custom Validation Formatter

Customize validation error formatting:

```java
@Component
public class CustomValidationErrorsFormatter implements ValidationErrorsFormatter {
    
    @Override
    public List<ValidationError> formatWithMasking(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
            .map(error -> new ValidationError(
                error.getField(),
                maskSensitiveValue(error.getField(), error.getRejectedValue()),
                error.getDefaultMessage()
            ))
            .collect(Collectors.toList());
    }
    
    private Object maskSensitiveValue(String field, Object value) {
        if (field.toLowerCase().contains("password") || 
            field.toLowerCase().contains("token")) {
            return "***";
        }
        return value;
    }
}
```

## Security Features

### Sensitive Data Masking

The starter automatically masks sensitive data in:

- Error messages (password, token, secret, key patterns)
- Validation error rejected values
- Custom field contributions

### Proxy-Aware Path Extraction

Correctly extracts request paths in proxy/load balancer environments:

1. Standard `Forwarded` header (RFC 7239)
2. `X-Forwarded-Path` header
3. `X-Forwarded-Uri` header
4. Fallback to `request.getRequestURI()`

## Testing

### Integration Testing

```java
@SpringBootTest
@AutoConfigureTestDatabase
@TestPropertySource(properties = {
    "patra.error.context-prefix=TEST",
    "patra.web.problem.enabled=true"
})
class WebErrorHandlingIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void shouldReturnProblemDetailForDomainException() throws Exception {
        mockMvc.perform(get("/api/test/not-found"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://errors.example.com/test-0404"))
            .andExpect(jsonPath("$.title").value("TEST-0404"))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("TEST-0404"))
            .andExpect(jsonPath("$.traceId").exists())
            .andExpect(jsonPath("$.path").value("/api/test/not-found"))
            .andExpect(jsonPath("$.timestamp").exists());
    }
    
    @Test
    void shouldFormatValidationErrors() throws Exception {
        mockMvc.perform(post("/api/test/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors").isArray())
            .andExpect(jsonPath("$.errors[0].field").exists())
            .andExpect(jsonPath("$.errors[0].message").exists());
    }
}
```

### Unit Testing

```java
@ExtendWith(MockitoExtension.class)
class GlobalRestExceptionHandlerTest {
    
    @Mock
    private ErrorResolutionService errorResolutionService;
    
    @Mock
    private ProblemDetailBuilder problemDetailBuilder;
    
    @InjectMocks
    private GlobalRestExceptionHandler handler;
    
    @Test
    void shouldHandleApplicationException() {
        ApplicationException ex = new ApplicationException(
            TestErrorCode.VALIDATION_FAILED, 
            "Test message"
        );
        
        when(errorResolutionService.resolve(ex))
            .thenReturn(new ErrorResolution(TestErrorCode.VALIDATION_FAILED, 422));
        
        ResponseEntity<ProblemDetail> response = handler.handleException(ex, mockRequest);
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }
}
```

## Performance Considerations

### Response Size Limits

- Validation errors are limited to 100 items to prevent oversized responses
- Stack traces are excluded by default (configurable)
- Sensitive data is masked to prevent information leakage

### Caching

- Error resolution results are cached at the core starter level
- ProblemDetail building is optimized for common scenarios

## Troubleshooting

### Common Issues

1. **ProblemDetail not returned**: Check that `patra.web.problem.enabled=true`
2. **Missing fields**: Verify core starter configuration and field contributors
3. **Wrong HTTP status**: Check error resolution algorithm and status mapping strategy

### Debug Logging

```yaml
logging:
  level:
    com.patra.starter.web.error: DEBUG
    com.patra.starter.core.error: DEBUG
```

## Migration

See the [Migration Guide](../docs/MIGRATION_GUIDE.md) for detailed instructions on migrating from manual exception handling to automatic ProblemDetail responses.