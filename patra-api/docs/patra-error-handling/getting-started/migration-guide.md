# Patra 错误处理系统 - 迁移指南

本指南提供从现有手动异常处理迁移到 Patra 错误处理系统的详细步骤说明。

## 目录

1. [前置条件](#前置条件)
2. [迁移概览](#迁移概览)
3. [分步迁移指南](#分步迁移指南)
4. [Registry 服务迁移示例](#registry-服务迁移示例)
5. [迁移测试](#迁移测试)
6. [常见迁移问题](#常见迁移问题)
7. [回滚策略](#回滚策略)

## 前置条件

开始迁移前，请确保您具备：

- Spring Boot 3.2.4 或更高版本
- Java 21 或更高版本
- 现有服务使用 Spring Web 和/或 Feign 客户端
- 有权限修改 POM 文件和配置

## 迁移概览

迁移过程包括以下步骤：

1. **添加 starter 依赖** 到您的服务
2. **配置错误处理属性**
3. **创建领域异常层次结构**（如果不存在）
4. **实现错误代码目录** 为您的服务
5. **移除手动异常处理器**
6. **更新 Feign 客户端** 使用 RemoteCallException
7. **全面测试迁移**

### 迁移时间线

- **阶段1**：添加依赖和基础配置（1-2小时）
- **阶段2**：创建领域异常和错误代码（2-4小时）
- **阶段3**：移除手动处理器和更新客户端（1-2小时）
- **阶段4**：测试和验证（2-4小时）

**总预估时间**：每个服务 6-12 小时

## 分步迁移指南

### 步骤1：添加 Starter 依赖

在您的服务的 POM 文件中添加所需的 starter：

```xml
<dependencies>
    <!-- 核心错误处理 -->
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-boot-starter-core</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>
    
    <!-- Web错误处理（用于REST API） -->
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-boot-starter-web</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>
    
    <!-- Feign错误处理（如果使用Feign客户端） -->
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-cloud-starter-feign</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>
    
    <!-- MyBatis错误处理（如果使用MyBatis） -->
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-boot-starter-mybatis</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

### 步骤2：配置基础属性

在您的 `application.yml` 中添加错误处理配置：

```yaml
patra:
  error:
    enabled: true
    context-prefix: YOUR_SERVICE_PREFIX  # 例如：REG, ORD, INV
  web:
    problem:
      enabled: true
      type-base-url: "https://errors.yourcompany.com/"
      include-stack: false
  feign:
    problem:
      enabled: true
      tolerant: true
```

**选择您的上下文前缀**：
- Registry 服务：`REG`
- Order 服务：`ORD`
- Inventory 服务：`INV`
- User 服务：`USR`
- Payment 服务：`PAY`

### 步骤3：创建领域异常层次结构

在您的领域模块中创建语义化基础异常：

```java
// 服务基础异常
public abstract class YourServiceException extends DomainException {
    protected YourServiceException(String message) { super(message); }
    protected YourServiceException(String message, Throwable cause) { super(message, cause); }
}

// 语义化基础异常
public abstract class YourServiceNotFound extends YourServiceException implements HasErrorTraits {
    protected YourServiceNotFound(String message) { super(message); }
    
    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.NOT_FOUND);
    }
}

public abstract class YourServiceConflict extends YourServiceException implements HasErrorTraits {
    protected YourServiceConflict(String message) { super(message); }
    
    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.CONFLICT);
    }
}

public abstract class YourServiceRuleViolation extends YourServiceException implements HasErrorTraits {
    protected YourServiceRuleViolation(String message) { super(message); }
    
    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.RULE_VIOLATION);
    }
}
```

创建具体的领域异常：

```java
// 具体异常
public class ResourceNotFoundException extends YourServiceNotFound {
    private final String resourceId;
    
    public ResourceNotFoundException(String resourceId) {
        super("Resource not found: " + resourceId);
        this.resourceId = resourceId;
    }
    
    public String getResourceId() { return resourceId; }
}

public class ResourceAlreadyExistsException extends YourServiceConflict {
    private final String resourceId;
    
    public ResourceAlreadyExistsException(String resourceId) {
        super("Resource already exists: " + resourceId);
        this.resourceId = resourceId;
    }
    
    public String getResourceId() { return resourceId; }
}
```

### 步骤4：创建错误代码目录

在您的 API 模块中创建错误代码枚举：

```java
public enum YourServiceErrorCode implements ErrorCodeLike {
    
    // HTTP对齐的通用代码（0xxx系列）
    YOUR_0400("YOUR-0400"), // Bad Request
    YOUR_0401("YOUR-0401"), // Unauthorized
    YOUR_0403("YOUR-0403"), // Forbidden
    YOUR_0404("YOUR-0404"), // Not Found
    YOUR_0409("YOUR-0409"), // Conflict
    YOUR_0422("YOUR-0422"), // Unprocessable Entity
    YOUR_0429("YOUR-0429"), // Too Many Requests
    YOUR_0500("YOUR-0500"), // Internal Server Error
    YOUR_0503("YOUR-0503"), // Service Unavailable
    YOUR_0504("YOUR-0504"), // Gateway Timeout
    
    // 业务特定代码（1xxx系列）
    YOUR_1001("YOUR-1001"), // Resource Not Found
    YOUR_1002("YOUR-1002"), // Resource Already Exists
    YOUR_1003("YOUR-1003"), // Resource Validation Failed
    YOUR_1004("YOUR-1004"), // Resource Access Denied
    YOUR_1005("YOUR-1005"); // Resource Quota Exceeded
    
    private final String code;
    
    YourServiceErrorCode(String code) {
        this.code = code;
    }
    
    @Override
    public String code() {
        return code;
    }
    
    @Override
    public String toString() {
        return code;
    }
}
```

### 步骤5：创建错误映射贡献者

在您的 boot 模块中创建自定义错误映射贡献者：

```java
@Component
public class YourServiceErrorMappingContributor implements ErrorMappingContributor {
    
    @Override
    public Optional<ErrorCodeLike> mapException(Throwable exception) {
        // 映射特定的领域异常到错误代码
        if (exception instanceof ResourceNotFoundException) {
            return Optional.of(YourServiceErrorCode.YOUR_1001);
        }
        
        if (exception instanceof ResourceAlreadyExistsException) {
            return Optional.of(YourServiceErrorCode.YOUR_1002);
        }
        
        if (exception instanceof ResourceValidationException) {
            return Optional.of(YourServiceErrorCode.YOUR_1003);
        }
        
        // 映射数据层异常
        if (exception instanceof DuplicateKeyException) {
            return Optional.of(YourServiceErrorCode.YOUR_1002);
        }
        
        if (exception instanceof DataIntegrityViolationException) {
            return Optional.of(YourServiceErrorCode.YOUR_1003);
        }
        
        return Optional.empty();
    }
}
```

### 步骤6：移除手动异常处理器

**迁移前**（移除这些）：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(ResourceNotFoundException ex) {
        ErrorResponse error = new ErrorResponse("RESOURCE_NOT_FOUND", ex.getMessage());
        return ResponseEntity.status(404).body(error);
    }
    
    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ResourceAlreadyExistsException ex) {
        ErrorResponse error = new ErrorResponse("RESOURCE_CONFLICT", ex.getMessage());
        return ResponseEntity.status(409).body(error);
    }
    
    // 移除所有手动异常处理器
}
```

**迁移后**（自动处理）：

```java
@RestController
@RequestMapping("/api/yourservice")
public class ResourceController {
    
    @GetMapping("/resources/{id}")
    public ResourceDto getResource(@PathVariable String id) {
        // 直接抛出领域异常 - 自动处理
        throw new ResourceNotFoundException(id);
        // 自动转换为：
        // {
        //   "type": "https://errors.yourcompany.com/your-1001",
        //   "title": "YOUR-1001",
        //   "status": 404,
        //   "detail": "Resource not found: test-id",
        //   "code": "YOUR-1001",
        //   "traceId": "abc123",
        //   "path": "/api/yourservice/resources/test-id",
        //   "timestamp": "2025-09-20T10:30:00Z"
        // }
    }
}
```

### 步骤7：更新 Feign 客户端

**迁移前**：

```java
@Service
public class IntegrationService {
    
    private final ExternalServiceClient client;
    
    public Optional<ResourceDto> getResourceIfExists(String id) {
        try {
            return Optional.of(client.getResource(id));
        } catch (FeignException.NotFound ex) {
            return Optional.empty();
        } catch (FeignException ex) {
            log.error("External service error: {}", ex.getMessage());
            throw new ServiceUnavailableException("External service unavailable");
        }
    }
}
```

**迁移后**：

```java
@Service
public class IntegrationService {
    
    private final ExternalServiceClient client;
    
    public Optional<ResourceDto> getResourceIfExists(String id) {
        try {
            return Optional.of(client.getResource(id));
        } catch (RemoteCallException ex) {
            if (RemoteErrorHelper.isNotFound(ex)) {
                return Optional.empty();
            }
            
            if (RemoteErrorHelper.is(ex, "EXT-1001")) {
                log.warn("Specific external error: {}", ex.getMessage());
                return Optional.empty();
            }
            
            if (RemoteErrorHelper.isServerError(ex)) {
                log.error("External service error: traceId={}, error={}", 
                         ex.getTraceId(), ex.getMessage());
                throw new ServiceUnavailableException("External service unavailable");
            }
            
            throw ex; // 重新抛出客户端错误
        }
    }
}
```

## Registry 服务迁移示例

以下是 Registry 服务的完整迁移示例：

### 1. 依赖配置 (patra-registry-boot/pom.xml)

```xml
<dependencies>
    <!-- 添加错误处理 starters -->
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-boot-starter-core</artifactId>
    </dependency>
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-boot-starter-web</artifactId>
    </dependency>
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-boot-starter-mybatis</artifactId>
    </dependency>
    
    <!-- 现有依赖 -->
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-registry-adapter</artifactId>
    </dependency>
    <!-- ... 其他依赖 ... -->
</dependencies>
```

### 2. 配置 (application.yml)

```yaml
patra:
  error:
    enabled: true
    context-prefix: REG
  web:
    problem:
      enabled: true
      type-base-url: "https://errors.patra.com/"
      include-stack: false

spring:
  application:
    name: patra-registry
  jackson:
    serialization:
      write-dates-as-timestamps: false
    deserialization:
      fail-on-unknown-properties: false
```

### 3. 领域异常（已实现）

Registry 服务已经有完整的领域异常层次结构：

```java
// 基础异常
public abstract class RegistryException extends DomainException
public abstract class RegistryNotFound extends RegistryException implements HasErrorTraits
public abstract class RegistryConflict extends RegistryException implements HasErrorTraits

// 具体异常
public class DictionaryNotFoundException extends RegistryNotFound
public class DictionaryTypeAlreadyExists extends RegistryConflict
// ... 其他异常
```

### 4. 错误代码目录（已实现）

```java
public enum RegistryErrorCode implements ErrorCodeLike {
    // 通用代码
    REG_0400("REG-0400"), REG_0404("REG-0404"), REG_0409("REG-0409"),
    
    // 业务代码
    REG_1401("REG-1401"), // Dictionary Type Not Found
    REG_1402("REG-1402"), // Dictionary Item Not Found
    REG_1404("REG-1404"), // Dictionary Type Already Exists
    // ... 其他代码
}
```

### 5. 错误映射贡献者（已实现）

```java
@Component
public class RegistryErrorMappingContributor implements ErrorMappingContributor {
    
    @Override
    public Optional<ErrorCodeLike> mapException(Throwable exception) {
        // 字典异常
        if (exception instanceof DictionaryNotFoundException ex) {
            return ex.getItemCode() != null 
                ? Optional.of(RegistryErrorCode.REG_1402)  // Item not found
                : Optional.of(RegistryErrorCode.REG_1401); // Type not found
        }
        
        if (exception instanceof DictionaryTypeAlreadyExists) {
            return Optional.of(RegistryErrorCode.REG_1404);
        }
        
        // ... 其他映射
        
        return Optional.empty();
    }
}
```

## 迁移测试

### 1. 单元测试

更新您的单元测试以验证错误处理：

```java
@SpringBootTest
@TestPropertySource(properties = {
    "patra.error.context-prefix=TEST",
    "patra.web.problem.enabled=true"
})
class ErrorHandlingMigrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void shouldReturnProblemDetailForDomainException() throws Exception {
        mockMvc.perform(get("/api/test/not-found"))
            .andExpect(status().isNotFound())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.type").value("https://errors.yourcompany.com/test-1001"))
            .andExpect(jsonPath("$.title").value("TEST-1001"))
            .andExpect(jsonPath("$.status").value(404))
            .andExpect(jsonPath("$.code").value("TEST-1001"))
            .andExpect(jsonPath("$.traceId").exists())
            .andExpect(jsonPath("$.path").value("/api/test/not-found"))
            .andExpect(jsonPath("$.timestamp").exists());
    }
}
```

### 2. 集成测试

测试完整的错误处理流程：

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ErrorHandlingIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void shouldHandleNotFoundError() {
        ResponseEntity<ProblemDetail> response = restTemplate.getForEntity(
            "/api/test/resources/nonexistent", 
            ProblemDetail.class
        );
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getHeaders().getContentType())
            .isEqualTo(MediaType.APPLICATION_PROBLEM_JSON);
        
        ProblemDetail problem = response.getBody();
        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getProperties().get("code")).isEqualTo("TEST-1001");
        assertThat(problem.getProperties().get("traceId")).isNotNull();
    }
}
```

## 常见迁移问题

### 问题1：缺少上下文前缀

**错误**：`IllegalArgumentException: context-prefix is required`

**解决方案**：在配置中添加上下文前缀：

```yaml
patra:
  error:
    context-prefix: YOUR_PREFIX  # 必需！
