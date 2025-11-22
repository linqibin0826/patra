# patra-spring-boot-starter-rest-client

## 概述

REST 客户端 Starter，提供统一的 HTTP 调用能力，集成日志记录、分布式追踪传播和性能指标收集。

本 Starter 基于 **Spring RestClient**（使用底层 JDK 21 HttpClient）提供类型安全的 HTTP 调用，支持自动配置超时、拦截器和重试机制，为基础设施层（Infrastructure Layer）提供标准化的外部 API 调用能力。

**HTTP 客户端技术选型**：
- **Spring RestClient**：Spring 官方推荐的现代化 HTTP 客户端（Spring 6.1+）
- **JDK 21 HttpClient**：底层使用 JDK 原生 HTTP 客户端，支持 HTTP/2
- **类型安全**：完全基于流式 API，支持泛型和 ResponseEntity

## 核心功能

- **默认 RestClient Bean**：开箱即用的 `defaultRestClient` Bean
- **统一超时控制**：全局配置连接超时、读取超时和写入超时
- **拦截器链**：日志记录、追踪传播、指标收集（按优先级排序）
- **日志拦截器**：记录 HTTP 请求和响应（可配置 Headers/Body）
- **追踪拦截器**：自动传播 SkyWalking TraceID 到下游服务
- **指标拦截器**：集成 Micrometer，收集成功/失败计数和请求耗时
- **多客户端支持**：支持多个命名客户端配置（如 pubmed、epmc）
- **灵活配置**：支持全局默认值和客户端级覆盖

## 自动配置内容

### RestClientAutoConfiguration

`RestClientAutoConfiguration` 自动配置以下 Bean：

| Bean 名称 | 类型 | 描述 |
|-----------|------|------|
| `defaultRestClient` | `RestClient` | 默认 RestClient Bean（自动注入所有拦截器） |
| `defaultHttpRequestFactory` | `JdkClientHttpRequestFactory` | HTTP 请求工厂（配置超时，使用 JDK HttpClient） |
| `loggingInterceptor` | `LoggingInterceptor` | 日志拦截器（@Order(100)） |
| `tracingInterceptor` | `TracingInterceptor` | 追踪拦截器（@Order(50)） |
| `metricsInterceptor` | `MetricsInterceptor` | 指标拦截器（@Order(10)，需要 MeterRegistry） |

### 启用条件

- 配置属性 `patra.rest-client.enabled=true`（默认启用）
- 指标 Bean 需要 `MeterRegistry` 存在
- 各拦截器可独立启用/禁用

### 拦截器执行顺序

拦截器按 `@Order` 值**从小到大**执行：

1. **MetricsInterceptor** (@Order(10)) - 最先执行，记录开始时间
2. **TracingInterceptor** (@Order(50)) - 传播 TraceID 到下游
3. **LoggingInterceptor** (@Order(100)) - 最后执行，记录日志

## 主要组件

### RestClient（Spring 管理）

提供类型安全的 HTTP 调用能力：

- **流式 API**：链式调用，类型安全
- **自动配置**：超时、拦截器、默认 Headers 自动应用
- **底层实现**：JDK 21 HttpClient（支持 HTTP/2）
- **配置来源**：从 `RestClientProperties` 自动提取

**使用示例**：
```java
@Component
@RequiredArgsConstructor
public class ExternalApiClient {

    private final RestClient defaultRestClient;

    public UserResponse fetchUser(Long userId) {
        return defaultRestClient.get()
            .uri("/users/{id}", userId)
            .retrieve()
            .body(UserResponse.class);
    }

    public CreateResponse createUser(UserRequest request) {
        return defaultRestClient.post()
            .uri("/users")
            .contentType(MediaType.APPLICATION_JSON)
            .body(request)
            .retrieve()
            .body(CreateResponse.class);
    }
}
```

### LoggingInterceptor（日志拦截器）

记录 HTTP 请求和响应信息：

- **请求日志**：HTTP 方法、URI、Headers（可选）、Body（可选）
- **响应日志**：状态码、请求耗时
- **性能监控**：自动记录每个请求的耗时（毫秒）
- **配置控制**：可配置是否记录 Headers 和 Body（避免敏感信息泄露）

**配置示例**：
```yaml
patra:
  rest-client:
    interceptors:
      logging:
        enabled: true
        log-headers: false  # 避免记录敏感 Headers（如 Authorization）
        log-body: false     # 避免记录大文件或敏感数据
```

