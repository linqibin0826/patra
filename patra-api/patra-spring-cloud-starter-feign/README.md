# patra-spring-cloud-starter-feign

## 概述

增强的 Feign 客户端配置 Starter，提供基于约定的客户端扫描、RFC 7807 错误处理、ErrorTrait 语义传播、追踪传播和可观测性能力。

## 核心功能

| 功能 | 描述 |
|------|------|
| **基于约定的 Feign 客户端扫描** | 自动发现 `com.patra` 包下的所有 `@FeignClient` 接口 |
| **ProblemDetail 错误解码** | 解析 RFC 7807 格式错误响应，提取业务错误码和扩展字段 |
| **ErrorTrait 语义传播** | 自动解析下游服务的错误特征，支持跨服务语义判断 |
| **追踪 ID 传播** | 自动传播 Trace ID 和 Span ID |
| **服务标识传播** | 注入调用方服务名称 |
| **错误观测** | Micrometer 指标采集 |

## 包结构

```
com.patra.starter.feign
├── error/                          # 错误处理（核心）
│   ├── config/                     # 自动配置和属性
│   ├── decoder/                    # ProblemDetailErrorDecoder
│   ├── exception/                  # RemoteCallException
│   ├── interceptor/                # TraceId 传播拦截器
│   ├── observation/                # Micrometer 指标记录
│   └── util/                       # RemoteErrorHelper 辅助工具
└── runtime/                        # 运行时配置
    ├── PatraFeignAutoConfiguration # Feign 客户端扫描
    └── PatraFeignRequestInterceptor # 服务标识传播
```

## 核心组件

### RemoteCallException

Feign 调用下游服务失败时抛出的统一异常，实现 `HasErrorTraits` 接口支持错误语义传播。

```java
public class RemoteCallException extends RuntimeException implements HasErrorTraits {

    // 下游服务返回的业务错误码
    String getErrorCode();

    // HTTP 状态码
    int getHttpStatus();

    // 分布式跟踪 ID
    String getTraceId();

    // ProblemDetail 扩展字段
    Map<String, Object> getAllExtensions();
    <T> T getExtension(String key, Class<T> type);

    // 错误语义特征（用于跨服务判断）
    Set<ErrorTrait> getErrorTraits();
    boolean hasErrorTraits();
}
```

**ErrorTrait 语义传播流程**：

```
上游服务抛出异常（实现 HasErrorTraits）
        ↓
ProblemDetailBuilder 输出 traits 字段
        ↓
HTTP 响应: {"traits": ["NOT_FOUND", "TIMEOUT"]}
        ↓
下游 Feign 客户端接收响应
        ↓
ProblemDetailErrorDecoder 解析 → RemoteCallException
        ↓
RemoteCallException.getErrorTraits() 返回解析后的特征
        ↓
业务代码基于语义特征判断错误类型
```

### ProblemDetailErrorDecoder

自定义 Feign ErrorDecoder，解析 RFC 7807 ProblemDetail 响应：

- 解析 `application/problem+json` 格式响应
- 提取 `code`、`traceId`、`traits` 等扩展字段
- 支持宽容模式：非 ProblemDetail 响应时优雅降级
- 集成 Micrometer 记录解码性能指标

### RemoteErrorHelper

静态工具类，提供基于 HTTP 状态码的错误分类方法：

```java
// HTTP 状态码检查
RemoteErrorHelper.isNotFound(exception)        // 404
RemoteErrorHelper.isConflict(exception)        // 409
RemoteErrorHelper.isUnauthorized(exception)    // 401
RemoteErrorHelper.isForbidden(exception)       // 403
RemoteErrorHelper.isClientError(exception)     // 4xx
RemoteErrorHelper.isServerError(exception)     // 5xx
RemoteErrorHelper.isRetryable(exception)       // 可重试错误

// 精确状态码匹配
RemoteErrorHelper.is(exception, 429)           // Too Many Requests
RemoteErrorHelper.isAnyOf(exception, 502, 503, 504)
```

## 自动配置

### PatraFeignAutoConfiguration

| Bean | 描述 |
|------|------|
| `patraFeignRequestInterceptor` | 服务标识传播拦截器 |
| `@EnableFeignClients` | 启用 `com.patra` 包下的 Feign 客户端扫描 |

### FeignErrorAutoConfiguration

