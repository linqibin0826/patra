# 错误解析框架 (Error Resolution Framework)

## 概述

分布式微服务系统中的统一错误处理框架，负责将任意异常转换为标准化的错误表示（`ErrorResolution`），支持多策略解析、可插拔扩展和生产级性能优化。

**设计目标：**

- **统一错误语义**：跨服务的一致错误码格式（`{SERVICE}-{0xxx}`）
- **多策略解析**：支持显式错误码、语义特征、命名启发式、原因链递归等多种解析策略
- **高性能**：异常类型缓存（缓存命中时性能提升 95%+）
- **可扩展**：通过 SPI 接口支持业务自定义映射
- **可观测**：慢解析检测、缓存统计、解析策略追踪
- **弹性保护**：可选的熔断器保护，防止级联故障

## 架构

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        错误解析管道 (Pipeline)                            │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                         │
│   异常输入                                                               │
│      │                                                                  │
│      ▼                                                                  │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │              拦截器链 (按 @Order 排序)                            │   │
│   │  ┌───────────────────┐  ┌───────────────────┐                   │   │
│   │  │CircuitBreaker     │→ │ ... (自定义拦截器) │                   │   │
│   │  │Interceptor        │  │                   │                   │   │
│   │  └───────────────────┘  └───────────────────┘                   │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│      │                                                                  │
│      ▼                                                                  │
│   ┌─────────────────────────────────────────────────────────────────┐   │
│   │           错误解析引擎 (ErrorResolutionEngine)                    │   │
│   │                                                                 │   │
│   │   解析策略（按优先级）：                                           │   │
│   │   ┌──────────────────────────────────────────────────────────┐  │   │
│   │   │ 1. Cache           │ 缓存命中直接返回（95%+ 性能提升）     │  │   │
│   │   │ 2. ApplicationException │ 直接获取 ErrorCodeLike        │  │   │
│   │   │ 3. Contributor     │ SPI 扩展映射（按 @Order 执行）       │  │   │
│   │   │ 4. Trait           │ 语义特征映射（HasErrorTraits）       │  │   │
│   │   │ 5. Naming          │ 类名后缀启发式（最长匹配）            │  │   │
│   │   │ 6. Cause           │ 原因链递归（智能跳过包装异常）        │  │   │
│   │   │ 7. Fallback        │ 回退（客户端 422 / 服务器 500）      │  │   │
│   │   └──────────────────────────────────────────────────────────┘  │   │
│   └─────────────────────────────────────────────────────────────────┘   │
│      │                                                                  │
│      ▼                                                                  │
│   ErrorResolution (errorCode, httpStatus, strategy)                     │
│                                                                         │
└─────────────────────────────────────────────────────────────────────────┘
```

## 包结构

```
com.patra.starter.core.error
├── config/                    # 配置与自动装配
│   ├── ErrorProperties        # 配置属性（patra.error.*）
│   ├── CoreErrorAutoConfiguration      # 核心自动配置
│   └── CircuitBreakerErrorAutoConfiguration  # 熔断器自动配置
├── engine/                    # 错误解析引擎
│   ├── ErrorResolutionEngine  # 引擎接口
│   └── DefaultErrorResolutionEngine  # 默认实现（多策略+缓存）
├── model/                     # 数据模型
│   ├── ErrorResolution        # 解析结果（record）
│   ├── ResolutionStrategy     # 解析策略枚举
│   └── SimpleErrorCode        # 简单错误码实现
├── pipeline/                  # 解析管道
│   ├── ErrorResolutionPipeline    # 管道（责任链）
│   ├── ResolutionInterceptor      # 拦截器接口
│   ├── ResolutionInvocation       # 调用链
│   └── interceptor/
│       └── CircuitBreakerInterceptor  # 熔断拦截器
├── spi/                       # SPI 扩展点
│   ├── ErrorMappingContributor    # 错误码映射贡献者
│   ├── TraceProvider              # 追踪 ID 提取
│   └── ProblemFieldContributor    # ProblemDetail 扩展字段
└── trace/
    └── HeaderBasedTraceProvider   # 基于 HTTP Header 的追踪提取