### TracingInterceptor（追踪拦截器）

自动传播分布式追踪上下文到下游服务：

- **SkyWalking 集成**：优先从 SkyWalking 提取 TraceID
- **MDC 降级**：如果 SkyWalking 未启用，从 MDC 提取 `traceId`
- **多 Header 传播**：支持多个追踪 Header（如 `X-Trace-ID`、`X-B3-TraceId`）
- **自动传播**：无需手动添加 TraceID 到请求头

**配置示例**：
```yaml
patra:
  rest-client:
    interceptors:
      tracing:
        enabled: true
        header-names:
          - X-Trace-ID
          - X-B3-TraceId
```

### MetricsInterceptor（指标拦截器）

收集 HTTP 请求的性能指标：

- **成功计数器**：`rest_client_requests_success_total`（2xx 响应）
- **失败计数器**：`rest_client_requests_failure_total`（非 2xx 或异常）
- **请求耗时**：`rest_client_request_duration_seconds`（分布汇总）

**Prometheus 查询示例**：
```promql
# 请求成功率
rate(rest_client_requests_success_total[5m])
  / (rate(rest_client_requests_success_total[5m]) + rate(rest_client_requests_failure_total[5m]))

# P95 请求耗时
histogram_quantile(0.95, rate(rest_client_request_duration_seconds_bucket[5m]))
```

## 配置属性

**配置前缀**：`patra.rest-client`

### 全局配置

```yaml
patra:
  rest-client:
    enabled: true                    # 启用自动配置（默认 true）
    timeout:
      connect: 10s                   # 连接超时（默认 10 秒）
      read: 30s                      # 读取超时（默认 30 秒）
      write: 30s                     # 写入超时（默认 30 秒）
    retry:
      enabled: false                 # 启用重试（默认 false，避免过于激进）
      max-attempts: 3                # 最大重试次数
      wait-duration: 1000            # 初始重试等待时间（毫秒）
      backoff-multiplier: 2.0        # 退避倍数
      max-wait-duration: 30000       # 最大等待时间（毫秒）
    interceptors:
      logging:
        enabled: true                # 启用日志拦截器
        log-headers: false           # 是否记录 HTTP 头（避免敏感信息泄露）
        log-body: false              # 是否记录请求/响应体
      tracing:
        enabled: true                # 启用追踪拦截器
        header-names:                # 追踪 Header 名称列表
          - X-Trace-ID
          - X-B3-TraceId
      metrics:
        enabled: true                # 启用指标拦截器
```

### 多客户端配置

```yaml
patra:
  rest-client:
    clients:
      pubmed:                        # 客户端名称（自定义）
        base-url: "https://eutils.ncbi.nlm.nih.gov/entrez/eutils"
        default-headers:
          User-Agent: "Patra-Ingest/0.1.0"
        timeout:
          read: 60s                  # 覆盖 PubMed 读取超时
      epmc:
        base-url: "https://www.ebi.ac.uk/europepmc/webservices/rest"
        timeout:
          read: 45s
```

**注意**：当前版本（v0.1.0）多客户端配置仅用于文档化，实际需要手动创建命名 Bean。未来版本将支持自动创建命名客户端。

### 配置优先级

1. **客户端级配置** > 全局默认值
2. **超时配置**：客户端 timeout > 全局 timeout
3. **拦截器**：全局启用/禁用，不支持客户端级覆盖

## 使用方式

### Maven 依赖

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-rest-client</artifactId>
</dependency>
```

**传递依赖**（自动包含）：
- `patra-spring-boot-starter-core`：核心基础设施
- `patra-common-core`：共享工具和异常
- `spring-web`：RestClient 支持
- `spring-retry`：重试机制（可选）
- `micrometer-core`：指标收集
- `apm-toolkit-trace`：SkyWalking 追踪

### 配置示例

**application.yml**：
```yaml
spring:
  application:
    name: patra-ingest

patra:
  rest-client:
    enabled: true
    timeout:
      connect: 10s
      read: 30s
    interceptors:
      logging:
        enabled: true
        log-headers: false
        log-body: false
      tracing:
        enabled: true
      metrics:
        enabled: true
```

### 代码示例

#### 基本使用（注入默认客户端）

```java
@Component
@RequiredArgsConstructor
public class PubMedApiClient {

    private final RestClient defaultRestClient;

