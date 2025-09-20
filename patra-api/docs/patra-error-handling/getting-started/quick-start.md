# Patra 错误处理系统 - 快速开始

## 5分钟快速集成

本指南将帮助您在5分钟内为现有Spring Boot服务集成Patra错误处理系统。

## 前置条件

- Spring Boot 3.2.4+
- Java 21+
- Maven 3.6+

## 步骤1：添加依赖

在您的服务的 `pom.xml` 中添加所需的starter依赖：

### 基础Web服务
```xml
<dependencies>
    <!-- 核心错误处理 -->
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-boot-starter-core</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>
    
    <!-- Web错误处理 -->
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-boot-starter-web</artifactId>
        <version>0.1.0-SNAPSHOT</version>
    </dependency>
</dependencies>
```

### 如果使用Feign客户端
```xml
<!-- Feign错误处理 -->
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-cloud-starter-feign</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 如果使用MyBatis
```xml
<!-- MyBatis错误处理 -->
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-mybatis</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## 步骤2：配置属性

在 `application.yml` 中添加基础配置：

```yaml
patra:
  error:
    enabled: true
    context-prefix: YOUR_SERVICE_PREFIX  # 例如：REG, ORD, INV, USR
  web:
    problem:
      enabled: true
      type-base-url: "https://errors.yourcompany.com/"
      include-stack: false  # 生产环境必须为false
  feign:
    problem:
      enabled: true
      tolerant: true
```

**重要**：`context-prefix` 是必需的，请为您的服务选择一个唯一的前缀：
- Registry服务：`REG`
- Order服务：`ORD`
- Inventory服务：`INV`
- User服务：`USR`
- Payment服务：`PAY`

## 步骤3：创建领域异常

在您的领域模块中创建异常层次结构：

### 基础异常类
```java
package com.yourcompany.yourservice.domain.exception;

import com.patra.common.error.DomainException;
import com.patra.common.error.trait.ErrorTrait;
import com.patra.common.error.trait.HasErrorTraits;
import java.util.Set;

// 服务基础异常
public abstract class YourServiceException extends DomainException {
    protected YourServiceException(String message) { super(message); }
    protected YourServiceException(String message, Throwable cause) { super(message, cause); }
}

// 资源未找到异常
public abstract class YourServiceNotFound extends YourServiceException implements HasErrorTraits {
    protected YourServiceNotFound(String message) { super(message); }
    
    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.NOT_FOUND);
    }
}

// 资源冲突异常
public abstract class YourServiceConflict extends YourServiceException implements HasErrorTraits {
    protected YourServiceConflict(String message) { super(message); }
    
    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.CONFLICT);
    }
}
```

### 具体异常类
```java
// 具体的业务异常
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

## 步骤4：创建错误代码枚举

在您的API模块中创建错误代码枚举：

```java
package com.yourcompany.yourservice.api.error;

import com.patra.common.error.codes.ErrorCodeLike;

public enum YourServiceErrorCode implements ErrorCodeLike {
    
    // HTTP对齐的通用代码
    YOUR_0400("YOUR-0400"), // Bad Request
    YOUR_0404("YOUR-0404"), // Not Found
    YOUR_0409("YOUR-0409"), // Conflict
    YOUR_0422("YOUR-0422"), // Unprocessable Entity
    YOUR_0500("YOUR-0500"), // Internal Server Error
    
