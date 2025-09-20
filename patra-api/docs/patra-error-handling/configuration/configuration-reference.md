# Patra 错误处理系统 - 配置参考手册

Patra 错误处理系统所有配置属性的完整参考手册和示例。

## 目录

1. [核心配置](#核心配置)
2. [Web配置](#web配置)
3. [Feign配置](#feign配置)
4. [链路追踪配置](#链路追踪配置)
5. [MyBatis配置](#mybatis配置)
6. [环境特定示例](#环境特定示例)
7. [高级配置](#高级配置)

## 核心配置

### patra.error.*

由 `patra-spring-boot-starter-core` 提供的核心错误处理配置。

| 属性 | 类型 | 默认值 | 必需 | 描述 |
|------|------|--------|------|------|
| `enabled` | boolean | `true` | 否 | 启用/禁用整个错误处理系统 |
| `context-prefix` | String | - | **是** | 服务特定的错误代码前缀（例如：REG, ORD, INV） |
| `map-status.strategy` | String | `suffix-heuristic` | 否 | HTTP状态码映射策略实现 |

#### 示例

```yaml
# 最小配置
patra:
  error:
    context-prefix: REG

# 完整配置
patra:
  error:
    enabled: true
    context-prefix: REG
    map-status:
      strategy: suffix-heuristic
```

#### 上下文前缀指南

为您的服务选择有意义的前缀：

```yaml
# Registry 服务
patra:
  error:
    context-prefix: REG

# Order 管理服务  
patra:
  error:
    context-prefix: ORD

# Inventory 服务
patra:
  error:
    context-prefix: INV

# User 管理服务
patra:
  error:
    context-prefix: USR

# Payment 服务
patra:
  error:
    context-prefix: PAY
```

## Web配置

### patra.web.problem.*

由 `patra-spring-boot-starter-web` 提供的Web特定错误处理配置。

| 属性 | 类型 | 默认值 | 必需 | 描述 |
|------|------|--------|------|------|
| `enabled` | boolean | `true` | 否 | 启用/禁用Web错误处理 |
| `type-base-url` | String | `https://errors.example.com/` | 否 | ProblemDetail type字段的基础URL |
| `include-stack` | boolean | `false` | 否 | 在响应中包含堆栈跟踪（仅开发环境） |

#### 示例

```yaml
# 开发环境配置
patra:
  web:
    problem:
      enabled: true
      type-base-url: "https://dev-errors.mycompany.com/"
      include-stack: true  # 仅用于开发环境

# 生产环境配置
patra:
  web:
    problem:
      enabled: true
      type-base-url: "https://errors.mycompany.com/"
      include-stack: false  # 生产环境绝不为true

# 禁用Web错误处理
patra:
  web:
    problem:
      enabled: false
```

#### Type Base URL 配置

`type-base-url` 用于构造 ProblemDetail 响应中的 `type` 字段：

```yaml
patra:
  error:
    context-prefix: REG
  web:
    problem:
      type-base-url: "https://errors.mycompany.com/"
```

结果：
```json
{
  "type": "https://errors.mycompany.com/reg-1001",
  "title": "REG-1001",
  "code": "REG-1001"
}
```

## Feign配置

### patra.feign.problem.*

由 `patra-spring-cloud-starter-feign` 提供的Feign客户端错误处理配置。

| 属性 | 类型 | 默认值 | 必需 | 描述 |
|------|------|--------|------|------|
| `enabled` | boolean | `true` | 否 | 启用/禁用Feign错误处理 |
| `tolerant` | boolean | `true` | 否 | 优雅处理非ProblemDetail响应 |

#### 示例

```yaml
# 推荐的生产环境配置
patra:
  feign:
    problem:
      enabled: true
      tolerant: true  # 优雅处理非ProblemDetail响应

# 严格模式（仅处理ProblemDetail响应）
patra:
  feign:
    problem:
      enabled: true
      tolerant: false

# 禁用Feign错误处理
patra:
  feign:
    problem:
      enabled: false
```

#### 容错模式行为

当 `tolerant: true`（推荐）时：

```yaml
patra:
  feign:
    problem:
      tolerant: true
```

- **ProblemDetail响应**：解码为带有完整错误详情的 `RemoteCallException`
- **非ProblemDetail响应**：优雅处理，提供基本错误信息
- **空响应**：处理时不抛出解析错误
- **无效JSON**：使用回退错误消息处理

当 `tolerant: false` 时：

```yaml
patra:
  feign:
    problem:
      tolerant: false
```

- **ProblemDetail响应**：解码为 `RemoteCallException`
- **非ProblemDetail响应**：抛出标准 `FeignException`
- **解析错误**：抛出 `FeignException`

## 链路追踪配置

### patra.tracing.*

分布式链路追踪配置，用于trace ID传播。

| 属性 | 类型 | 默认值 | 必需 | 描述 |
|------|------|--------|------|------|
| `header-names` | List<String> | `[traceId, X-B3-TraceId, traceparent]` | 否 | 要检查的trace ID头名称 |

#### 示例

```yaml
# 默认配置
patra:
  tracing:
    header-names:
      - traceId
      - X-B3-TraceId
      - traceparent

# 自定义trace头
patra:
  tracing:
    header-names:
      - traceId
      - X-B3-TraceId
      - traceparent
      - X-Custom-Trace-Id
      - X-Request-ID

# 最小配置（仅检查traceId）
patra:
  tracing:
    header-names:
      - traceId
```

#### Trace头优先级

头按指定顺序检查。使用第一个非空头值：

```yaml
patra:
  tracing:
    header-names:
      - traceId          # 首先检查
      - X-B3-TraceId     # 如果traceId为空则检查
      - traceparent      # 如果前面的头都为空则检查
```

## MyBatis配置

### 数据层错误映射

`patra-spring-boot-starter-mybatis` 为常见数据库异常提供自动映射。

无需额外配置。以下异常会自动映射：

- `DuplicateKeyException` → Conflict (409)
- `DataIntegrityViolationException` → Unprocessable Entity (422)
- `OptimisticLockingFailureException` → Conflict (409)

#### 自定义数据层映射

使用自定义贡献者覆盖默认映射：

```java
@Component
public class CustomDataLayerErrorMappingContributor implements ErrorMappingContributor {
    
    @Override
    public Optional<ErrorCodeLike> mapException(Throwable exception) {
        if (exception instanceof DuplicateKeyException) {
            return Optional.of(YourServiceErrorCode.DUPLICATE_RESOURCE);
        }
        
        if (exception instanceof DataIntegrityViolationException) {
            return Optional.of(YourServiceErrorCode.DATA_INTEGRITY_VIOLATION);
        }
        
        return Optional.empty();
    }
}
```

## 环境特定示例

### 开发环境

```yaml
# application-dev.yml
patra:
  error:
    enabled: true
    context-prefix: REG
  web:
    problem:
      enabled: true
      type-base-url: "https://dev-errors.mycompany.com/"
      include-stack: true  # 启用以便调试
  feign:
    problem:
      enabled: true
      tolerant: true
  tracing:
    header-names:
      - traceId
      - X-B3-TraceId
      - traceparent

# 启用调试日志
logging:
  level:
    com.patra.starter.core.error: DEBUG
    com.patra.starter.web.error: DEBUG
    com.patra.starter.feign.error: DEBUG
```

### 测试环境

```yaml
# application-test.yml
patra:
  error:
    enabled: true
    context-prefix: TEST
  web:
    problem:
      enabled: true
      type-base-url: "https://test-errors.mycompany.com/"
      include-stack: false
  feign:
    problem:
      enabled: true
      tolerant: true
  tracing:
    header-names:
      - traceId

# 测试的最小日志
logging:
  level:
    com.patra.starter: WARN
```

### 生产环境

```yaml
# application-prod.yml
patra:
  error:
    enabled: true
    context-prefix: REG
  web:
    problem:
      enabled: true
      type-base-url: "https://errors.mycompany.com/"
      include-stack: false  # 生产环境绝不启用
  feign:
    problem:
      enabled: true
      tolerant: true
  tracing:
    header-names:
      - traceId
      - X-B3-TraceId
      - traceparent

# 生产环境日志
logging:
  level:
    com.patra.starter: INFO
    root: WARN
```

## 高级配置

### 自定义状态映射策略

实现自定义HTTP状态映射逻辑：

```java
@Component
public class CustomStatusMappingStrategy implements StatusMappingStrategy {
    
    @Override
    public int mapToHttpStatus(ErrorCodeLike errorCode, Throwable exception) {
        String code = errorCode.code();
        
        // 自定义业务逻辑
        if (code.endsWith("-QUOTA")) {
            return 429; // Too Many Requests
        }
        
        if (code.endsWith("-TIMEOUT")) {
            return 504; // Gateway Timeout
        }
        
        if (code.endsWith("-UNAUTHORIZED")) {
            return 401; // Unauthorized
        }
        
        // 回退到后缀启发式
        if (code.contains("NOT_FOUND") || code.endsWith("-0404")) {
            return 404;
        }
        
        if (code.contains("CONFLICT") || code.endsWith("-0409")) {
            return 409;
        }
        
        // 默认回退
        return 422;
    }
}
```

### 自定义Trace提供者

实现自定义trace ID提取：

```java
@Component
public class CustomTraceProvider implements TraceProvider {
    
    private final YourTracingSystem tracingSystem;
    
    @Override
    public Optional<String> getCurrentTraceId() {
        // 自定义trace ID提取逻辑
        String traceId = tracingSystem.getCurrentTraceId();
        
        if (traceId != null && !traceId.isEmpty()) {
            return Optional.of(traceId);
        }
        
        // 回退到MDC
        String mdcTraceId = MDC.get("traceId");
        return Optional.ofNullable(mdcTraceId);
    }
}
```

### 自定义问题字段贡献者

为所有ProblemDetail响应添加自定义字段：

```java
@Component
public class CustomProblemFieldContributor implements ProblemFieldContributor {
    
    @Value("${spring.application.name}")
    private String serviceName;
    
    @Value("${app.version:unknown}")
    private String serviceVersion;
    
    @Override
    public void contribute(Map<String, Object> fields, Throwable exception) {
        fields.put("service", serviceName);
        fields.put("version", serviceVersion);
        fields.put("environment", getActiveProfile());
        
        // 添加异常特定字段
        if (exception instanceof YourCustomException customEx) {
            fields.put("customField", customEx.getCustomValue());
        }
    }
    
    private String getActiveProfile() {
        return System.getProperty("spring.profiles.active", "default");
    }
}
```

### Web特定字段贡献者

为Web响应添加请求特定字段：

```java
@Component
public class CustomWebProblemFieldContributor implements WebProblemFieldContributor {
    
    @Override
    public void contribute(Map<String, Object> fields, Throwable exception, HttpServletRequest request) {
        // 添加请求信息
        fields.put("method", request.getMethod());
        fields.put("userAgent", request.getHeader("User-Agent"));
        fields.put("clientIp", extractClientIp(request));
        
        // 如果可用，添加用户上下文
        String userId = request.getHeader("X-User-ID");
        if (userId != null) {
            fields.put("userId", userId);
        }
    }
    
    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
```

### 自定义验证错误格式化器

自定义验证错误格式化：

```java
@Component
public class CustomValidationErrorsFormatter implements ValidationErrorsFormatter {
    
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
        "password", "token", "secret", "key", "credential"
    );
    
    @Override
    public List<ValidationError> formatWithMasking(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
            .map(this::formatFieldError)
            .collect(Collectors.toList());
    }
    
    private ValidationError formatFieldError(FieldError error) {
        String field = error.getField();
        Object rejectedValue = maskSensitiveValue(field, error.getRejectedValue());
        String message = formatMessage(error);
        
        return new ValidationError(field, rejectedValue, message);
    }
    
    private Object maskSensitiveValue(String field, Object value) {
        if (value == null) return null;
        
        String fieldLower = field.toLowerCase();
        boolean isSensitive = SENSITIVE_FIELDS.stream()
            .anyMatch(fieldLower::contains);
        
        return isSensitive ? "***" : value;
    }
    
    private String formatMessage(FieldError error) {
        // 自定义消息格式化逻辑
        String defaultMessage = error.getDefaultMessage();
        
        // 如果消息中不包含字段名，则添加
        if (defaultMessage != null && !defaultMessage.contains(error.getField())) {
            return error.getField() + ": " + defaultMessage;
        }
        
        return defaultMessage;
    }
}
```

## 配置验证

### 必需属性验证

系统在启动时验证必需属性：

```yaml
# 这将导致启动失败
patra:
  error:
    enabled: true
    # context-prefix: REG  # 缺少必需属性！
```

错误消息：
```
***************************
APPLICATION FAILED TO START
***************************

Description:
Binding to target org.springframework.boot.context.properties.bind.BindException: Failed to bind properties under 'patra.error' to com.patra.starter.core.error.config.ErrorProperties

Reason: context-prefix is required but was not provided
```

### 属性类型验证

无效的属性类型在启动时被捕获：

```yaml
# 这将导致启动失败
patra:
  error:
    enabled: "invalid"  # 应该是boolean
    context-prefix: REG
```

### 配置测试

使用简单测试验证您的配置：

```java
@SpringBootTest
@TestPropertySource(properties = {
    "patra.error.context-prefix=TEST",
    "patra.web.problem.enabled=true",
    "patra.feign.problem.enabled=true"
})
class ConfigurationTest {
    
    @Autowired
    private ErrorProperties errorProperties;
    
    @Autowired
    private WebErrorProperties webErrorProperties;
    
    @Autowired
    private FeignErrorProperties feignErrorProperties;
    
    @Test
    void shouldLoadConfiguration() {
        assertThat(errorProperties.getContextPrefix()).isEqualTo("TEST");
        assertThat(errorProperties.isEnabled()).isTrue();
        
        assertThat(webErrorProperties.isEnabled()).isTrue();
        assertThat(webErrorProperties.getTypeBaseUrl()).isNotNull();
        
        assertThat(feignErrorProperties.isEnabled()).isTrue();
        assertThat(feignErrorProperties.isTolerant()).isTrue();
    }
}
```

## 配置故障排除

### 常见配置问题

1. **缺少context-prefix**：
   ```yaml
   patra:
     error:
       context-prefix: YOUR_PREFIX  # 必需！
   ```

2. **错误的属性名称**：
   ```yaml
   # 错误
   patra:
     error:
       contextPrefix: REG  # 应该是context-prefix
   
   # 正确
   patra:
     error:
       context-prefix: REG
   ```

3. **无效的boolean值**：
   ```yaml
   # 错误
   patra:
     error:
       enabled: "true"  # 应该是boolean，不是字符串
   
   # 正确
   patra:
     error:
       enabled: true
   ```

### 调试配置加载

启用配置调试日志：

```yaml
logging:
  level:
    org.springframework.boot.context.properties: DEBUG
    com.patra.starter.core.error.config: DEBUG
```

这将显示正在加载哪些属性及其值。