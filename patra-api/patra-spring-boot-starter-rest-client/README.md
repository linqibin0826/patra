# Patra Spring Boot Starter - REST Client

基于 Spring 6 RestClient 的 HTTP 客户端自动配置，提供开箱即用的超时、日志、重试等功能。

## 特性

- **零配置启动**：引入依赖即可使用，默认配置满足大多数场景
- **JDK HttpClient**：基于 JDK 11+ 原生 HTTP 客户端，支持 HTTP/2
- **灵活的超时配置**：支持全局和客户端级别的超时设置
- **日志拦截器**：可选记录请求/响应的 Headers 和 Body
- **拦截器扩展**：支持注入自定义 `ClientHttpRequestInterceptor`
- **多客户端配置**：支持按用途定义多个客户端配置

## 快速开始

### 添加依赖

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-rest-client</artifactId>
</dependency>
```

### 基本使用

无需任何配置，直接注入 `RestClient` 使用：

```java
@Service
public class MyService {

    private final RestClient restClient;

    public MyService(RestClient restClient) {
        this.restClient = restClient;
    }

    public String fetchData() {
        return restClient.get()
            .uri("https://api.example.com/data")
            .retrieve()
            .body(String.class);
    }
}
```

### 长时间运行客户端

对于大文件下载、批量数据传输等需要较长超时的场景，使用 `longRunningRestClient`：

```java
@Service
public class FileDownloadService {

    private final RestClient restClient;

    /// 通过 @Qualifier 注入长时间运行客户端
    public FileDownloadService(
            @Qualifier("longRunningRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public void downloadLargeFile(URI url, Path targetPath) {
        restClient.get()
            .uri(url)
            .exchange((request, response) -> {
                try (var is = response.getBody();
                     var os = Files.newOutputStream(targetPath)) {
                    is.transferTo(os);
                }
                return null;
            });
    }
}
```

**默认超时配置**：
- 连接超时：30 秒
- 读取超时：600 秒（10 分钟）
- 写入超时：30 秒

**禁用方式**：

```yaml
patra:
  rest-client:
    clients:
      long-running:
        enabled: false
```

## 配置项

所有配置项均以 `patra.rest-client` 为前缀。

### 完整配置示例

```yaml
patra:
  rest-client:
    # 是否启用自动配置（默认 true）
    enabled: true

    # 全局超时配置
    timeout:
      connect: 10s      # 连接超时（默认 10s）
      read: 30s         # 读取超时（默认 30s）
      write: 30s        # 写入超时（默认 30s）

    # 重试配置
    retry:
      enabled: false           # 是否启用重试（默认 false）
      max-attempts: 3          # 最大重试次数
      wait-duration: 1000      # 初始等待时间（ms）
      backoff-multiplier: 2.0  # 退避倍数
      max-wait-duration: 30000 # 最大等待时间（ms）

    # 拦截器配置
    interceptors:
      logging:
        enabled: true           # 是否启用日志（默认 true）
        log-headers: false      # 是否记录 Headers（默认 false）
        log-body: false         # 是否记录 Body（默认 false）
        max-body-log-length: 1024  # Body 日志最大字节数

      tracing:
        enabled: true                           # 是否启用追踪（默认 true）
        header-names:                           # 追踪 ID 头名称
          - X-Trace-ID
          - X-B3-TraceId

      metrics:
        enabled: true           # 是否启用指标（默认 true）

    # 多客户端配置
    clients:
      # 长时间运行客户端（自动配置，可选覆盖）
      long-running:
        enabled: true         # 是否启用（默认 true）
        timeout:
          connect: 30s        # 连接超时（默认 30s）
          read: 600s          # 读取超时（默认 600s / 10分钟）
          write: 30s          # 写入超时（默认 30s）

