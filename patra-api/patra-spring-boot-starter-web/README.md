# Patra Spring Boot Starter - Web

Web 层 Starter，提供统一的 REST API 错误处理和类型转换支持。

## 模块概述

本模块提供 Web 层基础设施，基于 RFC 7807 Problem Details 标准构建，支持：

- **统一错误处理**：全局异常处理器，将所有异常转换为标准化的 ProblemDetail 响应
- **验证错误格式化**：敏感字段掩码，验证错误列表格式化
- **类型转换**：自定义参数绑定转换器（如 ProvenanceCode）

## 核心功能

### 自动配置

| 配置类 | 功能 | 条件 |
|--------|------|------|
| `WebErrorAutoConfiguration` | 错误处理核心配置，创建全局异常处理器、验证格式化器、ProblemDetail 构建器 | Servlet Web 应用，`patra.web.problem.enabled=true`（默认启用） |
| `WebConversionAutoConfiguration` | 注册类型转换器（String → ProvenanceCode） | Converter 类存在 |

### 异常处理流程

```
异常发生
    ↓
GlobalRestExceptionHandler 捕获
    ↓
ProblemDetailAdapter 转换
    ├── ErrorResolutionPipeline（核心模块）解析错误码和 HTTP 状态
    └── ProblemDetailBuilder 构建 RFC 7807 响应
    ↓
返回 application/problem+json 响应
```

### 组件说明

| 组件 | 职责 |
|------|------|
| `GlobalRestExceptionHandler` | 全局异常处理器，拦截所有 REST 异常 |
| `ProblemDetailAdapter` | 异常到 ProblemDetail 的转换适配器 |
| `ProblemDetailBuilder` | RFC 7807 响应构建器，支持字段贡献器扩展 |
| `ValidationErrorsFormatter` | 验证错误格式化 SPI，支持敏感字段掩码 |

### SPI 扩展点

| SPI | 功能 |
|-----|------|
| `ValidationErrorsFormatter` | 自定义验证错误格式化逻辑 |
| `WebProblemFieldContributor` | 向 ProblemDetail 贡献 Web 特定扩展字段 |

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-web</artifactId>
</dependency>
```

### 2. 配置属性

```yaml
patra:
  web:
    problem:
      enabled: true                              # 是否启用错误处理（默认 true）
      type-base-url: https://errors.example.com/ # ProblemDetail type URI 基础 URL
      include-stack: false                       # 是否在响应中包含堆栈跟踪（仅调试）
```

### 3. 响应格式示例

**通用异常响应：**

```json
{
  "type": "https://errors.example.com/err_internal_error",
  "title": "ERR_INTERNAL_ERROR",
  "status": 500,
  "detail": "An unexpected error occurred",
  "code": "ERR_INTERNAL_ERROR",
  "path": "/api/users",
  "timestamp": "2024-01-15T10:30:00.000Z",
  "traceId": "abc123def456"
}
```

**验证异常响应：**

```json
{
  "type": "https://errors.example.com/err_validation_failed",
  "title": "ERR_VALIDATION_FAILED",
  "status": 400,
  "detail": "Validation failed for object='userRequest'",
  "code": "ERR_VALIDATION_FAILED",
  "path": "/api/users",
  "timestamp": "2024-01-15T10:30:00.000Z",
  "errors": [
    { "field": "email", "rejectedValue": "invalid", "message": "must be a valid email" },
    { "field": "password", "rejectedValue": "***", "message": "size must be between 8 and 32" }
  ]
}
```

## 配置参考

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `patra.web.problem.enabled` | boolean | true | 是否启用 Web 错误处理 |
| `patra.web.problem.type-base-url` | String | https://errors.example.com/ | ProblemDetail type URI 基础 URL |
| `patra.web.problem.include-stack` | boolean | false | 是否在响应中包含堆栈跟踪 |

## 安全特性

### 敏感数据掩码

`ProblemDetailBuilder` 自动对错误消息中的敏感数据进行掩码：

**掩码规则：**
- `password=xxx` → `password=***`
- `token=xxx` → `token=***`
- `secret=xxx` → `secret=***`
- `key=xxx` → `key=***`

### 验证错误掩码

`DefaultValidationErrorsFormatter` 对敏感字段的 `rejectedValue` 进行掩码，防止密码等敏感信息泄露到响应中。

### 代理感知路径提取

支持从反向代理头中提取原始请求路径，优先级：
1. `Forwarded` 头（RFC 7239）
2. `X-Forwarded-Path` 头
3. `X-Forwarded-Uri` 头
4. `request.getRequestURI()`

## 扩展示例

### 自定义 Web 字段贡献器

```java
@Component
public class CustomWebFieldContributor implements WebProblemFieldContributor {

    @Override
    public void contribute(Map<String, Object> fields, Throwable exception,
                          HttpServletRequest request) {
        fields.put("requestId", request.getHeader("X-Request-Id"));
        fields.put("userAgent", request.getHeader("User-Agent"));
    }
}
```

### 自定义验证错误格式化器

```java
@Component
public class CustomValidationErrorsFormatter implements ValidationErrorsFormatter {

    @Override
    public List<ValidationError> formatWithMasking(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
            .map(error -> new ValidationError(
                error.getField(),
                maskValue(error.getField(), error.getRejectedValue()),
                error.getDefaultMessage()
            ))
            .toList();
    }

    private Object maskValue(String field, Object value) {
        if (field.toLowerCase().contains("password") ||
            field.toLowerCase().contains("secret")) {
            return "***";
        }
        return value;
    }
}
```

## 依赖关系

```
patra-spring-boot-starter-web
├── patra-common-core                    # 公共工具、错误码定义
├── patra-spring-boot-starter-core       # 核心 Starter（ErrorResolutionPipeline）
├── spring-boot-starter-web              # Spring Web MVC
└── spring-boot-starter-validation       # Bean Validation
```

## 包结构

```
com.patra.starter.web
├── autoconfig/
│   └── WebConversionAutoConfiguration   # 类型转换器自动配置
└── error/
    ├── adapter/
    │   ├── DefaultProblemDetailAdapter  # 默认适配器实现
    │   ├── ProblemDetailAdapter         # 适配器接口
    │   └── model/
    │       └── ProblemDetailResponse    # 响应封装
    ├── builder/
    │   └── ProblemDetailBuilder         # RFC 7807 响应构建器
    ├── config/
    │   ├── WebErrorAutoConfiguration    # 错误处理自动配置
    │   └── WebErrorProperties           # 配置属性
    ├── formatter/
    │   └── DefaultValidationErrorsFormatter  # 默认验证格式化器
    ├── handler/
    │   └── GlobalRestExceptionHandler   # 全局异常处理器
    ├── model/
    │   └── ValidationError              # 验证错误模型
    ├── spi/
    │   ├── ValidationErrorsFormatter    # 验证格式化 SPI
    │   └── WebProblemFieldContributor   # Web 字段贡献器 SPI
    └── util/
        └── HttpStatusConverter          # HTTP 状态码转换工具
```

## 设计原则

1. **RFC 7807 标准**：完全遵循 Problem Details for HTTP APIs 标准
2. **零配置启用**：添加依赖即可使用，无需额外配置
3. **安全优先**：敏感数据自动掩码，防止信息泄露
4. **可扩展性**：通过 SPI 支持自定义字段贡献器和格式化器
5. **与核心模块集成**：复用 `patra-spring-boot-starter-core` 的错误解析管道
