# Patra 错误处理系统 - 设计原理

## 设计理念

Patra 错误处理系统的设计基于以下核心理念：

### 1. 约定优于配置 (Convention over Configuration)
- **零配置启动** - 提供合理的默认配置，开箱即用
- **渐进式增强** - 支持按需自定义，不强制复杂配置
- **标准化约定** - 遵循行业标准和最佳实践

### 2. 清洁架构 (Clean Architecture)
- **依赖倒置** - 高层模块不依赖低层模块，都依赖抽象
- **关注点分离** - 错误处理逻辑与业务逻辑分离
- **框架无关** - 领域层不依赖任何框架

### 3. 领域驱动设计 (Domain-Driven Design)
- **语义化异常** - 异常名称反映业务语义
- **错误特征** - 使用特征接口表达错误语义
- **统一语言** - 错误代码与业务术语保持一致

## 架构决策

### 1. 分层架构设计

#### 依赖方向规则
```
Adapter Layer    → Application Layer + API Layer
Application Layer → Domain Layer + Contract Layer  
Infrastructure Layer → Domain Layer + Contract Layer
Domain Layer     → patra-common (仅限)
API Layer        → 无依赖 (纯契约)
Contract Layer   → 无框架依赖
```

#### 错误处理在各层的职责

**领域层 (Domain Layer)**
- 定义业务异常类型
- 实现错误特征接口
- 保持框架无关性

```java
// 领域层异常 - 无框架依赖
public class DictionaryNotFoundException extends RegistryNotFound {
    public DictionaryNotFoundException(String typeCode) {
        super("Dictionary type not found: " + typeCode);
    }
}
```

**应用层 (Application Layer)**
- 异常传播和转换
- 业务逻辑编排中的异常处理
- 事务边界内的异常管理

```java
// 应用层服务 - 异常传播
@Service
public class DictionaryAppService {
    
    public DictionaryItemQuery findItem(String typeCode, String itemCode) {
        // 让领域异常自然传播，由基础设施层处理
        DictionaryItem item = dictionaryDomainService.getItemWithValidation(typeCode, itemCode);
        return converter.toQuery(item);
    }
}
```

**适配器层 (Adapter Layer)**
- HTTP异常处理（通过全局处理器）
- Feign客户端错误解码
- 外部系统异常转换

```java
// 适配器层控制器 - 无需手动异常处理
@RestController
public class DictionaryController {
    
    @GetMapping("/dictionaries/{typeCode}/items/{itemCode}")
    public DictionaryItemDto getItem(@PathVariable String typeCode, @PathVariable String itemCode) {
        // 直接调用应用服务，异常自动处理
        DictionaryItemQuery query = dictionaryAppService.findItem(typeCode, itemCode);
        return converter.toDto(query);
    }
}
```

### 2. 错误解析策略

#### 确定性解析算法
系统采用确定性的错误解析算法，确保相同异常总是产生相同的错误代码：

```java
public class ErrorResolutionService {
    
    public ErrorResolution resolve(Throwable exception) {
        // 1. ApplicationException - 直接提取
        if (exception instanceof ApplicationException) {
            return resolveApplicationException((ApplicationException) exception);
        }
        
        // 2. ErrorMappingContributor - 显式映射
        Optional<ErrorCodeLike> contributorResult = tryErrorMappingContributors(exception);
        if (contributorResult.isPresent()) {
            return createResolution(contributorResult.get(), exception);
        }
        
        // 3. HasErrorTraits - 特征映射
        if (exception instanceof HasErrorTraits) {
            return resolveByTraits((HasErrorTraits) exception);
        }
        
        // 4. 命名约定 - 启发式映射
        Optional<ErrorCodeLike> heuristicResult = tryHeuristicMapping(exception);
        if (heuristicResult.isPresent()) {
            return createResolution(heuristicResult.get(), exception);
        }
        
        // 5. 默认回退
        return createDefaultResolution(exception);
    }
}
```

#### 缓存策略
- **类级别缓存** - 按异常类缓存解析结果
- **线程安全** - 使用ConcurrentHashMap确保并发安全
- **内存控制** - 限制缓存大小，防止内存泄露

### 3. HTTP状态码映射策略

#### 语义化映射
基于错误特征的语义化HTTP状态码映射：

