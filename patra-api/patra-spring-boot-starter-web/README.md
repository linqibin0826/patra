# patra-spring-boot-starter-web

## 概述

Web 层自动配置 Starter,为 REST API 提供统一的 HTTP 错误处理、参数验证、类型转换和响应封装能力。基于 RFC 7807 ProblemDetail 标准,提供一致的错误响应格式。

本 Starter 专为适配器层(Adapter Layer)设计,简化 REST 控制器的开发,自动处理常见的 Web 层关注点。

## 核心功能

- **RFC 7807 ProblemDetail**: 标准化的错误响应格式,符合 HTTP API 最佳实践
- **全局异常处理**: 自动捕获并转换异常为 HTTP 错误响应
- **参数验证**: 集成 JSR-303 Bean Validation,自动格式化验证错误
- **类型转换**: 自动转换请求参数到领域类型(如 ProvenanceCode)
- **统一响应模型**: ApiResponse、PageResult 等标准响应封装

## 自动配置内容

### WebConversionAutoConfiguration
自动注册 Web 层类型转换器:
- `provenanceCodeConverter`: String → ProvenanceCode 转换器,支持 `@PathVariable` 和 `@RequestParam` 自动绑定

### WebErrorAutoConfiguration
配置 Web 层错误处理组件:
- `GlobalRestExceptionHandler`: 全局异常处理器(@RestControllerAdvice)
- `ProblemDetailAdapter`: 异常到 ProblemDetail 的适配器
- `ProblemDetailBuilder`: ProblemDetail 构建器,支持扩展字段
- `ValidationErrorsFormatter`: 验证错误格式化器,支持敏感信息脱敏

## 主要组件

### GlobalRestExceptionHandler
全局 REST 异常处理器,捕获所有未处理的异常并转换为 RFC 7807 ProblemDetail 响应:
- 处理通用异常(`Exception.class`)
- 特殊处理 Bean Validation 异常(`MethodArgumentNotValidException`)
- 自动记录错误日志(包含追踪 ID、请求路径等上下文信息)
- 限制验证错误最大返回数量(100 个)

### ProblemDetailBuilder
构建符合 RFC 7807 标准的错误响应:
- 自动包含追踪 ID(来自 TraceProvider)
- 支持通过 `ProblemFieldContributor` 和 `WebProblemFieldContributor` 扩展字段
- 生成错误类型 URI(基于 `type-base-url` 配置)
- 可选包含堆栈跟踪(仅用于开发/调试)

### ApiResponse / PageResult
统一的响应封装模型:
```java
// 标准响应
ApiResponse.ok(data);                           // 成功响应
ApiResponse.failure(ResultCode.ERROR, message); // 业务失败
ApiResponse.error(500, message);                // 错误响应

// 分页响应
PageResult.of(items, total, page, size);
```

## 扩展点

### 1. WebProblemFieldContributor (SPI)
向 ProblemDetail 添加 Web 特定的扩展字段:
```java
@Component
public class CustomFieldContributor implements WebProblemFieldContributor {
    @Override
    public void contribute(Map<String, Object> fields, Throwable exception,
                          HttpServletRequest request) {
        fields.put("requestId", request.getHeader("X-Request-ID"));
        fields.put("clientVersion", request.getHeader("X-Client-Version"));
        fields.put("path", request.getRequestURI());
    }
}
```

### 2. ValidationErrorsFormatter (SPI)
自定义验证错误格式化和脱敏逻辑:
```java
@Component
public class MyValidationErrorsFormatter implements ValidationErrorsFormatter {
    @Override
    public List<ValidationError> formatWithMasking(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
            .map(error -> new ValidationError(
                error.getField(),
                maskSensitiveMessage(error.getDefaultMessage())
            ))
            .toList();
    }
}
```

### 3. 类型转换器
注册自定义 Spring Converter:
```java
@Component
public class MyConverter implements Converter<String, MyDomainType> {
    @Override
    public MyDomainType convert(String source) {
        return MyDomainType.parse(source);
    }
}
```

## 配置属性

**配置前缀**: `patra.web.problem`

```yaml
patra:
  web:
    problem:
      enabled: true                                    # 是否启用 Web 错误处理
      type-base-url: https://errors.example.com/       # ProblemDetail type URI 基础 URL
      include-stack: false                             # 是否包含堆栈跟踪(生产环境应为 false)
```

## 使用方式

### Maven 依赖
```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-boot-starter-web</artifactId>
</dependency>
```

**传递依赖**(自动包含):
- `patra-spring-boot-starter-core`: 核心错误处理管道
- `patra-common-core`: 领域基础类
- `spring-boot-starter-web`: Spring MVC
- `spring-boot-starter-validation`: Bean Validation (JSR-303)

### 配置示例

```yaml
patra:
  web:
    problem:
      enabled: true
      type-base-url: https://api.papertrace.com/errors/
      include-stack: false

  error:
    context-prefix: INGEST
    observation:
      enabled: true
```

### 代码示例

**REST 控制器**:
```java
@RestController
@RequestMapping("/api/plans")
public class PlanController {
    private final PlanQueryService queryService;
    private final PlanIngestionOrchestrator orchestrator;

    // 自动验证请求参数
    @PostMapping
    public ApiResponse<PlanResponse> create(@Valid @RequestBody CreatePlanRequest request) {
        PlanAggregate plan = orchestrator.ingest(request.toCommand());
        return ApiResponse.ok(PlanResponse.from(plan));
    }

    // ProvenanceCode 自动转换
    @GetMapping("/by-source/{provenanceCode}")
    public ApiResponse<List<PlanResponse>> listBySource(
        @PathVariable ProvenanceCode provenanceCode  // 自动从 String 转换
    ) {
        List<PlanAggregate> plans = queryService.listBySource(provenanceCode);
        return ApiResponse.ok(plans.stream()
            .map(PlanResponse::from)
            .toList());
    }
}
```

**请求/响应模型**:
```java
public record CreatePlanRequest(
    @NotNull(message = "Provenance code is required")
    ProvenanceCode provenanceCode,

    @NotBlank(message = "External ID is required")
    String externalId
) {
    public PlanIngestionCommand toCommand() {
        return new PlanIngestionCommand(provenanceCode, externalId);
    }
}
```

**错误响应示例**(RFC 7807 ProblemDetail):
```json
{
  "type": "https://api.papertrace.com/errors/plan-not-found",
  "title": "Not Found",
  "status": 404,
  "detail": "Plan not found: 123",
  "instance": "/api/plans/123",
  "traceId": "abc123def456",
  "timestamp": "2025-01-12T10:30:45.123Z"
}
```

**验证错误响应示例**:
```json
{
  "type": "https://api.papertrace.com/errors/validation-failed",
  "title": "Bad Request",
  "status": 400,
  "detail": "Validation failed",
  "instance": "/api/plans",
  "traceId": "xyz789",
  "errors": [
    {
      "field": "provenanceCode",
      "message": "Provenance code is required"
    },
    {
      "field": "externalId",
      "message": "External ID is required"
    }
  ]
}
```

## 技术栈

- **Spring Boot**: 3.5.7
- **Spring MVC**: 6.2.2
- **Hibernate Validator**: 8.0.2 (JSR-303 实现)
- **Jackson**: 2.18.2

---

**最后更新**: 2025-01-12
**维护者**: Papertrace Team