```

### 问题2：现有异常处理器干扰

**错误**：手动处理器仍在被调用而不是自动处理

**解决方案**：移除或禁用现有的 `@RestControllerAdvice` 类：

```java
// 选项1：完全移除类
// @RestControllerAdvice  // 移除此注解
public class OldExceptionHandler {
    // 移除或注释掉
}

// 选项2：条件禁用
@RestControllerAdvice
@ConditionalOnProperty(name = "patra.web.problem.enabled", havingValue = "false")
public class OldExceptionHandler {
    // 仅在新系统禁用时激活
}
```

### 问题3：错误的HTTP状态码

**错误**：得到422而不是期望的状态码

**解决方案**：实现适当的错误特征或自定义状态映射：

```java
// 选项1：为领域异常添加错误特征
public class ResourceNotFoundException extends YourServiceNotFound implements HasErrorTraits {
    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.NOT_FOUND);  // 映射到404
    }
}

// 选项2：自定义状态映射策略
@Component
public class CustomStatusMappingStrategy implements StatusMappingStrategy {
    @Override
    public int mapToHttpStatus(ErrorCodeLike errorCode, Throwable exception) {
        if (errorCode.code().endsWith("-1001")) {
            return 404;
        }
        return 422; // 默认
    }
}
```

## 回滚策略

如果需要回滚迁移：

### 1. 快速回滚（禁用新系统）

```yaml
patra:
  error:
    enabled: false
  web:
    problem:
      enabled: false
  feign:
    problem:
      enabled: false
```

### 2. 渐进式回滚

1. **重新启用旧异常处理器**：

```java
@RestControllerAdvice
@ConditionalOnProperty(name = "patra.web.problem.enabled", havingValue = "false", matchIfMissing = true)
public class OldExceptionHandler {
    // 恢复旧处理器
}
```

2. **更新 Feign 客户端** 重新使用 `FeignException`
3. **从 POM 移除 starter 依赖**
4. **恢复旧配置**

## 迁移后检查清单

- [ ] 所有 starter 依赖已添加和配置
- [ ] 上下文前缀配置正确
- [ ] 领域异常层次结构已创建并具有适当特征
- [ ] 错误代码目录已实现和文档化
- [ ] 错误映射贡献者已实现
- [ ] 手动异常处理器已移除或禁用
- [ ] Feign 客户端已更新使用 RemoteCallException
- [ ] 单元测试已更新并通过
- [ ] 集成测试已更新并通过
- [ ] 错误响应在开发环境中已验证
- [ ] Trace ID 传播正常工作
- [ ] 性能影响已评估（应该是最小的）
- [ ] 团队成员文档已更新
- [ ] 监控和告警已更新为新错误格式

迁移应该是平滑的，并在一致的错误处理和更好的可观察性方面提供即时好处。