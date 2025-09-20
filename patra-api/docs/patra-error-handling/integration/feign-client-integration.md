# Feign 客户端集成指南

详细介绍如何在 Feign 客户端中集成 Patra 错误处理系统，实现服务间调用的类型化异常处理。

## 目录

1. [基础集成](#基础集成)
2. [错误处理模式](#错误处理模式)
3. [RemoteCallException 详解](#remotecallexception-详解)
4. [错误辅助工具](#错误辅助工具)
5. [最佳实践](#最佳实践)
6. [测试策略](#测试策略)

## 基础集成

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-cloud-starter-feign</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 2. 配置属性

```yaml
patra:
  feign:
    problem:
      enabled: true
      tolerant: true  # 推荐：优雅处理非ProblemDetail响应
```

### 3. 创建 Feign 客户端

```java
@FeignClient(name = "patra-registry", contextId = "registry")
public interface RegistryClient {
    
    @GetMapping("/api/registry/dictionaries/{typeCode}/items/{itemCode}")
    DictionaryItemDto getDictionaryItem(@PathVariable String typeCode, @PathVariable String itemCode);
    
    @GetMapping("/api/registry/dictionaries/{typeCode}/items")
    List<DictionaryItemDto> getDictionaryItems(@PathVariable String typeCode);
    
    @PostMapping("/api/registry/dictionaries")
    DictionaryTypeDto createDictionaryType(@RequestBody CreateDictionaryTypeRequest request);
}
```

## 错误处理模式

### 1. 基础错误处理

```java
@Service
public class RegistryIntegrationService {
    
    private final RegistryClient registryClient;
    
    public Optional<DictionaryItemDto> getDictionaryItemSafely(String typeCode, String itemCode) {
        try {
            DictionaryItemDto item = registryClient.getDictionaryItem(typeCode, itemCode);
            return Optional.of(item);
        } catch (RemoteCallException ex) {
            // 使用辅助方法检查错误类型
            if (RemoteErrorHelper.isNotFound(ex)) {
                log.debug("Dictionary item not found: typeCode={}, itemCode={}", typeCode, itemCode);
                return Optional.empty();
            }
            
            // 重新抛出其他错误
            throw ex;
        }
    }
}
```

### 2. 特定错误代码处理

```java
@Service
public class AdvancedRegistryIntegrationService {
    
    private final RegistryClient registryClient;
    
    public DictionaryItemDto getDictionaryItemWithRetry(String typeCode, String itemCode) {
        try {
            return registryClient.getDictionaryItem(typeCode, itemCode);
        } catch (RemoteCallException ex) {
            // 处理特定的Registry错误代码
            if (RemoteErrorHelper.is(ex, "REG-1401")) {
                throw new DictionaryTypeNotFoundException(typeCode);
            }
            
            if (RemoteErrorHelper.is(ex, "REG-1402")) {
                throw new DictionaryItemNotFoundException(typeCode, itemCode);
            }
            
            if (RemoteErrorHelper.is(ex, "REG-1403")) {
                throw new DictionaryItemDisabledException(typeCode, itemCode);
            }
            
            // 处理服务器错误（可重试）
            if (RemoteErrorHelper.isServerError(ex)) {
                log.error("Registry service error: traceId={}, error={}", ex.getTraceId(), ex.getMessage());
                throw new RegistryServiceUnavailableException("Registry service temporarily unavailable", ex);
            }
            
            // 重新抛出客户端错误（通常是我们的错误）
            throw ex;
        }
    }
}
```

### 3. 批量操作错误处理

```java
@Service
public class BatchRegistryService {
    
    private final RegistryClient registryClient;
    
    public List<DictionaryItemDto> getDictionaryItemsBatch(List<String> typeCodes) {
        List<DictionaryItemDto> results = new ArrayList<>();
        
        for (String typeCode : typeCodes) {
            try {
                List<DictionaryItemDto> items = registryClient.getDictionaryItems(typeCode);
                results.addAll(items);
            } catch (RemoteCallException ex) {
                // 记录失败但继续处理其他项
                if (RemoteErrorHelper.isNotFound(ex)) {
                    log.warn("Dictionary type not found, skipping: typeCode={}", typeCode);
                    continue;
                }
                
                // 对于服务器错误，停止批量处理
                if (RemoteErrorHelper.isServerError(ex)) {
                    log.error("Registry service error during batch processing: traceId={}", ex.getTraceId());
                    throw new BatchProcessingException("Batch processing failed due to service error", ex);
                }
                
                // 其他错误也停止处理
                throw ex;
            }
        }
        
        return results;
    }
}
```

## RemoteCallException 详解

### 属性说明

```java
public class RemoteCallException extends RuntimeException {
    private final String errorCode;        // 错误代码（如 "REG-1001"）
    private final int httpStatus;          // HTTP状态码
    private final String methodKey;        // Feign方法标识符
    private final String traceId;          // 分布式追踪ID
    private final Map<String, Object> extensions; // 额外的ProblemDetail字段
}
```

### 使用示例

```java
try {
    registryClient.getDictionaryItem("COUNTRY", "US");
} catch (RemoteCallException ex) {
    // 访问错误详情
    String errorCode = ex.getErrorCode();     // "REG-1402"
    int status = ex.getHttpStatus();          // 404
    String traceId = ex.getTraceId();         // "abc123def456"
    String method = ex.getMethodKey();        // "RegistryClient#getDictionaryItem(String,String)"
    
    // 访问扩展字段
    Map<String, Object> extensions = ex.getExtensions();
    String timestamp = (String) extensions.get("timestamp");
    String path = (String) extensions.get("path");
    
    log.error("Registry call failed: code={}, status={}, traceId={}, method={}", 
              errorCode, status, traceId, method);
}
```

## 错误辅助工具

### RemoteErrorHelper 方法

```java
// 按HTTP状态检查
RemoteErrorHelper.isNotFound(ex)        // 404 或 *-0404 错误代码
RemoteErrorHelper.isConflict(ex)        // 409 或 *-0409 错误代码
RemoteErrorHelper.isClientError(ex)     // 4xx 状态码
RemoteErrorHelper.isServerError(ex)     // 5xx 状态码

// 按错误代码检查
RemoteErrorHelper.is(ex, "REG-1001")    // 特定错误代码匹配
RemoteErrorHelper.hasErrorCode(ex)      // 有非空错误代码

// 模式匹配
RemoteErrorHelper.isQuotaExceeded(ex)   // *-QUOTA 或 429 状态
RemoteErrorHelper.isTimeout(ex)         // *-TIMEOUT 或 504 状态
```

### 自定义错误检查

```java
public class RegistryErrorHelper {
    
    public static boolean isDictionaryError(RemoteCallException ex) {
        String code = ex.getErrorCode();
        return code != null && code.startsWith("REG-14");
    }
    
    public static boolean isDictionaryTypeNotFound(RemoteCallException ex) {
        return RemoteErrorHelper.is(ex, "REG-1401");
    }
    
    public static boolean isDictionaryItemNotFound(RemoteCallException ex) {
        return RemoteErrorHelper.is(ex, "REG-1402");
    }
    
    public static boolean isDictionaryDisabled(RemoteCallException ex) {
        return RemoteErrorHelper.is(ex, "REG-1403") || RemoteErrorHelper.is(ex, "REG-1406");
    }
}
```

## 最佳实践

### 1. 错误分类处理

```java
@Service
public class RobustRegistryService {
    
    private final RegistryClient registryClient;
    
    public DictionaryItemDto getDictionaryItem(String typeCode, String itemCode) {
        try {
            return registryClient.getDictionaryItem(typeCode, itemCode);
        } catch (RemoteCallException ex) {
            // 1. 业务错误 - 转换为领域异常
            if (RemoteErrorHelper.isNotFound(ex)) {
                throw new DictionaryNotFoundException(typeCode, itemCode);
            }
            
            // 2. 基础设施错误 - 包装为基础设施异常
            if (RemoteErrorHelper.isServerError(ex)) {
                throw new RegistryServiceException("Registry service error", ex);
            }
            
            // 3. 客户端错误 - 通常表示我们的代码有问题
            if (RemoteErrorHelper.isClientError(ex)) {
                log.error("Client error calling registry: {}", ex.getMessage(), ex);
                throw new RegistryClientException("Invalid registry request", ex);
            }
            
            // 4. 未知错误 - 重新抛出
            throw ex;
        }
    }
}
```

### 2. 重试策略

```java
@Component
public class RetryableRegistryService {
    
    private final RegistryClient registryClient;
    
    @Retryable(
        value = {RegistryServiceException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2)
    )
    public DictionaryItemDto getDictionaryItemWithRetry(String typeCode, String itemCode) {
        try {
            return registryClient.getDictionaryItem(typeCode, itemCode);
        } catch (RemoteCallException ex) {
            // 只重试服务器错误
            if (RemoteErrorHelper.isServerError(ex)) {
                throw new RegistryServiceException("Registry service error", ex);
            }
            
            // 不重试客户端错误
            throw new RegistryClientException("Registry client error", ex);
        }
    }
    
    @Recover
    public DictionaryItemDto recover(RegistryServiceException ex, String typeCode, String itemCode) {
        log.error("Registry service unavailable after retries: typeCode={}, itemCode={}", typeCode, itemCode);
        throw new RegistryUnavailableException("Registry service is currently unavailable");
    }
}
```

### 3. 熔断器集成

```java
@Component
public class CircuitBreakerRegistryService {
    
    private final RegistryClient registryClient;
    
    @CircuitBreaker(name = "registry-service", fallbackMethod = "getDictionaryItemFallback")
    @TimeLimiter(name = "registry-service")
    public CompletableFuture<DictionaryItemDto> getDictionaryItemAsync(String typeCode, String itemCode) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return registryClient.getDictionaryItem(typeCode, itemCode);
            } catch (RemoteCallException ex) {
                if (RemoteErrorHelper.isNotFound(ex)) {
                    throw new DictionaryNotFoundException(typeCode, itemCode);
                }
                
                // 让熔断器处理服务器错误
                if (RemoteErrorHelper.isServerError(ex)) {
                    throw new RegistryServiceException("Registry service error", ex);
                }
                
                throw ex;
            }
        });
    }
    
    public CompletableFuture<DictionaryItemDto> getDictionaryItemFallback(
            String typeCode, String itemCode, Exception ex) {
        log.warn("Using fallback for dictionary item: typeCode={}, itemCode={}, error={}", 
                 typeCode, itemCode, ex.getMessage());
        
        // 返回默认值或从缓存获取
        return CompletableFuture.completedFuture(getFromCache(typeCode, itemCode));
    }
}
```

## 测试策略

### 1. Mock 测试

```java
@ExtendWith(MockitoExtension.class)
class RegistryIntegrationServiceTest {
    
    @Mock
    private RegistryClient registryClient;
    
    @InjectMocks
    private RegistryIntegrationService integrationService;
    
    @Test
    void shouldHandleNotFoundGracefully() {
        // 模拟404错误
        RemoteCallException notFoundEx = new RemoteCallException(
            404, "Dictionary item not found", "RegistryClient#getDictionaryItem(String,String)", "trace123"
        );
        notFoundEx.setErrorCode("REG-1402");
        
        when(registryClient.getDictionaryItem("COUNTRY", "XX")).thenThrow(notFoundEx);
        
        Optional<DictionaryItemDto> result = integrationService.getDictionaryItemSafely("COUNTRY", "XX");
        
        assertThat(result).isEmpty();
    }
    
    @Test
    void shouldPropagateServerErrors() {
        // 模拟500错误
        RemoteCallException serverError = new RemoteCallException(
            500, "Internal server error", "RegistryClient#getDictionaryItem(String,String)", "trace123"
        );
        
        when(registryClient.getDictionaryItem("COUNTRY", "US")).thenThrow(serverError);
        
        assertThatThrownBy(() -> integrationService.getDictionaryItemSafely("COUNTRY", "US"))
            .isInstanceOf(RemoteCallException.class)
            .hasMessage("Internal server error");
    }
}
```

### 2. 集成测试

```java
@SpringBootTest
@TestPropertySource(properties = {
    "patra.feign.problem.enabled=true",
    "patra.feign.problem.tolerant=true"
})
class FeignErrorHandlingIntegrationTest {
    
    @Autowired
    private RegistryClient registryClient;
    
    @Test
    void shouldDecodeProblemDetailResponse() {
        // 使用WireMock模拟下游服务
        stubFor(get(urlEqualTo("/api/registry/dictionaries/NONEXISTENT/items/TEST"))
            .willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/problem+json")
                .withBody("""
                    {
                        "type": "https://errors.patra.com/reg-1402",
                        "title": "REG-1402",
                        "status": 404,
                        "detail": "Dictionary item not found: typeCode=NONEXISTENT, itemCode=TEST",
                        "code": "REG-1402",
                        "traceId": "test-trace-123"
                    }
                    """)));
        
        assertThatThrownBy(() -> registryClient.getDictionaryItem("NONEXISTENT", "TEST"))
            .isInstanceOf(RemoteCallException.class)
            .satisfies(ex -> {
                RemoteCallException remoteEx = (RemoteCallException) ex;
                assertThat(remoteEx.getErrorCode()).isEqualTo("REG-1402");
                assertThat(remoteEx.getHttpStatus()).isEqualTo(404);
                assertThat(remoteEx.getTraceId()).isEqualTo("test-trace-123");
            });
    }
}
```

通过这些集成模式和最佳实践，您可以构建健壮的服务间通信，优雅地处理各种错误情况，并提供良好的用户体验。