```

## 核心组件

### ErrorResolutionEngine

错误解析引擎接口，将任意异常转换为统一的错误表示。

```java
public interface ErrorResolutionEngine {
  /// 将异常解析为标准化错误。
  ErrorResolution resolve(Throwable exception);
}
```

### DefaultErrorResolutionEngine

默认实现，提供多策略解析和性能优化：

| 策略 | 描述 | 缓存 | 优先级 |
|------|------|------|--------|
| `APPLICATION_EXCEPTION` | `ApplicationException` 自带 `ErrorCodeLike` | ✅ | 1 |
| `CONTRIBUTOR` | `ErrorMappingContributor` SPI 扩展 | ❌ | 2 |
| `TRAIT` | `HasErrorTraits` 语义特征映射 | ✅ | 3 |
| `NAMING` | 类名后缀启发式（如 `NotFoundException` → 404） | ✅ | 4 |
| `CAUSE` | 原因链递归解析 | ❌ | 5 |
| `FALLBACK` | 回退（客户端错误 422 / 服务器错误 500） | ✅ | 6 |

**命名启发式映射表：**

| 后缀 | HTTP 状态码 |
|------|-------------|
| `NotFound` | 404 |
| `Conflict`, `AlreadyExists` | 409 |
| `Invalid`, `Validation` | 422 |
| `QuotaExceeded` | 429 |
| `Unauthorized` | 401 |
| `Forbidden` | 403 |
| `Timeout` | 504 |

### ErrorResolutionPipeline

错误解析管道，通过拦截器链处理异常解析流程：

```
异常 → [Interceptor₁] → [Interceptor₂] → ... → [Engine] → ErrorResolution
```

拦截器按 `@Order` 注解排序，数值越小优先级越高。

### ErrorResolution

解析结果，包含：

- `errorCode`: 业务错误码（`ErrorCodeLike`）
- `httpStatus`: HTTP 状态码（100-599）
- `strategy`: 使用的解析策略（用于可观测性）

## SPI 扩展点

### ErrorMappingContributor

自定义异常到错误码的映射：

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)  // 高优先级
public class DataLayerErrorMappingContributor implements ErrorMappingContributor {

  @Override
  public Optional<ErrorCodeLike> mapException(Throwable exception) {
    if (exception instanceof SQLException) {
      return Optional.of(HttpStdErrors.of("INGEST").INTERNAL_ERROR());
    }
    return Optional.empty();  // 传递给下一个 Contributor
  }
}
```

**优先级指南：**

| 范围 | 适用场景 |
|------|----------|
| `HIGHEST_PRECEDENCE` | 数据层异常（SQL、连接池） |
| `-100 ~ 0` | 基础设施异常（外部 API、消息队列） |
| `0 ~ 100` | 业务异常 |
| `100+` | 兜底逻辑 |

### TraceProvider

从执行上下文提取追踪 ID：

```java
@Component
public class OtelTraceProvider implements TraceProvider {

  @Override
  public Optional<String> getCurrentTraceId() {
    // OpenTelemetry Agent 自动将 traceId 注入 MDC
    return Optional.ofNullable(MDC.get("traceId"));
  }
}
```

### ProblemFieldContributor

丰富 `ProblemDetail` 响应字段：

```java
@Component
public class ServiceInfoContributor implements ProblemFieldContributor {

  @Override
  public void contribute(Map<String, Object> fields, Throwable exception) {
    fields.put("service", "patra-ingest");
    fields.put("timestamp", Instant.now().toString());
  }
}
```

### ResolutionInterceptor

自定义解析管道拦截器：

```java
@Component
@Order(100)
public class MetricsInterceptor implements ResolutionInterceptor {

  @Override
  public ErrorResolution intercept(Throwable exception, ResolutionInvocation invocation) {
    long start = System.nanoTime();
    try {
      return invocation.proceed(exception);
    } finally {
      metrics.recordResolutionTime(System.nanoTime() - start);
    }
  }
}
```

## 配置

### 配置属性（patra.error.*）

```yaml
patra:
  error:
    # 是否启用错误解析框架（默认: true）
    enabled: true

    # [必需] 错误码上下文前缀，如 INGEST、REG、CATALOG
    context-prefix: INGEST

    # 解析引擎配置
    engine:
      # 原因链最大递归深度（默认: 10）
      max-cause-depth: 10
      # 是否启用语义特征映射（默认: true）
      enable-trait-mapping: true
      # 是否启用类名启发式（默认: true）
      enable-naming-heuristic: true

    # 观测配置
    observation:
      # 是否启用观测（默认: true）
      enabled: true
      # 慢解析阈值，毫秒（默认: 200）
      slow-threshold-ms: 200
      # 是否记录慢解析日志（默认: true）
      log-slow-resolution: true

    # 熔断器配置（需要 Resilience4j）
    circuit-breaker:
      # 是否启用熔断保护（默认: true）
      enabled: true
      # 失败率阈值，%（默认: 50）
      failure-rate-threshold: 50
      # 触发熔断的最小调用数（默认: 20）
      minimum-number-of-calls: 20
      # 滑动窗口大小（默认: 50）
      sliding-window-size: 50
      # 半开状态允许的调用数（默认: 5）
      permitted-calls-in-half-open-state: 5
      # 熔断器打开后等待时间（默认: 30s）
      wait-duration-in-open-state: 30s
```

## 与异常体系的集成

本框架与 `patra-common-core` 中的异常体系紧密集成：