```java
public enum ErrorTrait {
    NOT_FOUND(404),           // 资源未找到
    CONFLICT(409),            // 资源冲突
    RULE_VIOLATION(422),      // 业务规则违反
    UNAUTHORIZED(401),        // 未认证
    FORBIDDEN(403),           // 无权限
    RATE_LIMITED(429),        // 限流
    TIMEOUT(504);             // 超时
    
    private final int defaultHttpStatus;
}
```

#### 后缀启发式策略
对于未实现错误特征的异常，使用命名约定：

```java
public class SuffixHeuristicStatusMappingStrategy implements StatusMappingStrategy {
    
    @Override
    public int mapToHttpStatus(ErrorCodeLike errorCode, Throwable exception) {
        String className = exception.getClass().getSimpleName();
        
        if (className.contains("NotFound") || className.endsWith("NotFound")) {
            return 404;
        }
        
        if (className.contains("Conflict") || className.endsWith("Conflict") || 
            className.contains("AlreadyExists") || className.endsWith("AlreadyExists")) {
            return 409;
        }
        
        // 默认客户端错误
        return 422;
    }
}
```

### 4. 扩展点设计

#### SPI (Service Provider Interface) 模式
系统提供多个SPI接口，支持插件式扩展：

```java
// 异常映射扩展点
public interface ErrorMappingContributor {
    Optional<ErrorCodeLike> mapException(Throwable exception);
}

// 状态码映射扩展点  
public interface StatusMappingStrategy {
    int mapToHttpStatus(ErrorCodeLike errorCode, Throwable exception);
}

// 响应字段扩展点
public interface ProblemFieldContributor {
    void contribute(Map<String, Object> fields, Throwable exception);
}
```

#### 自动发现机制
使用Spring的自动配置机制发现和注册扩展：

```java
@Configuration
@EnableConfigurationProperties(ErrorProperties.class)
public class CoreErrorAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean
    public ErrorResolutionService errorResolutionService(
            List<ErrorMappingContributor> contributors,
            StatusMappingStrategy statusMappingStrategy) {
        return new ErrorResolutionService(contributors, statusMappingStrategy);
    }
}
```

## 技术决策

### 1. 为什么选择 RFC 7807 ProblemDetail？

#### 标准化优势
- **行业标准** - RFC 7807是IETF标准，广泛支持
- **结构化** - 提供标准字段和扩展机制
- **工具支持** - 主流HTTP客户端和工具支持

#### 扩展性
```json
{
  "type": "https://errors.patra.com/reg-1401",
  "title": "REG-1401", 
  "status": 404,
  "detail": "Dictionary type not found: COUNTRY",
  
  // 标准扩展字段
  "code": "REG-1401",
  "traceId": "abc123def456",
  "path": "/api/registry/dictionaries/COUNTRY",
  "timestamp": "2025-09-20T10:30:00Z",
  
  // 业务特定扩展字段
  "typeCode": "COUNTRY",
  "suggestions": ["COUNTRIES", "COUNTRY_CODE"]
}
```

### 2. 为什么使用错误特征 (Error Traits)？

#### 语义表达
错误特征提供了比HTTP状态码更丰富的语义表达：

```java
public class DictionaryNotFoundException extends RegistryNotFound implements HasErrorTraits {
    
    @Override
    public Set<ErrorTrait> getErrorTraits() {
        return Set.of(ErrorTrait.NOT_FOUND);
    }
}
```

#### 解耦合
- **业务语义与HTTP协议解耦** - 错误特征表达业务语义，HTTP状态码由框架映射
- **异常类型与状态码解耦** - 同一异常可以根据上下文映射不同状态码

### 3. 为什么采用分层的错误代码？

#### 可扩展性
```
REG-0xxx  → HTTP对齐的通用错误
REG-14xx  → 字典相关错误  
REG-15xx  → 注册表通用错误
REG-16xx  → 配置相关错误
```

#### 可读性
- **服务前缀** - 快速识别错误来源
- **功能分组** - 错误代码按功能领域分组
- **语义化** - 错误代码反映业务语义

### 4. 为什么选择自动配置？

#### 开发体验
- **零配置启动** - 添加依赖即可使用
- **约定优于配置** - 提供合理默认值
- **渐进式增强** - 支持按需自定义

#### 一致性
- **统一配置模式** - 所有starter使用相同配置模式
- **环境感知** - 根据环境自动调整配置
- **条件装配** - 根据条件启用/禁用功能

## 性能考虑

### 1. 错误解析性能

