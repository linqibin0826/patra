# Patra Spring Boot Starter - HTTP Interface

为 Spring HTTP Interface 提供统一的错误处理和自动配置支持。

## 功能概述

- **ProblemDetail 错误处理**：自动解析 RFC 7807 格式的错误响应，转换为 `RemoteCallException`
- **ErrorTrait 语义传播**：支持服务间错误语义自动传播（NOT_FOUND、TIMEOUT、CONFLICT 等）
- **RestClient 自动配置**：预配置超时和错误处理器

> **注意**：TraceId 传播由 OpenTelemetry Java Agent 自动处理，无需手动配置。

## 包结构

```
dev.linqibin.starter.httpinterface
├── config/
│   ├── HttpInterfaceAutoConfiguration  # 自动配置类
│   └── HttpInterfaceProperties         # 配置属性
├── error/
│   └── ProblemDetailErrorHandler       # RFC 7807 错误处理器
└── factory/
    └── RestClientFactory               # RestClient 工厂
```

## 自动配置的 Bean

| Bean 名称 | 类型 | 说明 |
|-----------|------|------|
| `problemDetailErrorHandler` | `ProblemDetailErrorHandler` | RFC 7807 错误响应处理器 |
| `httpInterfaceRestClientCustomizer` | `RestClientCustomizer` | RestClient.Builder 自定义器 |
| `httpInterfaceRestClientBuilder` | `RestClient.Builder` | 预配置的 RestClient.Builder（默认主 Bean，非负载均衡） |
| `httpInterfaceLoadBalancedRestClientBuilder` | `RestClient.Builder` | 预配置的 RestClient.Builder（负载均衡，专用于 `lb://`） |

## 配置属性

```yaml
patra:
  http:
    interface:
      # 是否启用 HTTP Interface 自动配置（默认 true）
      enabled: true
      # 全局连接超时（默认 5s）
      connect-timeout: 5s
      # 全局读取超时（默认 30s）
      read-timeout: 30s
      # 错误处理配置
      error-handling:
        # 是否启用 ProblemDetail 解析（默认 true）
        problem-detail-enabled: true
        # 容错模式：非 ProblemDetail 响应也包装为 RemoteCallException（默认 true）
        tolerant: true
        # 最大错误响应体大小（默认 64KB）
        max-error-body-size: 65536
      # 服务分组配置（可选）
      groups:
        registry:
          base-url: lb://patra-registry
          read-timeout: 10s
        storage:
          base-url: lb://patra-object-storage
          read-timeout: 60s
```

## 使用示例

### 1. 添加依赖

```xml
<dependency>
    <groupId>dev.linqibin.patra</groupId>
    <artifactId>patra-spring-boot-starter-http-interface</artifactId>
</dependency>
```

### 2. 定义 Endpoint 接口（-api 模块）

```java
@HttpExchange("/api/dictionaries")
public interface DictionaryEndpoint {

    @GetExchange("/{code}")
    DictionaryResp getByCode(@PathVariable String code);

    @GetExchange
    List<DictionaryResp> list(@RequestParam String type);
}
```

### 3. 注册 HTTP Interface 代理（-boot 模块）

```java
@Configuration
public class HttpClientConfiguration {

    @Bean
    public DictionaryEndpoint dictionaryEndpoint(
            @Qualifier("httpInterfaceLoadBalancedRestClientBuilder") RestClient.Builder builder,
            RestClientFactory factory) {
        RestClient client = factory.createRestClient(builder, "registry", "lb://patra-registry");
        return factory.createProxy(client, DictionaryEndpoint.class);
    }
}
```

### 4. 使用 Endpoint（-infra 模块）

```java
@Component
@RequiredArgsConstructor
public class DictionaryResolverAdapter implements DictionaryResolver {

    private final DictionaryEndpoint dictionaryEndpoint;

    @Override
    public DictionaryEntry resolve(String code) {
        try {
            return dictionaryEndpoint.getByCode(code);
        } catch (RemoteCallException ex) {
            // 基于 ErrorTrait 语义判断
            if (ex.getErrorTraits().contains(StandardErrorTrait.NOT_FOUND)) {
                throw new DictionaryNotFoundException(code);
            }
            // 或使用工具类基于 HTTP 状态码判断
            if (RemoteErrorHelper.isNotFound(ex)) {
                throw new DictionaryNotFoundException(code);
            }
            throw ex;
        }
    }
}
```

## 错误处理流程

```
下游服务响应
    ↓
ProblemDetailErrorHandler.handle()
    ↓
┌─────────────────────────────────┐
│ 是否为 application/problem+json? │
└─────────────────────────────────┘
    │是                    │否
    ↓                     ↓
解析 ProblemDetail     容错模式？
    ↓                     │是         │否
提取字段:                  ↓           ↓
- status              包装为        抛出通用
- detail              RemoteCall   RemoteCall
- errorCode           Exception    Exception
- traceId
- traits (ErrorTrait)
    ↓
构造 RemoteCallException
    ↓
抛出异常
```

## ErrorTrait 语义传播

当下游服务在 ProblemDetail 响应中包含 `traits` 字段时，这些语义特征会被自动解析并传播：

```json
{
  "type": "about:blank",
  "title": "Not Found",
  "status": 404,
  "detail": "资源未找到: id=123",
  "traits": ["NOT_FOUND", "BUSINESS_ERROR"]
}
```

上游服务可以基于 ErrorTrait 进行语义判断：

```java
try {
    return endpoint.getResource(id);
} catch (RemoteCallException ex) {
    // 推荐方式：基于语义特征判断
    if (ex.getErrorTraits().contains(StandardErrorTrait.NOT_FOUND)) {
        throw new ResourceNotFoundException(id);
    }
    if (ex.getErrorTraits().contains(StandardErrorTrait.RETRYABLE)) {
        // 执行重试逻辑
    }
    throw ex;
}
```

## 与 Feign 的对比

| 特性 | OpenFeign | HTTP Interface |
|------|-----------|----------------|
| 依赖 | Spring Cloud | Spring Framework 核心 |
| 注解 | `@FeignClient` | `@HttpExchange` |
| 底层实现 | 可插拔（默认 HTTP Client） | RestClient |
| 错误处理 | `ErrorDecoder` | `ResponseSpec.ErrorHandler` |
| 拦截器 | `RequestInterceptor` | `ClientHttpRequestInterceptor` |
| 配置复杂度 | 高 | 低 |

## 参考文档

- [Spring HTTP Interface](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-http-interface)
- [RestClient](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html#rest-restclient)
- [RFC 7807 Problem Details](https://www.rfc-editor.org/rfc/rfc7807)
