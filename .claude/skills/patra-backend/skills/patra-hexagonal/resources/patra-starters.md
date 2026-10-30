# patra-tool 工具模块快速参考

工具模块在仓库根 `patra-tool/`，一个能力一个模块，包 `dev.patra.tool.<能力>`，配置前缀 `patra.<能力>`，
总开关 `patra.<能力>.enabled`；bean 全部由 `autoconfigure` 包里的自动配置注册，服务启动类只扫自己的包。
每个模块的 README 列了配置键、默认值与覆盖方式。

## 按模块层分类

| 模块层 | 工具模块 |
|-------|----------|
| **adapter** | `patra-tool-web` |
| **app（命令总线）** | `patra-tool-cqrs` |
| **infra（数据库）** | `patra-tool-jpa` |
| **infra（内部服务 HTTP Interface / 外部 REST 调用）** | `patra-tool-http-client` |
| **infra（对象存储）** | `patra-tool-object-storage` |
| **infra（批处理）** | `patra-tool-batch` |
| **domain / api（插件自动给，不手写）** | `patra-tool-core` |
| **boot** | `patra-tool-launch`（插件自动给）、`patra-tool-observability`、`patra-tool-openapi`、`patra-tool-security`（需要鉴权时） |
| **测试** | `patra-tool-test`（插件自动给到 testImplementation） |

## 模块详情

### patra-tool-web

**适用**：`patra-{service}-adapter`
**功能**：REST Controller、错误解析引擎与 ProblemDetail、`ErrorMappingContributor` SPI、MVC 响应 Long → String；
错误码前缀 `patra.web.error.context-prefix`

### patra-tool-cqrs

**适用**：`patra-{service}-app`
**功能**：`CommandBus` / `CommandHandler` / `SimpleCommandBus`、日志 / 追踪 / 指标拦截器；同一命令注册两个 Handler 启动即失败

### patra-tool-jpa

**适用**：`patra-{service}-infra`（涉及数据库）
**功能**：Spring Data JPA、`BaseJpaEntity` 基类（雪花 ID + 审计 + 乐观锁）、`SoftDeletableJpaEntity`、Flyway 迁移、`JpaErrorMappingContributor`

### patra-tool-http-client

**适用**：`patra-{service}-infra`（调用其他 Patra 微服务，或外部 REST API、下载文件）
**功能**：Spring 7 HTTP Interface + RestClient + Nacos 服务发现；外部调用的统一超时、代理、日志拦截

**三步使用（内部服务）**：
1. 在 `-api` 模块定义 `@HttpExchange` 接口
2. 在 `-boot` 模块的 `HttpClientConfiguration` 注册代理
3. 在 `-infra` 模块注入 Endpoint 使用

```java
// -api 模块
@HttpExchange(url = "/_internal/dictionaries", accept = "application/json")
public interface DictionaryEndpoint {
    @PostExchange("/resolve")
    DictionaryResolveResp resolve(@RequestBody DictionaryResolveReq request);
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
    public DictionaryEndpoint dictionaryEndpoint(
            RestClient registryRestClient, RestClientFactory factory) {
        return factory.createProxy(registryRestClient, DictionaryEndpoint.class);
    }
}
```

### patra-tool-object-storage

**适用**：`patra-{service}-infra`（需要对象存储）
**功能**：MinIO / S3 抽象、`ObjectStorageTemplate`（上传、下载、删除、按前缀删除）；对象键由各服务自己定

### patra-tool-batch

**适用**：`patra-{service}-app` / `-infra`（Spring Batch 作业）
**功能**：Batch 独立数据源、作业基座

### patra-tool-observability

**适用**：`patra-{service}-boot`
**功能**：logback（`logback-patra.xml`）、`logging.*` 与 `management.*` 默认值、指标 `application` 标签

### patra-tool-openapi

**适用**：`patra-{service}-boot`
**功能**：SpringDoc OpenAPI + Scalar UI、Javadoc 自动映射为 API 文档

**访问**：`http://localhost:<port>/scalar/index.html`

### patra-tool-security

**适用**：`patra-{service}-boot`（需要鉴权的服务与网关）
**功能**：身份断言签发 / 验签、安全异常透传

## 依赖检查清单

新建功能时，按以下顺序检查：

1. **Adapter 层** → `patra-tool-web`
2. **App 层（命令总线）** → `patra-tool-cqrs`
3. **Infra 层（数据库）** → `patra-tool-jpa`
4. **Infra 层（服务调用 / 外部 REST）** → `patra-tool-http-client`（可选）
5. **Infra 层（对象存储）** → `patra-tool-object-storage`（可选）
6. **Domain 层** → 不能手写任何工具模块依赖
7. **Boot 层** → `patra-tool-observability` / `patra-tool-openapi`（可选）/ `patra-tool-security`（可选）

声明了却没 import 的 project 依赖会被 R5（`checkDeclaredDependencies`）拦下。