    public ESearchResponse search(String query) {
        return defaultRestClient.get()
            .uri("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?db=pubmed&term={query}&retmode=json", query)
            .retrieve()
            .body(ESearchResponse.class);
    }

    public String fetchArticle(String pmid) {
        return defaultRestClient.get()
            .uri("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/efetch.fcgi?db=pubmed&id={pmid}&retmode=xml", pmid)
            .retrieve()
            .body(String.class);
    }
}
```

#### 高级使用（自定义客户端）

```java
@Configuration
public class CustomRestClientConfig {

    @Bean
    public RestClient pubmedRestClient(
        JdkClientHttpRequestFactory requestFactory,
        ObjectProvider<ClientHttpRequestInterceptor> interceptorsProvider
    ) {
        // 创建专用于 PubMed 的 RestClient
        return RestClient.builder()
            .requestFactory(requestFactory)
            .baseUrl("https://eutils.ncbi.nlm.nih.gov/entrez/eutils")
            .defaultHeader("User-Agent", "Patra-Ingest/0.1.0")
            .requestInterceptors(list ->
                list.addAll(interceptorsProvider.orderedStream().toList())
            )
            .build();
    }
}
```

#### 文件下载（流式响应）

```java
@Component
@RequiredArgsConstructor
public class FileDownloadService {

    private final RestClient defaultRestClient;

    public void downloadFile(String url, Path outputPath) throws IOException {
        defaultRestClient.get()
            .uri(url)
            .exchange((request, response) -> {
                if (response.getStatusCode().is2xxSuccessful()) {
                    Files.copy(response.getBody(), outputPath, StandardCopyOption.REPLACE_EXISTING);
                    return true;
                }
                throw new RuntimeException("Download failed: " + response.getStatusCode());
            });
    }
}
```

#### 错误处理

```java
@Component
@RequiredArgsConstructor
public class ResilientApiClient {

    private final RestClient defaultRestClient;

    public Optional<UserResponse> fetchUserSafely(Long userId) {
        try {
            return Optional.ofNullable(
                defaultRestClient.get()
                    .uri("/users/{id}", userId)
                    .retrieve()
                    .onStatus(
                        status -> status.value() == 404,
                        (request, response) -> {
                            throw new UserNotFoundException("User not found: " + userId);
                        }
                    )
                    .body(UserResponse.class)
            );
        } catch (UserNotFoundException e) {
            log.warn("User not found: {}", userId);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to fetch user: {}", userId, e);
            throw new ExternalApiException("External API error", e);
        }
    }
}
```

## 架构集成

### 六边形架构中的位置

本 Starter 位于**框架层（Framework Layer）**，为基础设施层提供技术支撑：

```
Domain Layer (patra-xxx-domain)
  - ExternalApiPort (业务端口接口)
    ↑ implements
Infrastructure Layer (patra-xxx-infra)
  - ExternalApiAdapter (桥接适配器)
    ↓ uses
Framework Layer (patra-starter-rest-client) ← 本 Starter
  - defaultRestClient (统一 HTTP 客户端)
  - LoggingInterceptor (日志记录)
  - TracingInterceptor (追踪传播)
  - MetricsInterceptor (指标收集)
    ↓ calls
External APIs (第三方服务)
  - PubMed API
  - Europe PMC API
  - 其他 REST API
```

### 与其他 Starter 的关系

**依赖关系**：
- **patra-spring-boot-starter-core**：提供错误处理、JSON 序列化、追踪传播等核心能力
- **patra-spring-boot-starter-web**：Adapter 层使用（提供 REST 控制器能力）
- **patra-spring-boot-starter-provenance**：数据源客户端使用（PubMed、EPMC 等）

**分层使用**：
- **Adapter 层**：不应使用（Adapter 提供 REST 服务，不调用外部 API）
- **Infrastructure 层**：主要使用场景（调用外部 API、下载文件）
- **Domain 层**：禁止使用（Domain 层不依赖框架）

### 使用场景

**典型场景**：
1. **外部 API 调用**：调用 PubMed、Europe PMC、Crossref 等外部 REST API
2. **文件下载**：从外部 URL 下载文件（XML、PDF、图片等）
3. **Webhook 发送**：向外部系统发送 Webhook 通知
4. **服务间调用**：微服务之间的 HTTP 调用（优先使用 Feign，简单调用使用 RestClient）

**Infrastructure 层使用示例**：
```java
@Repository
@RequiredArgsConstructor
public class PubMedDataAdapter implements ProvenanceDataPort {