    // 业务特定代码
    YOUR_1001("YOUR-1001"), // Resource Not Found
    YOUR_1002("YOUR-1002"), // Resource Already Exists
    YOUR_1003("YOUR-1003"), // Resource Validation Failed
    YOUR_1004("YOUR-1004"); // Resource Access Denied
    
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

## 步骤5：创建错误映射贡献者

在您的boot模块中创建错误映射贡献者：

```java
package com.yourcompany.yourservice.config;

import com.patra.common.error.codes.ErrorCodeLike;
import com.patra.starter.core.error.spi.ErrorMappingContributor;
import com.yourcompany.yourservice.api.error.YourServiceErrorCode;
import com.yourcompany.yourservice.domain.exception.*;
import org.springframework.stereotype.Component;
import java.util.Optional;

@Component
public class YourServiceErrorMappingContributor implements ErrorMappingContributor {
    
    @Override
    public Optional<ErrorCodeLike> mapException(Throwable exception) {
        
        if (exception instanceof ResourceNotFoundException) {
            return Optional.of(YourServiceErrorCode.YOUR_1001);
        }
        
        if (exception instanceof ResourceAlreadyExistsException) {
            return Optional.of(YourServiceErrorCode.YOUR_1002);
        }
        
        // 可以添加更多映射...
        
        return Optional.empty();
    }
}
```

## 步骤6：更新控制器

移除手动异常处理，让系统自动处理：

### 之前（手动处理）
```java
@RestController
public class ResourceController {
    
    @GetMapping("/resources/{id}")
    public ResponseEntity<?> getResource(@PathVariable String id) {
        try {
            ResourceDto resource = resourceService.getResource(id);
            return ResponseEntity.ok(resource);
        } catch (ResourceNotFoundException ex) {
            return ResponseEntity.notFound().build();
        } catch (Exception ex) {
            return ResponseEntity.status(500).body("Internal error");
        }
    }
}
```

### 之后（自动处理）
```java
@RestController
public class ResourceController {
    
    @GetMapping("/resources/{id}")
    public ResourceDto getResource(@PathVariable String id) {
        // 直接抛出异常，系统自动处理
        return resourceService.getResource(id);
        // ResourceNotFoundException 自动转换为：
        // {
        //   "type": "https://errors.yourcompany.com/your-1001",
        //   "title": "YOUR-1001",
        //   "status": 404,
        //   "detail": "Resource not found: test-id",
        //   "code": "YOUR-1001",
        //   "traceId": "abc123",
        //   "path": "/resources/test-id",
        //   "timestamp": "2025-09-20T10:30:00Z"
        // }
    }
}
```

## 步骤7：测试集成

启动应用并测试错误处理：

### 测试404错误
```bash
curl -X GET http://localhost:8080/resources/nonexistent \
  -H "Accept: application/json"
```

期望响应：
```json
{
  "type": "https://errors.yourcompany.com/your-1001",
  "title": "YOUR-1001",
  "status": 404,
  "detail": "Resource not found: nonexistent",
  "code": "YOUR-1001",
  "traceId": "abc123def456",
  "path": "/resources/nonexistent",
  "timestamp": "2025-09-20T10:30:00Z"
}
```

### 测试验证错误
```bash
curl -X POST http://localhost:8080/resources \
  -H "Content-Type: application/json" \
  -d "{}"
```

期望响应：
```json
{
  "type": "https://errors.yourcompany.com/your-0422",
  "title": "YOUR-0422",
  "status": 422,
  "detail": "Validation failed",
  "code": "YOUR-0422",
  "traceId": "abc123def456",
  "path": "/resources",
  "timestamp": "2025-09-20T10:30:00Z",
  "errors": [
    {
      "field": "name",
      "rejectedValue": null,
      "message": "Name is required"
    }
  ]
}
```

## 步骤8：Feign客户端集成（可选）

如果您的服务调用其他服务，更新Feign客户端错误处理：

### 之前（手动处理）
```java
@Service
public class IntegrationService {
    
    public Optional<ExternalResourceDto> getExternalResource(String id) {
        try {
            return Optional.of(externalClient.getResource(id));
        } catch (FeignException.NotFound ex) {
            return Optional.empty();
        } catch (FeignException ex) {
            throw new ServiceUnavailableException("External service error");
        }
    }
}
```

### 之后（自动处理）
```java
@Service
public class IntegrationService {
    
    public Optional<ExternalResourceDto> getExternalResource(String id) {
        try {
            return Optional.of(externalClient.getResource(id));
        } catch (RemoteCallException ex) {
            // 使用辅助方法检查错误类型
            if (RemoteErrorHelper.isNotFound(ex)) {
                return Optional.empty();
            }
            
            // 检查特定错误代码
            if (RemoteErrorHelper.is(ex, "EXT-1001")) {
                log.warn("Specific external error: {}", ex.getMessage());
                return Optional.empty();
            }
            
            // 检查服务器错误
            if (RemoteErrorHelper.isServerError(ex)) {
                throw new ServiceUnavailableException("External service error", ex);
            }
            
            throw ex; // 重新抛出客户端错误
        }
    }
}
```

## 完成！

恭喜！您已经成功集成了Patra错误处理系统。现在您的服务具备了：

✅ **自动异常处理** - 无需手动编写异常处理器  
✅ **标准化错误响应** - 符合RFC 7807的ProblemDetail格式  
✅ **类型化远程调用异常** - Feign客户端自动解码错误响应  
✅ **分布式链路追踪** - 自动传播trace ID  
✅ **语义化错误代码** - 业务友好的错误代码体系  

## 下一步

### 进阶配置
- **[配置参考](../configuration/configuration-reference.md)** - 了解所有配置选项
- **[自定义扩展](../integration/customization-guide.md)** - 学习如何自定义错误处理

### 最佳实践
- **[Registry集成示例](../integration/registry-integration.md)** - 查看完整的集成示例
- **[测试指南](../developer/testing-guide.md)** - 学习如何测试错误处理

### 故障排除
- **[故障排除指南](../developer/troubleshooting.md)** - 解决常见问题
- **[性能优化](../developer/performance-guide.md)** - 优化性能配置

## 常见问题

### Q: 为什么需要context-prefix？
A: context-prefix用于生成唯一的错误代码，避免不同服务之间的错误代码冲突。

### Q: 可以禁用某些功能吗？
A: 是的，所有功能都可以通过配置禁用：
```yaml
patra:
  error:
    enabled: false  # 禁用整个错误处理系统
  web:
    problem:
      enabled: false  # 仅禁用Web错误处理
  feign:
    problem:
      enabled: false  # 仅禁用Feign错误处理
```

### Q: 如何在开发环境启用堆栈跟踪？
A: 在开发环境配置中设置：
```yaml
patra:
  web:
    problem:
      include-stack: true  # 仅在开发环境使用
```

### Q: 如何添加自定义字段到错误响应？
A: 实现`ProblemFieldContributor`接口：
```java
@Component
public class CustomFieldContributor implements ProblemFieldContributor {
    @Override
    public void contribute(Map<String, Object> fields, Throwable exception) {
        fields.put("serviceVersion", "1.0.0");
    }
}
```