      # 自定义客户端示例
      pubmed:
        base-url: "https://eutils.ncbi.nlm.nih.gov/entrez/eutils"
        default-headers:
          Accept: "application/json"
        timeout:
          connect: 5s
          read: 60s
```

### 配置项说明

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | `true` | 是否启用自动配置 |
| `timeout.connect` | Duration | `10s` | 连接超时时间 |
| `timeout.read` | Duration | `30s` | 读取超时时间 |
| `timeout.write` | Duration | `30s` | 写入超时时间 |
| `retry.enabled` | boolean | `false` | 是否启用重试 |
| `retry.max-attempts` | int | `3` | 最大重试次数 |
| `retry.wait-duration` | long | `1000` | 初始重试等待时间（ms） |
| `retry.backoff-multiplier` | double | `2.0` | 退避倍数 |
| `retry.max-wait-duration` | long | `30000` | 最大等待时间（ms） |
| `interceptors.logging.enabled` | boolean | `true` | 是否启用日志拦截器 |
| `interceptors.logging.log-headers` | boolean | `false` | 是否记录 HTTP 头 |
| `interceptors.logging.log-body` | boolean | `false` | 是否记录请求/响应体 |
| `interceptors.logging.max-body-log-length` | int | `1024` | Body 日志最大字节数 |
| `clients.*.enabled` | boolean | `true` | 是否启用指定客户端 |
| `clients.*.timeout.connect` | Duration | - | 客户端级连接超时（覆盖全局） |
| `clients.*.timeout.read` | Duration | - | 客户端级读取超时（覆盖全局） |
| `clients.long-running.timeout.read` | Duration | `600s` | 长时间运行客户端读取超时 |

## 日志输出

启用日志拦截器后，DEBUG 级别日志示例：

```
DEBUG c.p.s.r.i.LoggingInterceptor - HTTP GET https://api.example.com/data
DEBUG c.p.s.r.i.LoggingInterceptor - HTTP GET https://api.example.com/data -> 200 OK (156 ms)
```

启用 `log-headers` 和 `log-body` 后：

```
DEBUG c.p.s.r.i.LoggingInterceptor - HTTP POST https://api.example.com/data
DEBUG c.p.s.r.i.LoggingInterceptor - Headers: {Content-Type=[application/json]}
DEBUG c.p.s.r.i.LoggingInterceptor - Body: {"name":"test"}
DEBUG c.p.s.r.i.LoggingInterceptor - HTTP POST https://api.example.com/data -> 201 Created (234 ms)
```

## 自定义扩展

### 自定义拦截器

实现 `ClientHttpRequestInterceptor` 并注册为 Bean：

```java
@Component
@Order(50)  // 控制拦截器执行顺序
public class AuthInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {

        request.getHeaders().add("Authorization", "Bearer " + getToken());
        return execution.execute(request, body);
    }
}
```

### 自定义 RestClient

如需完全自定义，可替换默认 Bean：

```java
@Bean
public RestClient customRestClient(JdkClientHttpRequestFactory factory) {
    return RestClient.builder()
        .requestFactory(factory)
        .baseUrl("https://api.example.com")
        .defaultHeader("X-Custom-Header", "value")
        .build();
}
```

### 拦截器顺序

内置拦截器默认顺序：

| 拦截器 | Order | 说明 |
|--------|-------|------|
| LoggingInterceptor | 100 | 日志拦截器 |

建议自定义拦截器使用小于 100 的值，以确保在日志记录之前执行。

## 自动配置的 Bean

| Bean 名称 | 类型 | 条件 | 说明 |
|-----------|------|------|------|
| `defaultRestClient` | `RestClient` | 无同名 Bean | 默认客户端（read=30s） |
| `longRunningRestClient` | `RestClient` | `clients.long-running.enabled=true` | 长时间运行客户端（read=600s） |
| `loggingInterceptor` | `LoggingInterceptor` | `logging.enabled=true` | 日志拦截器 |

> **注意**: `JdkClientHttpRequestFactory` 不再作为 Bean 暴露，改为在 `defaultRestClient` 内部直接创建。
> 这是为了避免被 Spring Cloud LoadBalancer 的 BeanPostProcessor 包装，导致调用外部 URL 时出现
> `Service Instance cannot be null` 错误。

## 依赖关系

```
patra-spring-boot-starter-rest-client
├── patra-common-core          # 通用工具类
├── patra-spring-boot-starter-core  # 核心 starter
├── spring-boot-autoconfigure  # 自动配置支持
├── spring-web                 # RestClient
├── spring-retry (optional)    # 重试支持
└── apm-toolkit-trace          # SkyWalking 追踪
```

## 与可观测性集成

追踪传播和指标收集功能由 `patra-spring-boot-starter-observability` 提供。
引入该 starter 后，会自动注入追踪和指标拦截器，无需额外配置。

## 注意事项

1. **敏感信息保护**：`log-headers` 和 `log-body` 默认关闭，避免敏感信息泄露
2. **Body 截断**：启用 `log-body` 时，超过 `max-body-log-length` 的内容会被截断
3. **日志级别**：日志拦截器仅在 DEBUG 级别输出，生产环境建议保持 INFO 级别
4. **重试策略**：重试功能默认关闭，启用前请评估对下游服务的影响

## 版本要求

- Java 21+
- Spring Boot 3.5+
- Spring Framework 6.2+