    private final RestClient defaultRestClient;

    @Override
    public ESearchResponse search(String query) {
        // 调用外部 PubMed API
        return defaultRestClient.get()
            .uri("https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?db=pubmed&term={query}&retmode=json", query)
            .retrieve()
            .body(ESearchResponse.class);
    }
}
```

## 扩展点

### 自定义拦截器

```java
@Component
@Order(20)  // 在 TracingInterceptor 之后、LoggingInterceptor 之前执行
public class CustomAuthInterceptor implements ClientHttpRequestInterceptor {

    @Override
    public ClientHttpResponse intercept(
        HttpRequest request, byte[] body, ClientHttpRequestExecution execution
    ) throws IOException {
        // 添加认证 Header
        request.getHeaders().add("Authorization", "Bearer " + getToken());
        return execution.execute(request, body);
    }

    private String getToken() {
        // 获取访问令牌逻辑
        return "your-access-token";
    }
}
```

### 禁用自动配置

如需完全自定义配置，可禁用自动配置：

```yaml
patra:
  rest-client:
    enabled: false
```

```java
@Configuration
public class CustomRestClientConfig {

    @Bean
    public RestClient customRestClient() {
        return RestClient.builder()
            .baseUrl("https://custom-api.example.com")
            .defaultHeader("Custom-Header", "value")
            .build();
    }
}
```

### 自定义超时配置

```java
@Configuration
public class TimeoutConfig {

    @Bean
    public JdkClientHttpRequestFactory customRequestFactory() {
        var httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))  // 自定义连接超时
            .build();

        var factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMinutes(2));  // 自定义读取超时
        return factory;
    }
}
```

## 技术栈

- **Spring Boot**: 3.5.7
- **Spring Web**: 6.2.2（提供 RestClient）
- **JDK HttpClient**: JDK 21 内置（HTTP/2 支持）
- **Spring Retry**: 2.0.10（可选）
- **Micrometer**: 1.15.1
- **SkyWalking APM Toolkit**: 9.4.0

## 最佳实践

### 1. 超时配置

**推荐**：根据 API 响应时间特性配置合理的超时：
- **快速 API**（如查询）：`connect=5s, read=10s`
- **慢速 API**（如复杂计算）：`connect=10s, read=60s`
- **文件下载**：`connect=10s, read=300s`（5 分钟）

### 2. 日志配置

**生产环境推荐**：
```yaml
patra:
  rest-client:
    interceptors:
      logging:
        enabled: true
        log-headers: false  # 避免记录敏感 Headers（如 API Key）
        log-body: false     # 避免记录大文件或敏感数据
```

**开发环境推荐**：
```yaml
patra:
  rest-client:
    interceptors:
      logging:
        enabled: true
        log-headers: true   # 方便调试
        log-body: true      # 查看请求/响应内容
```

### 3. 重试策略

**推荐**：仅对幂等操作启用重试：
- ✅ **GET 请求**：安全启用重试
- ❌ **POST/PUT/DELETE**：避免重复操作，除非业务逻辑保证幂等性

```yaml
patra:
  rest-client:
    retry:
      enabled: true       # 仅对 GET 请求启用
      max-attempts: 3
      wait-duration: 1000
```

### 4. 避免在 Domain 层使用

**错误示例**：
```java
// ❌ 错误：Domain 层不应依赖框架
package com.patra.ingest.domain.service;

import org.springframework.web.client.RestClient;

public class PlanDomainService {
    private final RestClient restClient;  // ❌ 违反依赖方向
}
```

**正确示例**：
```java
// ✅ 正确：在 Infrastructure 层使用
package com.patra.ingest.infra.adapter;

import org.springframework.web.client.RestClient;

@Repository
public class ExternalApiAdapter implements ExternalApiPort {
    private final RestClient defaultRestClient;  // ✅ 符合六边形架构
}
```

### 5. 统一使用 defaultRestClient

**推荐**：除非有特殊需求（如不同超时配置），否则统一使用 `defaultRestClient`：

```java
// ✅ 推荐：注入默认客户端
@RequiredArgsConstructor
public class ApiClient {
    private final RestClient defaultRestClient;
}

// ❌ 避免：手动创建 RestClient（除非有充分理由）
public class ApiClient {
    private final RestClient customClient = RestClient.create();
}
```

---

**最后更新**：2025-01-22
**维护者**：Patra Team
