# Patra Starters 快速参考

## 按模块层分类

| 模块层 | Starters |
|-------|----------|
| **adapter** | `patra-spring-boot-starter-web` |
| **infra（数据库）** | `patra-spring-boot-starter-jpa` |
| **infra（内部服务调用）** | `patra-spring-boot-starter-http-interface` |
| **infra（外部 REST 调用）** | `patra-spring-boot-starter-rest-client` |
| **infra（对象存储）** | `patra-spring-boot-starter-object-storage` |
| **infra（分布式锁）** | `patra-spring-boot-starter-redisson` |
| **所有层（除 domain）** | `patra-spring-boot-starter-core` |
| **可选（API 文档）** | `patra-spring-boot-starter-openapi`（boot 模块） |
| **可选（可观测性）** | `patra-spring-boot-starter-observability` |

## Starter 详情

### starter-web

**适用**：`patra-{service}-adapter`
**功能**：REST Controller、全局异常处理、参数校验、统一响应模型

### starter-jpa

**适用**：`patra-{service}-infra`（涉及数据库）
**功能**：Spring Data JPA、`BaseJpaEntity` 基类（雪花 ID + 审计 + 乐观锁）、`SoftDeletableJpaEntity`、Flyway 迁移

### starter-http-interface

**适用**：`patra-{service}-infra`（调用其他 Patra 微服务）
**功能**：Spring 7 HTTP Interface + RestClient + Consul 服务发现

**三步使用**：
1. 在 `-api` 模块定义 `@HttpExchange` 接口
2. 在 `-boot` 模块的 `HttpClientConfiguration` 注册代理
3. 在 `-infra` 模块注入 Endpoint 使用

```java
// -api 模块
@HttpExchange(url = "/_internal/provenances", accept = "application/json")
public interface ProvenanceEndpoint {
    @GetExchange("/{code}/config")
    ProvenanceConfigResp getConfiguration(@PathVariable("code") ProvenanceCode code);
}

// -boot 模块
@Configuration
public class HttpClientConfiguration {
    @Bean
    public RestClient registryRestClient(
            @Qualifier("httpInterfaceLoadBalancedRestClientBuilder") RestClient.Builder builder,
            RestClientFactory factory) {
        return factory.createRestClient(builder, "registry", "lb://patra-registry");
    }

    @Bean
    public ProvenanceEndpoint provenanceEndpoint(
            RestClient registryRestClient, RestClientFactory factory) {
        return factory.createProxy(registryRestClient, ProvenanceEndpoint.class);
    }
}
```

### starter-rest-client

**适用**：`patra-{service}-infra`（调用外部 REST API、下载文件）
**功能**：Spring RestClient（JDK 21 HttpClient）、统一超时、日志拦截

### starter-object-storage

**适用**：`patra-{service}-infra`（需要对象存储）
**功能**：MinIO/S3 抽象、`ObjectStorageTemplate`、`StorageLocationResolver`

### starter-redisson

**适用**：`patra-{service}-infra`（需要分布式锁）
**功能**：`@DistributedLock` 声明式注解、SpEL 动态锁键、多种锁类型

### starter-openapi

**适用**：`patra-{service}-boot`
**功能**：SpringDoc OpenAPI 3.0.1 + Scalar UI、Javadoc 自动映射为 API 文档

**访问**：`http://localhost:8080/scalar/index.html`

## 依赖检查清单

新建功能时，按以下顺序检查：

1. **Adapter 层** → `starter-web`
2. **Infra 层（数据库）** → `starter-jpa`
3. **Infra 层（服务调用）** → `starter-http-interface`（可选）
4. **Infra 层（对象存储）** → `starter-object-storage`（可选）
5. **Infra 层（REST 调用）** → `starter-rest-client`（可选）
6. **Infra 层（分布式锁）** → `starter-redisson`（可选）
7. **Domain 层** → 不能添加任何 Starter
8. **Boot 层（API 文档）** → `starter-openapi`（可选）
