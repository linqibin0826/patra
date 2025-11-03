# patra-spring-cloud-starter-feign

## 概述

增强的 Feign 客户端配置 Starter,提供基于约定的客户端扫描、错误处理、追踪传播和观测能力。

本 Starter 使用 Papertrace 约定扩展 Spring Cloud OpenFeign,自动发现并注册 RPC 客户端,提供统一的错误解码、请求拦截和可观测性支持。

## 核心功能

- **基于约定的 Feign 客户端扫描**: 自动发现 `com.patra` 包下的所有 `@FeignClient` 接口
- **自定义错误解码器**: 映射 HTTP 错误到领域异常,支持 Problem Detail 规范
- **追踪 ID 传播**: 自动传播 Trace ID 和 Span ID
- **服务标识传播**: 注入调用方服务名称
- **错误观测**: 可选的 Micrometer 指标采集

## 自动配置内容

### 自动配置类

**PatraFeignAutoConfiguration** 自动配置:

| Bean 名称 | 类型 | 描述 |
|-----------|------|------|
| `patraFeignRequestInterceptor` | `PatraFeignRequestInterceptor` | 服务标识传播拦截器 |
| `@EnableFeignClients` | - | 启用 `com.patra` 包下的 Feign 客户端扫描 |

**FeignErrorAutoConfiguration** 自动配置:

| Bean 名称 | 类型 | 描述 |
|-----------|------|------|
| `problemDetailErrorDecoder` | `ErrorDecoder` | Problem Detail 错误解码器 |
| `traceIdRequestInterceptor` | `TraceIdRequestInterceptor` | 追踪 ID 传播拦截器 |
| `feignErrorObservationRecorder` | `FeignErrorObservationRecorder` | 错误观测记录器(可选) |

### 启用条件

- 配置属性 `patra.feign.enabled=true` (默认启用)
- 配置属性 `patra.feign.problem.enabled=true` (默认启用)
- 需要 Feign 客户端库存在

## 主要组件

### 基于约定的扫描

本 Starter 自动扫描并注册 `com.patra` 包下所有使用 `@FeignClient` 注解的接口。

**约定**: 将 RPC 客户端放在 `{module}-api/src/main/java/com/patra/{module}/api/rpc/client/` 包中。

**自动发现的客户端示例**:
- `com.patra.registry.api.rpc.client.ProvenanceClient`
- `com.patra.registry.api.rpc.client.ExprClient`
- `com.patra.ingest.api.rpc.client.TaskClient`

**优势**:
- 约定优于配置: 遵循命名模式 → 自动注册
- DRY 原则: 无需在各服务中分散 `@EnableFeignClients`
- 一致性: 所有服务遵循相同的发现模式

### ProblemDetailErrorDecoder

自定义错误解码器,将 HTTP 错误映射到领域异常:

- `404 Not Found` → `NotFoundException`
- `409 Conflict` → `ConflictException`
- `5xx Server Error` → `RemoteServiceException`
- 支持 RFC 7807 Problem Detail 格式解析

### TraceIdRequestInterceptor

追踪 ID 传播拦截器,自动注入:
- `X-Trace-ID`: 追踪 ID
- `X-Span-ID`: Span ID
- 支持从 MDC 或 TraceProvider 获取追踪信息

### PatraFeignRequestInterceptor

服务标识传播拦截器,自动注入:
- `X-Service-Name`: 调用方服务名称(从 `spring.application.name` 读取)

## 配置属性

配置前缀: `patra.feign`

### 基础配置

```yaml
patra:
  feign:
    enabled: true  # 启用 Starter(默认 true)
    service-header: "X-Service-Name"  # 服务标识头名称
    max-error-body-size: 65536  # 错误响应体最大读取字节数(默认 64KB)
    redact-keys:  # 需要脱敏的请求头键(不区分大小写)
      - "token"
      - "password"
      - "secret"
      - "apiKey"
```

### 错误处理配置

```yaml
patra:
  feign:
    problem:
      enabled: true  # 启用 Problem Detail 错误解码(默认 true)
      tolerant: false  # 容错模式: 解析失败时降级为通用异常
      observation:
        enabled: true  # 启用错误观测(默认 true)
```

### Spring Cloud OpenFeign 配置

```yaml
feign:
  client:
    config:
      default:
        connect-timeout: 5000  # 连接超时(毫秒)
        read-timeout: 10000    # 读取超时(毫秒)
```

## 使用方式

### Maven 依赖

```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-cloud-starter-feign</artifactId>
</dependency>
```

### 定义 Feign 客户端

**步骤 1**: 在 `{module}-api` 中创建客户端接口

```java
package com.patra.registry.api.rpc.client;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "patra-registry", contextId = "provenanceClient")
public interface ProvenanceClient extends ProvenanceEndpoint {
    // 方法从端点接口继承
}
```

**步骤 2**: 在服务中使用客户端

```java
@Component
@RequiredArgsConstructor
public class PatraRegistryPortImpl implements PatraRegistryPort {

    private final ProvenanceClient client;  // 自动注入(无需 @EnableFeignClients!)

    @Override
    public ProvenanceConfigSnapshot fetchConfig(ProvenanceCode code) {
        try {
            return client.getConfiguration(code, null, Instant.now());
        } catch (NotFoundException ex) {
            throw new ConfigNotFoundException("Config not found for: " + code, ex);
        }
    }
}
```

### 错误处理

```java
@Component
@RequiredArgsConstructor
public class RpcService {

    private final ProvenanceClient provenanceClient;

    public ProvenanceConfig getConfig(String code) {
        try {
            return provenanceClient.getConfiguration(code, null, Instant.now());
        } catch (NotFoundException ex) {
            // 404 错误,资源不存在
            log.warn("配置不存在: {}", code);
            return ProvenanceConfig.empty();
        } catch (RemoteServiceException ex) {
            // 5xx 错误,远程服务异常
            log.error("远程服务调用失败: {}", ex.getMessage(), ex);
            throw new ServiceUnavailableException("Registry 服务不可用", ex);
        }
    }
}
```

## 扩展点

### 自定义错误解码器

如需自定义错误解码逻辑,可提供自己的 `ErrorDecoder` Bean:

```java
@Configuration
public class CustomFeignConfig {

    @Bean
    public ErrorDecoder customErrorDecoder() {
        return new CustomErrorDecoder();
    }
}
```

### 自定义请求拦截器

如需添加自定义请求拦截器,直接注册为 Bean:

```java
@Component
public class CustomRequestInterceptor implements RequestInterceptor {

    @Override
    public void apply(RequestTemplate template) {
        // 自定义逻辑
        template.header("X-Custom-Header", "value");
    }
}
```

## 技术栈

- Spring Cloud OpenFeign
- Spring Boot 3.5.7
- Micrometer (可选)
- patra-spring-boot-starter-core