| Bean | 描述 |
|------|------|
| `problemDetailErrorDecoder` | ProblemDetail 错误解码器 |
| `traceIdRequestInterceptor` | TraceId 传播拦截器 |
| `feignErrorObservationRecorder` | 错误观测记录器 |

## 配置属性

```yaml
patra:
  feign:
    enabled: true                    # 启用 Starter（默认 true）
    service-header: "X-Service-Name" # 服务标识头名称
    max-error-body-size: 65536       # 错误响应体最大读取字节数
    redact-keys:                     # 需要脱敏的请求头键
      - "token"
      - "password"
    problem:
      enabled: true                  # 启用 ProblemDetail 错误解码
      tolerant: false                # 宽容模式：解析失败时降级为通用异常
      observation:
        enabled: true                # 启用错误观测
```

## 使用方式

### Maven 依赖

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-cloud-starter-feign</artifactId>
</dependency>
```

### 定义 Feign 客户端

在 `{module}-api` 模块中创建客户端接口：

```java
package com.patra.registry.api.rpc.client;

@FeignClient(name = "patra-registry", contextId = "provenanceClient")
public interface ProvenanceClient extends ProvenanceEndpoint {
    // 方法从端点接口继承
}
```

### 错误处理（推荐：基于 ErrorTrait）

```java
@Component
@RequiredArgsConstructor
public class RegistryAdapter implements RegistryPort {

    private final ProvenanceClient client;

    @Override
    public ProvenanceConfig fetchConfig(ProvenanceCode code) {
        try {
            return client.getConfiguration(code);
        } catch (RemoteCallException ex) {
            // 基于语义特征判断（推荐）
            Set<ErrorTrait> traits = ex.getErrorTraits();

            if (traits.contains(StandardErrorTrait.NOT_FOUND)) {
                throw new ConfigNotFoundException("配置不存在: " + code, ex);
            }
            if (traits.contains(StandardErrorTrait.TIMEOUT)
                || traits.contains(StandardErrorTrait.DEP_UNAVAILABLE)) {
                throw new ServiceUnavailableException("Registry 服务不可用", ex);
            }

            // 其他错误
            throw new ConfigFetchException("获取配置失败", ex);
        }
    }
}
```

### 错误处理（备选：基于 HTTP 状态码）

```java
@Component
public class LegacyErrorHandler {

    public void handleError(RemoteCallException ex) {
        // 基于 HTTP 状态码判断
        if (RemoteErrorHelper.isNotFound(ex)) {
            // 404 处理
        } else if (RemoteErrorHelper.isServerError(ex)) {
            // 5xx 处理
        } else if (RemoteErrorHelper.isRetryable(ex)) {
            // 可重试错误处理
        }
    }
}
```

### 访问 ProblemDetail 扩展字段

```java
try {
    client.call();
} catch (RemoteCallException ex) {
    // 业务错误码
    if (ex.hasErrorCode()) {
        log.error("业务错误码: {}", ex.getErrorCode());
    }

    // 分布式跟踪
    if (ex.hasTraceId()) {
        log.error("TraceId: {}", ex.getTraceId());
    }

    // 自定义扩展字段
    String retryAfter = ex.getExtension("retryAfter", String.class);
    Integer errorCount = ex.getExtension("errorCount", Integer.class);
}
```

## 约定

### Feign 客户端位置

```
{module}-api/src/main/java/com/patra/{module}/api/rpc/client/
```

自动发现示例：
- `com.patra.registry.api.rpc.client.ProvenanceClient`
- `com.patra.registry.api.rpc.client.ExprClient`
- `com.patra.ingest.api.rpc.client.TaskClient`

### 错误处理层次

```
适配器层（Feign Client 调用）
    ↓ catch RemoteCallException
基础设施层（Port 实现）
    ↓ 转换为领域异常
领域层（纯业务逻辑）
```

## 技术栈

- Spring Cloud OpenFeign
- **Spring Cloud LoadBalancer**（已包含，无需单独引入）
- Spring Boot 3.5.7
- Micrometer
- patra-spring-boot-starter-core
- patra-common-core（ErrorTrait、HasErrorTraits）

> **注意**: 本 Starter 已包含 `spring-cloud-starter-loadbalancer` 依赖，使用 Feign 客户端时无需在
> 服务模块中额外引入。LoadBalancer 依赖集中管理可避免与 `patra-spring-boot-starter-rest-client`
> 的 `defaultRestClient` 产生冲突。
