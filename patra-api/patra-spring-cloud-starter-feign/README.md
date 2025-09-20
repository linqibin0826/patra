# Patra Spring Cloud Starter Feign

Feign error handling starter that automatically decodes downstream ProblemDetail responses into typed exceptions. Provides seamless error handling for service-to-service communication.

## Features

- **Automatic ProblemDetail decoding** from downstream services
- **Typed exception handling** with RemoteCallException
- **Tolerant mode** for graceful handling of non-ProblemDetail responses
- **Trace ID propagation** for distributed tracing
- **Error helper utilities** for common error checking patterns
- **Circuit breaker friendly** error handling

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
        <artifactId>patra-spring-cloud-starter-feign</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

### 2. Configure Properties

```yaml
patra:
  error:
    context-prefix: REG  # Required in core starter
  feign:
    problem:
      enabled: true
      tolerant: true
```

### 3. Use with Feign Clients

```java
@FeignClient(name = "registry-service", contextId = "registry")
public interface RegistryClient {
    
    @GetMapping("/api/registry/namespaces/{id}")
    NamespaceDto getNamespace(@PathVariable String id);
    
    @PostMapping("/api/registry/namespaces")
    NamespaceDto createNamespace(@RequestBody CreateNamespaceRequest request);
}

@Service
public class IntegrationService {
    
    private final RegistryClient registryClient;
    
    public Optional<NamespaceDto> getNamespaceIfExists(String id) {
        try {
            return Optional.of(registryClient.getNamespace(id));
        } catch (RemoteCallException ex) {
            if (RemoteErrorHelper.isNotFound(ex)) {
                return Optional.empty();
            }
            throw ex; // Re-throw other errors
        }
    }
    
    public void handleRegistryErrors() {
        try {
            registryClient.createNamespace(request);
        } catch (RemoteCallException ex) {
            if (RemoteErrorHelper.isConflict(ex)) {
                log.warn("Namespace already exists: {}", ex.getMessage());
                return;
            }
            
            if (RemoteErrorHelper.is(ex, "REG-1001")) {
                log.error("Specific registry error: {}", ex.getMessage());
                return;
            }
            
            throw ex; // Re-throw unexpected errors
        }
    }
}
```

## Configuration Properties

### Feign Error Properties (`patra.feign.problem`)

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable/disable Feign error handling |
| `tolerant` | boolean | `true` | Handle non-ProblemDetail responses gracefully |

### Example Configuration

```yaml
patra:
  feign:
    problem:
      enabled: true
      tolerant: true  # Recommended for production
```

## RemoteCallException

The main exception type for remote service errors:

### Properties

```java
public class RemoteCallException extends RuntimeException {
    private final String errorCode;        // Error code from ProblemDetail
    private final int httpStatus;          // HTTP status code
    private final String methodKey;        // Feign method identifier
    private final String traceId;          // Distributed trace ID
    private final Map<String, Object> extensions; // Additional ProblemDetail fields
}
```

### Usage Examples

```java
try {
    registryClient.getNamespace("test");
} catch (RemoteCallException ex) {
    // Access error details
    String errorCode = ex.getErrorCode();     // "REG-1001"
    int status = ex.getHttpStatus();          // 404
    String traceId = ex.getTraceId();         // "abc123def456"
    String method = ex.getMethodKey();        // "RegistryClient#getNamespace(String)"
    
    // Access extension fields
    Map<String, Object> extensions = ex.getExtensions();
    String timestamp = (String) extensions.get("timestamp");
    String path = (String) extensions.get("path");
}
```

## Error Helper Utilities

### RemoteErrorHelper

Utility class for common error checking patterns:

```java
// Check by HTTP status
RemoteErrorHelper.isNotFound(ex)        // 404 or *-0404 error code
RemoteErrorHelper.isConflict(ex)        // 409 or *-0409 error code
RemoteErrorHelper.isClientError(ex)     // 4xx status codes
RemoteErrorHelper.isServerError(ex)     // 5xx status codes

// Check by error code
RemoteErrorHelper.is(ex, "REG-1001")    // Specific error code match
RemoteErrorHelper.hasErrorCode(ex)      // Has non-null error code

// Pattern matching
RemoteErrorHelper.isQuotaExceeded(ex)   // *-QUOTA or 429 status
RemoteErrorHelper.isTimeout(ex)         // *-TIMEOUT or 504 status
```

### Usage Patterns

```java
@Service
public class OrderService {
    
    private final InventoryClient inventoryClient;
    
    public void processOrder(OrderRequest request) {
        try {
            inventoryClient.reserveItems(request.getItems());
        } catch (RemoteCallException ex) {
            // Handle specific business errors
            if (RemoteErrorHelper.is(ex, "INV-2001")) {
                throw new InsufficientInventoryException(ex.getMessage());
            }
            
            // Handle common error patterns
            if (RemoteErrorHelper.isQuotaExceeded(ex)) {
                throw new RateLimitExceededException("Inventory service rate limit exceeded");
            }
            
            // Handle infrastructure errors
            if (RemoteErrorHelper.isServerError(ex)) {
                log.error("Inventory service error: {}", ex.getMessage(), ex);
                throw new ServiceUnavailableException("Inventory service temporarily unavailable");
            }
            
            // Re-throw client errors (likely our fault)
            throw ex;
        }
    }
}
```

## Tolerant Mode

When `tolerant: true` (default), the error decoder gracefully handles:

### Non-ProblemDetail Responses

```java
// HTTP 404 with empty body → RemoteCallException with status 404
// HTTP 500 with plain text → RemoteCallException with status 500 and text message
// Invalid JSON response → RemoteCallException with decode error message
```

