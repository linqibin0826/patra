# patra-spring-cloud-starter-feign

> 增强的 Feign 客户端配置,支持基于约定的扫描、错误处理、追踪和重试策略。

## 📌 目的

使用 Papertrace 约定扩展 Spring Cloud OpenFeign:

- **基于约定的 Feign 客户端扫描**(`com.patra.*.api.rpc.client`)
- 自定义错误解码器(映射错误到领域异常)
- 请求拦截器(追踪 ID 传播、调用方服务 ID)
- 重试策略(指数退避)
- 超时配置
- 熔断器集成

## 🏗️ 基于约定的扫描

### 自动 Feign 客户端发现

本 Starter **自动扫描并注册** `com.patra` 包下所有使用 `@FeignClient` 注解的接口。

**约定**: 将 RPC 客户端放在 `{module}-api/src/main/java/com/patra/{module}/api/rpc/client/` 包中。

**自动发现的客户端示例**:

- `com.patra.registry.api.rpc.client.ProvenanceClient`
- `com.patra.registry.api.rpc.client.ExprClient`
- `com.patra.ingest.api.rpc.client.TaskClient`(未来)
- `com.patra.data.api.rpc.client.LiteratureClient`(未来)

### 无需手动配置

```java
// ❌ 旧方式: 在每个服务中手动配置
@EnableFeignClients(basePackages = {"com.patra.registry.api.rpc.client"})
@SpringBootApplication
public class MyApplication {
}

// ✅ 新方式: 基于约定的自动发现
@SpringBootApplication
public class MyApplication {
    // Feign 客户端通过 Starter 自动发现
}
```

### 优势

- ✅ **约定优于配置**: 遵循命名模式 → 自动注册
- ✅ **DRY 原则**: 无需在各服务中分散 `@EnableFeignClients`
- ✅ **一致性**: 所有服务遵循相同的发现模式
- ✅ **可维护性**: 添加新的 Feign 客户端无需更新配置

### 发现所有 Feign 客户端

本 Starter 发现 `com.patra` 下的**所有** `@FeignClient` 接口,包括放置在
`com.patra.{module}.api.rpc.client.*` 下的业务 RPC 客户端(遵循约定)。

**各个 Starter 无需声明 `@EnableFeignClients`** - 本 Starter 集中处理。

## 🔧 自动配置

### 错误解码器

- 映射 HTTP 404 → `NotFoundException`
- 映射 HTTP 409 → `ConflictException`
- 映射 HTTP 5xx → `RemoteServiceException`
- 从响应中提取 Problem Detail

### 请求拦截器

- 注入 `X-Trace-ID` 头
- 注入 `X-Span-ID` 头
- 传播关联 ID

### 重试配置

- 最大尝试次数: 3
- 退避: 100ms, 300ms, 900ms(指数)
- 可重试: 502, 503, 504 状态码

## 🔗 依赖

```xml

<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-spring-cloud-starter-feign</artifactId>
</dependency>
```

包含: Spring Cloud OpenFeign、Resilience4j Retry

## 🚀 用法

### 定义 Feign 客户端(基于约定)

**步骤 1**: 在 `{module}-api/src/main/java/com/patra/{module}/api/rpc/client/` 创建客户端接口

```java
package com.patra.registry.api.rpc.client;

import org.springframework.cloud.openfeign.FeignClient;

@FeignClient(name = "patra-registry", contextId = "provenanceClient")
public interface ProvenanceClient extends ProvenanceEndpoint {
    // 方法从端点接口继承
}
```

**步骤 2**: 在服务中使用客户端(自动发现并注入)

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

**就这样!** 应用类中无需 `@EnableFeignClients` 注解。

### 配置

```yaml
feign:
  client:
    config:
      default:
        connect-timeout: 5000
        read-timeout: 10000
        retry-max-attempts: 3
```

---

## 🔗 相关文档

- [主 README](../README.md)
- [patra-spring-boot-starter-provenance](../patra-spring-boot-starter-provenance/README.md) - Provenance 客户端 Starter
- [架构指南](../docs/ARCHITECTURE.md) - 系统设计模式

---

**最后更新**: 2025-10-14
