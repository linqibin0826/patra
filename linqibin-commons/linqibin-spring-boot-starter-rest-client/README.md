# Patra Spring Boot Starter - REST Client

基于 Spring 6 RestClient 的 HTTP 客户端自动配置，提供开箱即用的超时、日志、重试等功能。

## 特性

- **零配置启动**：引入依赖即可使用，默认配置满足大多数场景
- **JDK HttpClient**：基于 JDK 11+ 原生 HTTP 客户端，支持 HTTP/2
- **灵活的超时配置**：支持全局和客户端级别的超时设置
- **日志拦截器**：可选记录请求/响应的 Headers 和 Body
- **拦截器扩展**：支持注入自定义 `ClientHttpRequestInterceptor`
- **多客户端配置**：支持按用途定义多个客户端配置
- **文件下载进度监控**：支持进度百分比、下载速度、剩余时间估算
- **流式下载支持**：提供基于 WebClient 的流式下载能力，解决长时间传输问题

## 快速开始

### 添加依赖

```xml
<dependency>
    <groupId>dev.linqibin.patra</groupId>
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
linqibin:
  starter:
    rest-client:
      clients:
        long-running:
          enabled: false
```

### 流式下载 WebClient

对于需要流式读取响应体的场景（如大文件流式处理、边下载边解析），使用 `streamingWebClient`：

```java
@Service
public class StreamingService {

    private final WebClient webClient;

    /// 通过 @Qualifier 注入流式下载客户端
    public StreamingService(
            @Qualifier("streamingWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    public Flux<DataBuffer> streamDownload(URI url) {
        return webClient.get()
            .uri(url)
            .retrieve()
            .bodyToFlux(DataBuffer.class);
    }
}
```

**为什么需要 WebClient？**

`RestClient.exchange(close=false)` 在长时间流式传输过程中可能意外关闭 InputStream，
导致 `IOException: closed` 异常。WebClient + Reactor Netty 原生支持 Reactive Streams 背压机制，
连接生命周期由响应式订阅控制，更适合流式下载场景。

**默认配置**：
- 连接超时：30 秒
- 响应超时：30 分钟（适合大文件流式下载）
- 内存限制：-1（不限制，使用流式处理）

**启用条件**：
- 需要 `spring-webflux` 和 `reactor-netty-http` 依赖
- `linqibin.starter.rest-client.streaming.enabled=true`（默认启用）

**禁用方式**：

```yaml
linqibin:
  starter:
    rest-client:
      streaming:
        enabled: false
```

### 文件下载与进度监控

使用 `DownloadClient` 下载大文件并监控进度：

```java
@Service
public class FileService {

    private final DownloadClient downloadClient;
    private final ProgressListener progressListener;

    public FileService(DownloadClient downloadClient, ProgressListener progressListener) {
        this.downloadClient = downloadClient;
        this.progressListener = progressListener;
    }

    public Path downloadFile(URI url) {
        DownloadResult result =
            downloadClient.downloadToTemp(
                url,
                DownloadOptions.withProgressListener(progressListener));
        return result.filePath();
    }
}
```

**流式下载示例**：

```java
try (StreamingDownloadResponse result = downloadClient.openStream(url)) {
    String content = new String(result.inputStream().readAllBytes(), StandardCharsets.UTF_8);
}
```

**FTP 下载示例**：

```java
// 方式 1：使用全局配置的 FTP 账号（需在 application.yml 中配置）
DownloadResult result = downloadClient.downloadToTemp(
    URI.create("ftp://ftp.example.com/data/file.xml"), null);

// 方式 2：调用时指定 FTP 账号（覆盖全局配置）
DownloadResult result = downloadClient.downloadToTemp(
    URI.create("ftp://ftp.example.com/data/file.xml"),
    DownloadOptions.withFtpCredentials("username", "password"));

// 方式 3：使用 FtpCredentials 对象
FtpCredentials credentials = new FtpCredentials("username", "password");
DownloadResult result = downloadClient.downloadToTemp(
    URI.create("ftp://ftp.example.com/data/file.xml"),
    DownloadOptions.withFtpCredentials(credentials));
```

> **注意**：FTP 账号密码无默认值。匿名 FTP 可不填；私有 FTP 必须通过全局配置或 `DownloadOptions` 显式传入。

**可选配置**（全局默认 + 单次覆盖）：