```
                           ┌────────────────────┐
                           │    ErrorCodeLike   │  ← 错误码契约
                           └────────────────────┘
                                    ▲
                    ┌───────────────┼───────────────┐
                    │               │               │
          ┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
          │  SimpleErrorCode │ │  HttpStdErrors  │ │ 业务 ErrorCode  │
          └─────────────────┘ └─────────────────┘ └─────────────────┘

          ┌────────────────────────────────────────────────────────┐
          │                   异常继承体系                          │
          │                                                        │
          │   RuntimeException                                     │
          │         ▲                                              │
          │         │                                              │
          │   ┌─────┴──────┐                                       │
          │   │            │                                       │
          │   ▼            ▼                                       │
          │ DomainException  ApplicationException                  │
          │ (领域层)         (应用层)                               │
          │ implements       has ErrorCodeLike                     │
          │ HasErrorTraits                                         │
          │         │                                              │
          │         ▼                                              │
          │   业务领域异常                                          │
          │   (UserNotFoundException, PlanValidationException...)  │
          └────────────────────────────────────────────────────────┘
```

**最佳实践：**

1. **领域层**：继承 `DomainException`，携带 `StandardErrorTrait`
2. **应用层**：使用 `ApplicationException` 包装领域异常，携带明确的 `ErrorCodeLike`
3. **基础设施层**：通过 `ErrorMappingContributor` 映射第三方异常

## 性能优化

### 异常类型缓存

- **策略缓存**：为每个异常类型缓存解析策略函数，而非解析结果
- **缓存命中**：缓存命中时性能提升 95%+
- **选择性缓存**：`Contributor` 和 `Cause` 策略不缓存（可能有状态或依赖上下文）

### 命名启发式优化

- **最长后缀匹配**：避免歧义（如 `UserNotFoundInvalidException` 优先匹配 `Invalid`）
- **单次遍历**：仅遍历映射表一次

### 原因链智能终止

- **跳过包装异常**：自动跳过 `RuntimeException`、`Exception`、`UndeclaredThrowableException` 等无业务语义的包装
- **业务异常优先**：外层有业务语义时，不继续递归到基础设施异常
- **深度限制**：可配置的最大递归深度（默认 10）

### 慢解析检测

- **阈值监控**：超过配置阈值（默认 200ms）记录 WARN 日志
- **限流日志**：每分钟最多记录一次，避免日志轰炸
- **缓存统计**：提供命中率、命中数、未命中数等统计

## 使用示例

### 业务代码（推荐方式）

**业务代码通常不需要直接使用 `ErrorResolutionPipeline`**。只需抛出适当的异常，框架会自动处理：

```java
@Service
public class UserService {

  public User findById(String userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));  // 直接抛出异常
  }
}
```

框架会自动完成以下流程：

```
业务代码抛异常 → GlobalExceptionHandler 捕获 → ProblemDetailAdapter.adapt()
                                                        ↓
                                          ErrorResolutionPipeline.resolve()
                                                        ↓
                                          返回 RFC 7807 ProblemDetail 响应
```

### 直接使用 Pipeline（特殊场景）

以下场景需要直接使用 `ErrorResolutionPipeline`：

- **自定义异常处理器**：需要编写自己的异常适配逻辑
- **非 Web 上下文**：消息消费者、定时任务中需要将异常转换为特定格式
- **测试/调试**：验证异常解析结果是否符合预期

```java
@Component
public class MessageErrorHandler {

  private final ErrorResolutionPipeline pipeline;

  public void handleConsumerError(Throwable ex, Message message) {
    ErrorResolution resolution = pipeline.resolve(ex);
    // 根据解析结果决定重试策略或死信处理
    if (resolution.httpStatus() >= 500) {
      scheduleRetry(message);
    } else {
      sendToDeadLetter(message, resolution.errorCode().code());
    }
  }
}
```

## 错误码格式

错误码格式：`{SERVICE}-{0xxx}`

| 示例 | 含义 |
|------|------|
| `INGEST-0404` | 采集服务，资源未找到 |
| `REG-0409` | 注册服务，资源冲突 |
| `CATALOG-0500` | 目录服务，内部错误 |
| `UNKNOWN-0503` | 未配置前缀，服务不可用 |

**0xxx 段映射到 HTTP 语义：**

| 错误码后缀 | HTTP 状态 | 语义 |
|-----------|----------|------|
| 0400 | 400 | Bad Request |
| 0401 | 401 | Unauthorized |
| 0403 | 403 | Forbidden |
| 0404 | 404 | Not Found |
| 0409 | 409 | Conflict |
| 0422 | 422 | Unprocessable Entity |
| 0429 | 429 | Too Many Requests |
| 0500 | 500 | Internal Server Error |
| 0503 | 503 | Service Unavailable |
| 0504 | 504 | Gateway Timeout |

## 相关模块

- `patra-common-core` - 异常基类和错误码契约
- `patra-spring-boot-starter-web` - Web 层异常处理（`GlobalRestExceptionHandler`）
- `patra-spring-boot-starter-observability` - 可观测性拦截器（追踪、指标）