#### 缓存策略
```java
@Component
public class ErrorResolutionService {
    
    // 类级别缓存，避免重复解析
    private final Map<Class<?>, ErrorResolution> resolutionCache = new ConcurrentHashMap<>();
    
    public ErrorResolution resolve(Throwable exception) {
        Class<?> exceptionClass = exception.getClass();
        
        // 先查缓存
        ErrorResolution cached = resolutionCache.get(exceptionClass);
        if (cached != null) {
            return cached;
        }
        
        // 解析并缓存
        ErrorResolution resolution = doResolve(exception);
        resolutionCache.put(exceptionClass, resolution);
        return resolution;
    }
}
```

#### 原因链遍历优化
```java
private Throwable findRootCause(Throwable exception) {
    Throwable current = exception;
    int depth = 0;
    
    // 限制遍历深度，防止无限循环
    while (current.getCause() != null && depth < MAX_CAUSE_DEPTH) {
        current = current.getCause();
        depth++;
    }
    
    return current;
}
```

### 2. 内存使用优化

#### 对象复用
```java
@Component
public class ProblemDetailBuilder {
    
    // 复用构建器，减少对象创建
    private final ThreadLocal<ProblemDetail.Builder> builderCache = 
        ThreadLocal.withInitial(ProblemDetail::forStatus);
    
    public ProblemDetail build(ErrorResolution resolution, HttpServletRequest request) {
        ProblemDetail.Builder builder = builderCache.get();
        builder.clear(); // 清理上次使用状态
        
        return builder
            .withStatus(resolution.httpStatus())
            .withTitle(resolution.errorCode().code())
            // ... 其他字段
            .build();
    }
}
```

#### 响应大小控制
```java
public class DefaultValidationErrorsFormatter implements ValidationErrorsFormatter {
    
    private static final int MAX_VALIDATION_ERRORS = 100;
    
    @Override
    public List<ValidationError> formatWithMasking(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
            .limit(MAX_VALIDATION_ERRORS) // 限制错误数量
            .map(this::formatFieldError)
            .collect(Collectors.toList());
    }
}
```

## 安全考虑

### 1. 敏感信息保护

#### 自动掩码
```java
public class DefaultValidationErrorsFormatter {
    
    private static final Set<String> SENSITIVE_FIELDS = Set.of(
        "password", "token", "secret", "key", "credential"
    );
    
    private Object maskSensitiveValue(String field, Object value) {
        if (value == null) return null;
        
        String fieldLower = field.toLowerCase();
        boolean isSensitive = SENSITIVE_FIELDS.stream()
            .anyMatch(fieldLower::contains);
        
        return isSensitive ? "***" : value;
    }
}
```

#### 堆栈跟踪控制
```java
@ConditionalOnProperty(name = "patra.web.problem.include-stack", havingValue = "false", matchIfMissing = true)
public class ProductionProblemDetailBuilder implements ProblemDetailBuilder {
    
    @Override
    public ProblemDetail build(ErrorResolution resolution, HttpServletRequest request) {
        // 生产环境不包含堆栈跟踪
        return ProblemDetail.forStatus(resolution.httpStatus())
            .withTitle(resolution.errorCode().code())
            .withDetail(sanitizeMessage(resolution.message()))
            .build();
    }
}
```

### 2. 信息泄露防护

#### 错误消息净化
```java
private String sanitizeMessage(String message) {
    if (message == null) return null;
    
    // 移除可能的敏感信息
    return message
        .replaceAll("password=\\w+", "password=***")
        .replaceAll("token=\\w+", "token=***")
        .replaceAll("key=\\w+", "key=***");
}
```

## 测试策略

### 1. 单元测试
- **错误解析逻辑** - 测试各种异常的解析结果
- **状态码映射** - 测试HTTP状态码映射逻辑
- **缓存机制** - 测试缓存的正确性和性能

### 2. 集成测试
- **端到端流程** - 测试从异常抛出到ProblemDetail响应的完整流程
- **Feign错误解码** - 测试服务间调用的错误处理
- **配置验证** - 测试各种配置组合的正确性

### 3. 性能测试
- **错误解析性能** - 测试高并发下的错误解析性能
- **内存使用** - 测试长时间运行的内存使用情况
- **缓存效果** - 测试缓存对性能的提升效果

## 下一步

- **快速开始** - 查看 `../getting-started/quick-start.md`
- **系统概览** - 查看 `system-overview.md`
- **配置参考** - 查看 `../configuration/configuration-reference.md`
- **自定义扩展** - 查看 `../integration/customization-guide.md`