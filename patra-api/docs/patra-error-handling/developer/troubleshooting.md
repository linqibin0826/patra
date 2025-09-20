# 故障排除指南

Patra 错误处理系统常见问题的诊断和解决方案。

## 目录

1. [常见问题](#常见问题)
2. [配置问题](#配置问题)
3. [运行时问题](#运行时问题)
4. [性能问题](#性能问题)
5. [调试技巧](#调试技巧)
6. [监控和诊断](#监控和诊断)

## 常见问题

### 1. 缺少 context-prefix 配置

**症状**：
```
APPLICATION FAILED TO START
***************************
Description:
Binding to target ... Failed to bind properties under 'patra.error'
Reason: context-prefix is required but was not provided
```

**原因**：未配置必需的 `patra.error.context-prefix` 属性。

**解决方案**：
```yaml
patra:
  error:
    context-prefix: YOUR_SERVICE_PREFIX  # 例如：REG, ORD, INV
```

**验证**：
```bash
# 检查配置是否生效
curl http://localhost:8080/actuator/configprops | jq '.contexts.application.beans.errorProperties'
```

### 2. 手动异常处理器干扰

**症状**：
- 仍然返回自定义错误格式而不是 ProblemDetail
- 日志显示手动异常处理器被调用

**原因**：现有的 `@RestControllerAdvice` 类优先级高于自动错误处理。

**解决方案**：

**选项1：移除手动处理器**
```java
// 删除或注释掉整个类
// @RestControllerAdvice
// public class OldExceptionHandler { ... }
```

**选项2：条件禁用**
```java
@RestControllerAdvice
@ConditionalOnProperty(name = "patra.web.problem.enabled", havingValue = "false")
public class OldExceptionHandler {
    // 仅在新系统禁用时激活
}
```

**选项3：调整优先级**
```java
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE) // 最低优先级
public class OldExceptionHandler {
    // 让新系统优先处理
}
```

**验证**：
```bash
# 测试错误响应格式
curl -X GET http://localhost:8080/api/test/not-found \
  -H "Accept: application/json" | jq '.'

# 应该返回 ProblemDetail 格式
{
  "type": "https://errors.example.com/your-1001",
  "title": "YOUR-1001",
  "status": 404,
  "code": "YOUR-1001",
  ...
}
```

### 3. 错误的 HTTP 状态码

**症状**：
- 期望 404，但返回 422
- 期望 409，但返回 422

**原因**：异常未正确映射到 HTTP 状态码。

**解决方案**：

**选项1：实现错误特征**
```java
public class ResourceNotFoundException extends YourServiceNotFound implements HasErrorTraits {
    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.NOT_FOUND);  // 映射到 404
    }
}
```

**选项2：使用命名约定**
```java
// 异常类名包含 "NotFound" 会自动映射到 404
public class ResourceNotFoundException extends YourServiceException {
    // 自动映射到 404
}

// 异常类名包含 "AlreadyExists" 会自动映射到 409
public class ResourceAlreadyExistsException extends YourServiceException {
    // 自动映射到 409
}
```

**选项3：自定义状态映射策略**
```java
@Component
public class CustomStatusMappingStrategy implements StatusMappingStrategy {
    @Override
    public int mapToHttpStatus(ErrorCodeLike errorCode, Throwable exception) {
        if (errorCode.code().endsWith("-1001")) {
            return 404;
        }
        if (errorCode.code().endsWith("-1002")) {
            return 409;
        }
        return 422; // 默认
    }
}
```

**验证**：
```bash
# 测试特定异常的状态码
curl -X GET http://localhost:8080/api/test/not-found -I
# HTTP/1.1 404 Not Found

curl -X POST http://localhost:8080/api/test/duplicate -I  
# HTTP/1.1 409 Conflict
```

### 4. Feign 客户端未使用 RemoteCallException

**症状**：
- 仍然抛出 `FeignException` 而不是 `RemoteCallException`
- 无法使用 `RemoteErrorHelper` 方法

**原因**：Feign 错误处理未正确配置。

**解决方案**：

**检查配置**：
```yaml
patra:
  feign:
    problem:
      enabled: true  # 必须为 true
      tolerant: true  # 推荐
```

**检查下游服务响应**：
```bash
# 下游服务应该返回 application/problem+json
curl -X GET http://downstream-service/api/resource/not-found \
  -H "Accept: application/json" -v

# 检查 Content-Type 头
< Content-Type: application/problem+json
```

**检查 Feign 客户端配置**：
```java
@FeignClient(name = "downstream-service", contextId = "downstream")
public interface DownstreamClient {
    // 确保使用正确的服务名和上下文ID
}
```

**验证**：
```java
@Test
void shouldThrowRemoteCallException() {
    assertThatThrownBy(() -> downstreamClient.getResource("not-found"))
        .isInstanceOf(RemoteCallException.class)
        .satisfies(ex -> {
            RemoteCallException remoteEx = (RemoteCallException) ex;
            assertThat(remoteEx.getErrorCode()).isNotNull();
            assertThat(remoteEx.getHttpStatus()).isEqualTo(404);
        });
}
```

### 5. 缺少 Trace ID

**症状**：
- 错误响应中 `traceId` 字段为 null
- 无法关联分布式调用链

**原因**：Trace ID 提供者未正确配置。

**解决方案**：

**检查 Trace 头配置**：
```yaml
patra:
  tracing:
    header-names:
      - traceId
      - X-B3-TraceId
      - traceparent
      - X-Your-Custom-Header
```

**实现自定义 Trace 提供者**：
```java
@Component
public class CustomTraceProvider implements TraceProvider {
    @Override
    public Optional<String> getCurrentTraceId() {
        // 从 MDC 获取
        String mdcTraceId = MDC.get("traceId");
        if (mdcTraceId != null) {
            return Optional.of(mdcTraceId);
        }
        
        // 从自定义追踪系统获取
        String customTraceId = YourTracingSystem.getCurrentTraceId();
        return Optional.ofNullable(customTraceId);
    }
}
```

**验证**：
```bash
# 发送带有 trace ID 的请求
curl -X GET http://localhost:8080/api/test/error \
  -H "traceId: test-trace-123" | jq '.traceId'

# 应该返回 "test-trace-123"
```

## 配置问题

### 1. 配置属性类型错误

**症状**：
```
Failed to bind properties under 'patra.error' to ErrorProperties
Reason: Failed to convert property value of type 'java.lang.String' to required type 'boolean'
```

**原因**：配置值类型不匹配。

**解决方案**：
```yaml
# ❌ 错误
patra:
  error:
    enabled: "true"  # 字符串

# ✅ 正确
patra:
  error:
    enabled: true    # 布尔值
```

### 2. 配置属性名称错误

**症状**：配置不生效，使用默认值。

**原因**：属性名称拼写错误。

**解决方案**：
```yaml
# ❌ 错误
patra:
  error:
    contextPrefix: REG  # 驼峰命名

# ✅ 正确
patra:
  error:
    context-prefix: REG  # 短横线命名
```

### 3. 环境特定配置问题

**症状**：在某些环境中配置不生效。

**解决方案**：

**检查 Profile 激活**：
```bash
# 检查当前激活的 Profile
curl http://localhost:8080/actuator/env | jq '.activeProfiles'
```

**检查配置优先级**：
```yaml
# application.yml (基础配置)
patra:
  error:
    context-prefix: DEFAULT

---
# application-prod.yml (生产环境覆盖)
patra:
  error:
    context-prefix: PROD
```

**使用环境变量**：
```bash
# 环境变量优先级最高
export PATRA_ERROR_CONTEXT_PREFIX=ENV_VALUE
```

## 运行时问题

### 1. 循环依赖

**症状**：
```
The dependencies of some of the beans in the application context form a cycle:
┌─────┐
│  errorResolutionService defined in class path resource
↑     ↓
│  customErrorMappingContributor defined in file
└─────┘
```

**原因**：错误映射贡献者与错误解析服务之间的循环依赖。

**解决方案**：
```java
@Component
public class CustomErrorMappingContributor implements ErrorMappingContributor {
    
    // ❌ 不要注入 ErrorResolutionService
    // @Autowired
    // private ErrorResolutionService errorResolutionService;
    
    @Override
    public Optional<ErrorCodeLike> mapException(Throwable exception) {
        // 直接处理异常映射，不依赖其他服务
        if (exception instanceof CustomException) {
            return Optional.of(CustomErrorCode.CUSTOM_ERROR);
        }
        return Optional.empty();
    }
}
```

### 2. 内存泄漏

**症状**：
- 应用内存使用持续增长
- 错误解析变慢

**原因**：错误解析缓存无限增长。

**解决方案**：

**检查缓存大小**：
```java
@Component
public class ErrorResolutionService {
    
    // 使用有界缓存
    private final Map<Class<?>, ErrorResolution> cache = 
        new ConcurrentHashMap<>(1000); // 限制大小
    
    public ErrorResolution resolve(Throwable exception) {
        Class<?> key = exception.getClass();
        
        // 检查缓存大小
        if (cache.size() > 1000) {
            cache.clear(); // 简单的清理策略
        }
        
        return cache.computeIfAbsent(key, k -> doResolve(exception));
    }
}
```

**监控缓存使用**：
```java
@Component
public class ErrorCacheMetrics {
    
    @EventListener
    @Async
    public void onCacheAccess(CacheAccessEvent event) {
        meterRegistry.counter("error.cache.access", 
            "hit", String.valueOf(event.isHit())).increment();
    }
}
```

### 3. 性能问题

**症状**：
- 错误处理响应慢
- 高并发下性能下降

**解决方案**：

**启用调试日志**：
```yaml
logging:
  level:
    com.patra.starter.core.error: DEBUG
```

**检查性能指标**：
```java
@Component
public class ErrorResolutionService {
    
    @Timed(name = "error.resolution.time")
    public ErrorResolution resolve(Throwable exception) {
        // 测量解析时间
        return doResolve(exception);
    }
}
```

**优化缓存策略**：
```java
// 使用 Caffeine 缓存
@Bean
public Cache<Class<?>, ErrorResolution> errorResolutionCache() {
    return Caffeine.newBuilder()
        .maximumSize(1000)
        .expireAfterWrite(1, TimeUnit.HOURS)
        .recordStats()
        .build();
}
```

## 调试技巧

### 1. 启用调试日志

```yaml
logging:
  level:
    # 错误处理相关
    com.patra.starter.core.error: DEBUG
    com.patra.starter.web.error: DEBUG
    com.patra.starter.feign.error: DEBUG
    
    # Spring 相关
    org.springframework.web: DEBUG
    org.springframework.boot.autoconfigure: DEBUG
    
    # 根日志
    root: INFO
```

### 2. 使用 Actuator 端点

```bash
# 检查配置属性
curl http://localhost:8080/actuator/configprops | jq '.contexts.application.beans.errorProperties'

# 检查环境变量
curl http://localhost:8080/actuator/env | jq '.propertySources[] | select(.name | contains("error"))'

# 检查健康状态
curl http://localhost:8080/actuator/health

# 检查指标
curl http://localhost:8080/actuator/metrics/error.resolution.time
```

### 3. 自定义调试端点

```java
@RestController
@RequestMapping("/debug/error")
public class ErrorDebugController {
    
    private final ErrorResolutionService errorResolutionService;
    
    @GetMapping("/resolve/{exceptionClass}")
    public Map<String, Object> debugResolve(@PathVariable String exceptionClass) {
        try {
            Class<?> clazz = Class.forName(exceptionClass);
            Exception exception = (Exception) clazz.getDeclaredConstructor(String.class)
                .newInstance("Debug test");
            
            ErrorResolution resolution = errorResolutionService.resolve(exception);
            
            return Map.of(
                "exceptionClass", exceptionClass,
                "errorCode", resolution.errorCode().code(),
                "httpStatus", resolution.httpStatus(),
                "message", resolution.message()
            );
        } catch (Exception e) {
            return Map.of("error", e.getMessage());
        }
    }
}
```

### 4. 测试特定错误场景

```java
@RestController
@RequestMapping("/test/error")
public class ErrorTestController {
    
    @GetMapping("/not-found")
    public void testNotFound() {
        throw new ResourceNotFoundException("test-resource");
    }
    
    @GetMapping("/conflict")
    public void testConflict() {
        throw new ResourceAlreadyExistsException("test-resource");
    }
    
    @GetMapping("/validation")
    public void testValidation(@Valid @RequestBody TestRequest request) {
        // 验证错误会自动处理
    }
    
    @GetMapping("/custom/{code}")
    public void testCustomError(@PathVariable String code) {
        throw new ApplicationException(
            TestErrorCode.valueOf(code), 
            "Custom test error"
        );
    }
}
```

## 监控和诊断

### 1. 错误指标监控

```java
@Component
public class ErrorMetrics {
    
    private final MeterRegistry meterRegistry;
    
    @EventListener
    public void onErrorResolution(ErrorResolutionEvent event) {
        Counter.builder("error.resolution.total")
            .tag("service", event.getServiceName())
            .tag("code", event.getErrorCode())
            .tag("status", String.valueOf(event.getHttpStatus()))
            .register(meterRegistry)
            .increment();
    }
}
```

### 2. 健康检查

```java
@Component
public class ErrorHandlingHealthIndicator implements HealthIndicator {
    
    private final ErrorResolutionService errorResolutionService;
    
    @Override
    public Health health() {
        try {
            // 测试错误解析功能
            ErrorResolution resolution = errorResolutionService.resolve(
                new RuntimeException("Health check test")
            );
            
            return Health.up()
                .withDetail("errorResolution", "working")
                .withDetail("lastTest", Instant.now())
                .build();
                
        } catch (Exception e) {
            return Health.down()
                .withDetail("error", e.getMessage())
                .build();
        }
    }
}
```

### 3. 分布式追踪

```java
@Component
public class ErrorTracingAspect {
    
    @Around("@annotation(org.springframework.web.bind.annotation.ExceptionHandler)")
    public Object traceErrorHandling(ProceedingJoinPoint joinPoint) throws Throwable {
        Span span = tracer.nextSpan()
            .name("error-handling")
            .tag("handler", joinPoint.getSignature().getName())
            .start();
        
        try (Tracer.SpanInScope ws = tracer.withSpanInScope(span)) {
            return joinPoint.proceed();
        } catch (Exception e) {
            span.tag("error", e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
}
```

### 4. 日志聚合和分析

```yaml
# Logback 配置 - 结构化日志
logging:
  pattern:
    console: '{"timestamp":"%d{yyyy-MM-dd HH:mm:ss.SSS}","level":"%-5level","thread":"%thread","traceId":"%X{traceId:-}","logger":"%logger{36}","message":"%msg","exception":"%ex"}%n'
```

```bash
# 使用 jq 分析错误日志
tail -f logs/application.log | jq 'select(.level == "ERROR")'

# 统计错误代码分布
grep "ERROR" logs/application.log | jq -r '.message' | grep -o 'REG-[0-9]*' | sort | uniq -c
```

通过这些故障排除技巧和监控方法，可以快速诊断和解决错误处理系统中的问题。