```yaml
linqibin:
  starter:
    rest-client:
      download:
        enabled: true
        base-dir: /data/patra/downloads
        temp-dir: /data/patra/tmp
        write-strategy: OVERWRITE
        create-dirs: true
        cleanup-on-failure: true
        buffer-size: 65536
        retry:
          enabled: true
          max-attempts: 3
          initial-backoff: 2s
          max-backoff: 30s
        ftp:
          enabled: true
          # FTP 账号密码无默认值，匿名 FTP 可不填，私有 FTP 必须配置或在 DownloadOptions 中传入
          # username: YOUR_FTP_USERNAME
          # password: YOUR_FTP_PASSWORD
          connect-timeout: 30s
          data-timeout: 30m
          passive-mode: true
          default-content-type: application/xml
```

**进度回调信息**（`DownloadProgress`）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `bytesDownloaded` | long | 已下载字节数 |
| `totalBytes` | long | 文件总大小（-1 表示未知） |
| `percentage` | int | 下载进度百分比（0-100） |
| `speedBytesPerSecond` | long | 当前下载速度（字节/秒） |
| `estimatedRemainingSeconds` | long | 预计剩余时间（秒） |
| `elapsedMillis` | long | 已用时间（毫秒） |

**便捷格式化方法**：

```java
progress.formattedSpeed()           // "12.5 MB/s"
progress.formattedRemainingTime()   // "2m 30s"
progress.formattedSize()            // "150 MB / 300 MB (50%)"
progress.formattedElapsedTime()     // "45.2s"
```

**自定义监听器**：

```java
@Component
public class CustomProgressListener implements ProgressListener {

    @Override
    public void onProgress(DownloadProgress progress) {
        System.out.printf("下载进度: %d%%, 速度: %s%n",
            progress.percentage(), progress.formattedSpeed());
    }

    @Override
    public void onComplete(DownloadProgress finalProgress) {
        System.out.printf("下载完成，耗时: %s%n",
            finalProgress.formattedElapsedTime());
    }

    @Override
    public void onError(Exception exception, DownloadProgress lastProgress) {
        System.err.println("下载失败: " + exception.getMessage());
    }
}
```

**记录的 Micrometer 指标**：

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `download.duration.seconds` | Timer | 下载耗时分布 |
| `download.bytes.total` | Counter | 累计下载字节数 |
| `download.count.total` | Counter | 累计下载次数（tag: `status=success/failure`） |
| `download.failure.total` | Counter | 失败次数（tag: `error_type`） |

## 配置项

所有配置项均以 `linqibin.starter.rest-client` 为前缀。

### 完整配置示例

```yaml
linqibin:
  starter:
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
| `streaming.enabled` | boolean | `true` | 是否启用流式 WebClient（需 spring-webflux 依赖） |

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
| `streamingWebClient` | `WebClient` | `streaming.enabled=true` + `WebClient` 类存在 | 流式下载专用 WebClient（响应超时 30 分钟） |
| `loggingInterceptor` | `LoggingInterceptor` | `logging.enabled=true` | 日志拦截器 |
| `downloadClient` | `DownloadClient` | 存在任一流式下载策略 | 统一下载客户端（流式 + 落盘） |
| `defaultProgressListener` | `ProgressListener` | 无同名 Bean | 组合日志和指标监听器 |

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
├── micrometer-core (optional) # 下载指标支持
├── spring-webflux (optional)  # 流式下载 WebClient 支持
└── reactor-netty-http (optional) # WebClient 底层 HTTP 客户端
```

## 与可观测性集成

追踪传播和指标收集功能由 `patra-spring-boot-starter-observability` 提供。
引入该 starter 后，会自动注入追踪和指标拦截器，无需额外配置。

## 注意事项

1. **敏感信息保护**：`log-headers` 和 `log-body` 默认关闭，避免敏感信息泄露
2. **Body 截断**：启用 `log-body` 时，超过 `max-body-log-length` 的内容会被截断
3. **日志级别**：日志拦截器仅在 DEBUG 级别输出，生产环境建议保持 INFO 级别
4. **重试策略**：重试功能默认关闭，启用前请评估对下游服务的影响

## 测试

模块测试默认全部可在无外网环境下执行。

依赖外部服务（隧道代理 `tunnel.qg.net:15561`、真实 HTTPS 站点）的连通性测试
通过 JUnit 5 `@Tag("external")` 标记，由根 `build-logic/linqibin.java-base.gradle.kts`
默认从 `./gradlew test` 中排除，避免 CI 与本地构建因外网波动 flake。

显式运行真实链路验证：

```bash
# 单模块
./gradlew :linqibin-spring-boot-starter-rest-client:test -PrunExternal

# 全仓
./gradlew test -PrunExternal
```

`TunnelProxyConnectivityTest` 同时需要环境变量 `PROXY_AUTH_KEY` / `PROXY_AUTH_PWD`，
未设置时通过 `Assumptions.assumeTrue` 自动跳过。

## 版本要求

- Java 25+
- Spring Boot 4.0+
- Spring Framework 7.0+