### Strict Mode

When `tolerant: false`, non-ProblemDetail responses throw standard `FeignException`:

```yaml
patra:
  feign:
    problem:
      tolerant: false  # Strict mode - only handle ProblemDetail responses
```

## Trace ID Propagation

### Automatic Propagation

The starter automatically adds trace IDs to outgoing requests:

```java
@Component
public class TraceIdRequestInterceptor implements RequestInterceptor {
    
    @Override
    public void apply(RequestTemplate template) {
        traceProvider.getCurrentTraceId()
            .ifPresent(traceId -> template.header("traceId", traceId));
    }
}
```

### Custom Headers

Configure additional trace headers:

```yaml
patra:
  tracing:
    header-names:
      - traceId
      - X-B3-TraceId
      - traceparent
      - X-Custom-Trace-Id
```

## Circuit Breaker Integration

### Hystrix/Resilience4j Integration

```java
@Component
public class ResilientRegistryClient {
    
    private final RegistryClient registryClient;
    
    @CircuitBreaker(name = "registry-service")
    @Retry(name = "registry-service")
    public Optional<NamespaceDto> getNamespace(String id) {
        try {
            return Optional.of(registryClient.getNamespace(id));
        } catch (RemoteCallException ex) {
            if (RemoteErrorHelper.isNotFound(ex)) {
                return Optional.empty();
            }
            
            // Let circuit breaker handle server errors
            if (RemoteErrorHelper.isServerError(ex)) {
                throw new ServiceUnavailableException(ex.getMessage(), ex);
            }
            
            // Don't retry client errors
            throw new NonRetryableException(ex.getMessage(), ex);
        }
    }
}
```

## Testing

### Mock Testing

```java
@ExtendWith(MockitoExtension.class)
class IntegrationServiceTest {
    
    @Mock
    private RegistryClient registryClient;
    
    @InjectMocks
    private IntegrationService integrationService;
    
    @Test
    void shouldHandleNotFoundGracefully() {
        RemoteCallException notFoundEx = new RemoteCallException(
            404, "Namespace not found", "RegistryClient#getNamespace(String)", "trace123"
        );
        
        when(registryClient.getNamespace("test")).thenThrow(notFoundEx);
        
        Optional<NamespaceDto> result = integrationService.getNamespaceIfExists("test");
        
        assertThat(result).isEmpty();
    }
    
    @Test
    void shouldPropagateUnexpectedErrors() {
        RemoteCallException serverError = new RemoteCallException(
            500, "Internal server error", "RegistryClient#getNamespace(String)", "trace123"
        );
        
        when(registryClient.getNamespace("test")).thenThrow(serverError);
        
        assertThatThrownBy(() -> integrationService.getNamespaceIfExists("test"))
            .isInstanceOf(RemoteCallException.class)
            .hasMessage("Internal server error");
    }
}
```

### Integration Testing

```java
@SpringBootTest
@TestPropertySource(properties = {
    "patra.feign.problem.enabled=true",
    "patra.feign.problem.tolerant=true"
})
class FeignErrorHandlingIntegrationTest {
    
    @Autowired
    private TestFeignClient testClient;
    
    @Test
    void shouldDecodeProblemDetailResponse() {
        // Mock downstream service returning ProblemDetail
        mockServer.expect(requestTo("/api/test/error"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body("""
                    {
                        "type": "https://errors.example.com/test-0404",
                        "title": "TEST-0404",
                        "status": 404,
                        "detail": "Resource not found",
                        "code": "TEST-0404",
                        "traceId": "abc123"
                    }
                    """));
        
        assertThatThrownBy(() -> testClient.getResource("test"))
            .isInstanceOf(RemoteCallException.class)
            .satisfies(ex -> {
                RemoteCallException remoteEx = (RemoteCallException) ex;
                assertThat(remoteEx.getErrorCode()).isEqualTo("TEST-0404");
                assertThat(remoteEx.getHttpStatus()).isEqualTo(404);
                assertThat(remoteEx.getTraceId()).isEqualTo("abc123");
            });
    }
}
```

## Performance Considerations

### Error Decoding Performance

- ProblemDetail parsing is optimized with Jackson ObjectMapper
- Tolerant mode adds minimal overhead for error cases
- Trace ID extraction uses efficient header lookup

### Memory Usage

- RemoteCallException stores only essential error information
- Extension fields are stored in a HashMap (lazy initialization)
- No unnecessary object creation in happy path

## Troubleshooting

### Common Issues

1. **FeignException instead of RemoteCallException**: Check that `patra.feign.problem.enabled=true`
2. **Missing trace IDs**: Verify trace provider configuration and header names
3. **Tolerant mode not working**: Ensure downstream service returns proper content-type headers

### Debug Logging

```yaml
logging:
  level:
    com.patra.starter.feign.error: DEBUG
    feign.Logger: DEBUG
```

### Error Decoder Testing

```java
@Test
void testErrorDecoderDirectly() {
    ProblemDetailErrorDecoder decoder = new ProblemDetailErrorDecoder(objectMapper, properties);
    
    Response response = Response.builder()
        .status(404)
        .reason("Not Found")
        .headers(Map.of("content-type", List.of("application/problem+json")))
        .body(problemDetailJson, StandardCharsets.UTF_8)
        .build();
    
    Exception result = decoder.decode("TestClient#method()", response);
    
    assertThat(result).isInstanceOf(RemoteCallException.class);
}
```

## Migration

See the [Migration Guide](../docs/MIGRATION_GUIDE.md) for detailed instructions on migrating from manual Feign error handling to automatic ProblemDetail decoding.