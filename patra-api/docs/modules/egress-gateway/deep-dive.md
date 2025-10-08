# patra-egress-gateway 深入指南

> 南向网关（Egress Gateway）- 统一管理所有出站外部服务调用的微服务

---

## 1. 模块定位

### 服务/组件作用
**patra-egress-gateway** 是 Papertrace 项目中负责统一管理所有出站外部服务调用的微服务。它为上游业务方提供标准化的外部服务访问能力，包括：
- 医学文献数据源（PubMed、PMC、Crossref 等）
- 对象存储服务（OSS、MinIO、S3 等）
- 邮件服务
- 短信/验证码服务

### 主要消费者
- **patra-ingest**：调用外部医学文献 API 采集数据
- **patra-storage**：调用 OSS/S3 存储文件
- **patra-notification**：调用邮件/短信服务发送通知
- 其他需要访问外部服务的微服务

### 架构边界
- **所属分层**：基础设施层（Infrastructure Layer）
- **依赖方向**：被上游业务服务依赖，不依赖其他业务服务
- **架构模式**：六边形架构（Hexagonal Architecture）+ DDD
- **通信方式**：提供 Feign RPC 接口供内部服务调用

---

## 2. 核心能力

### 2.1 透传外部服务调用
- **能力摘要**：接收业务方的请求参数和认证信息，原样透传给外部服务
- **价值**：业务方无需关心 HTTP 客户端实现细节，专注业务逻辑

### 2.2 弹性能力提供
- **能力摘要**：提供限流、重试、熔断、超时等通用弹性能力（基于 Resilience4j）
- **价值**：保护外部服务和内部系统，避免雪崩效应

### 2.3 响应语义统一
- **能力摘要**：将外部服务的响应封装为统一的语义结构（ResponseEnvelope）
- **价值**：提供一致的响应格式，包含限流状态、重试建议、响应头白名单等元信息

### 2.4 配置管理
- **能力摘要**：管理系统级弹性配置，支持业务方覆盖（不超过最大值）
- **价值**：平衡灵活性与安全性，避免业务方配置不当导致系统不稳定

### 2.5 可观测性
- **能力摘要**：记录每次外部调用的详细日志和指标（traceId、duration、statusCode）
- **价值**：方便排查问题、监控外部服务健康度、优化性能

---

## 3. 分层结构与依赖

### 3.1 子模块/包结构
```
patra-egress-gateway/
├── patra-egress-gateway-api/          # 外部 RPC 契约（DTOs、Client 接口）
├── patra-egress-gateway-adapter/      # 入站适配器（REST Controller）
├── patra-egress-gateway-app/          # 应用层（用例编排）
├── patra-egress-gateway-domain/       # 领域层（聚合、值对象、端口）
├── patra-egress-gateway-infra/        # 基础设施层（端口实现）
└── patra-egress-gateway-boot/         # 启动模块
```

### 3.2 关键依赖
| 依赖 | 版本 | 作用 |
|------|------|------|
| `patra-spring-boot-starter-core` | 0.1.0 | 统一错误处理、Trace SPI |
| `patra-spring-boot-starter-web` | 0.1.0 | Web 配置、全局异常处理 |
| `resilience4j-spring-boot3` | 2.2.0 | 弹性能力（限流、重试、熔断） |
| `spring-web` | 6.1.5 | RestClient HTTP 客户端 |
| `spring-cloud-starter-openfeign` | 4.1.1 | Feign 声明式 RPC |

### 3.3 禁止事项
- ❌ 不进行业务数据转换和处理（只透传）
- ❌ 不包含业务规则判断
- ❌ 不持久化业务数据
- ❌ 不解析外部服务的业务数据内容
- ❌ Domain 层不引入任何框架依赖（保持纯 Java）

---

## 4. 领域模型

### 4.1 核心聚合

#### ResilienceConfigAggregate（弹性配置聚合根）
```java
public class ResilienceConfigAggregate {
    private final ResilienceConfig systemDefaultConfig;  // 系统默认配置
    private final ResilienceConfig systemMaxConfig;       // 系统最大配置
    
    // 静态工厂方法：加载系统配置
    public static ResilienceConfigAggregate loadSystemConfig(ConfigPort configPort);
    
    // 合并业务方配置（不超过最大值）
    public ResilienceConfig mergeWithCallerConfig(ResilienceConfig callerConfig);
}
```

**职责**：
- 加载系统级默认配置和最大配置
- 合并业务方传入的配置，确保不超过系统最大值
- 校验配置有效性

### 4.2 核心值对象

#### ResilienceConfig（弹性配置）
```java
public record ResilienceConfig(
    Duration timeout,                       // 超时时间
    int maxRetries,                         // 最大重试次数
    Duration retryBackoff,                  // 重试退避时间
    int rateLimit,                          // 限流速率（每秒请求数）
    int circuitBreakerThreshold,            // 熔断阈值（失败次数）
    Duration circuitBreakerWindow,          // 熔断时间窗口
    List<String> responseHeaderWhitelist    // 响应头白名单
) {
    public void validate();                 // 校验配置有效性
    public ResilienceConfig mergeWithMax(ResilienceConfig max); // 合并配置
}
```

#### HttpRequest（HTTP 请求）
```java
public record HttpRequest(
    String url,
    String method,
    Map<String, String> headers,
    String body
) {}
```

#### ResponseEnvelope（响应信封）
```java
public record ResponseEnvelope(
    int statusCode,
    String body,
    Map<String, String> headers,           // 经过白名单过滤的响应头
    RateLimitStatus rateLimitStatus,       // 限流状态
    RetryAdvice retryAdvice,               // 重试建议
    String contentHash                     // 响应内容哈希（用于缓存）
) {}
```

### 4.3 端口接口（Ports）

#### ConfigPort（配置端口）
```java
public interface ConfigPort {
    ResilienceConfig loadSystemDefaultConfig();
    ResilienceConfig loadSystemMaxConfig();
}
```

#### HttpClientPort（HTTP 客户端端口）
```java
public interface HttpClientPort {
    HttpResponse call(HttpRequest request, ResilienceConfig config);
}
```

---

## 5. 弹性模式（Resilience4j 集成）

### 5.1 限流（Rate Limiting）
```yaml
resilience4j:
  ratelimiter:
    configs:
      default:
        limit-for-period: 100       # 每个周期允许的请求数
        limit-refresh-period: 1s    # 周期时长
        timeout-duration: 0s        # 等待许可的超时时间
```

### 5.2 重试（Retry）
```yaml
resilience4j:
  retry:
    configs:
      default:
        max-attempts: 3             # 最大重试次数
        wait-duration: 1s           # 重试间隔
        exponential-backoff-multiplier: 2  # 指数退避倍数
        retry-exceptions:
          - java.net.SocketTimeoutException
          - org.springframework.web.client.HttpServerErrorException
```

### 5.3 熔断（Circuit Breaker）
```yaml
resilience4j:
  circuitbreaker:
    configs:
      default:
        failure-rate-threshold: 50       # 失败率阈值（百分比）
        slow-call-rate-threshold: 50     # 慢调用阈值（百分比）
        slow-call-duration-threshold: 2s # 慢调用持续时间
        wait-duration-in-open-state: 10s # 熔断打开后等待时间
        sliding-window-type: COUNT_BASED # 滑动窗口类型
        sliding-window-size: 10          # 滑动窗口大小
```

### 5.4 超时（Time Limiter）
```yaml
resilience4j:
  timelimiter:
    configs:
      default:
        timeout-duration: 30s       # 超时时间
        cancel-running-future: true # 超时后取消正在运行的任务
```

---

## 6. API 设计

### 6.1 Feign Client 接口
```java
@FeignClient(
    name = "patra-egress-gateway",
    path = "/internal/egress"
)
public interface EgressGatewayClient {
    
    @PostMapping("/call")
    ExternalCallResponseDTO call(@Valid @RequestBody ExternalCallRequestDTO request);
}
```

### 6.2 请求 DTO
```java
public record ExternalCallRequestDTO(
    @NotBlank String url,
    @NotBlank String method,
    Map<String, String> headers,
    String body,
    ResilienceConfigDTO resilienceConfig  // 可选，业务方覆盖配置
) {}

public record ResilienceConfigDTO(
    Integer timeout,           // 单位：秒
    Integer maxRetries,
    Integer rateLimit,
    List<String> responseHeaderWhitelist
) {}
```

### 6.3 响应 DTO
```java
public record ExternalCallResponseDTO(
    ResponseEnvelopeDTO envelope,
    long durationMs,
    int retryCount,
    String traceId
) {}

public record ResponseEnvelopeDTO(
    int statusCode,
    String body,
    Map<String, String> headers,
    RateLimitStatusDTO rateLimitStatus,
    RetryAdviceDTO retryAdvice,
    String contentHash
) {}
```

---

## 7. 运行与配置

### 7.1 引入方式
```xml
<dependency>
    <groupId>com.papertrace</groupId>
    <artifactId>patra-egress-gateway-api</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### 7.2 必要配置
```yaml
# application.yaml
patra:
  egress:
    resilience:
      max:
        timeout: 60s           # 系统最大超时时间
        maxRetries: 5          # 系统最大重试次数
        rateLimit: 1000        # 系统最大限流速率
      default:
        timeout: 30s           # 系统默认超时时间
        maxRetries: 3          # 系统默认重试次数
        rateLimit: 100         # 系统默认限流速率
        responseHeaderWhitelist:
          - Content-Type
          - Content-Length
          - X-RateLimit-Limit
          - X-RateLimit-Remaining
          - X-RateLimit-Reset
          - Retry-After

# Nacos 配置（Data ID: egress-gateway.yaml）
resilience4j:
  ratelimiter:
    configs:
      default:
        limit-for-period: 100
        limit-refresh-period: 1s
  retry:
    configs:
      default:
        max-attempts: 3
        wait-duration: 1s
  circuitbreaker:
    configs:
      default:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
```

### 7.3 启动步骤
```bash
# 1. 编译模块
mvn -q -DskipTests compile

# 2. 打包
mvn clean package -DskipTests

# 3. 运行
mvn -pl patra-egress-gateway/patra-egress-gateway-boot spring-boot:run

# 或使用 jar 包
java -jar patra-egress-gateway-boot/target/patra-egress-gateway-boot-0.1.0-SNAPSHOT.jar
```

---

## 8. 观测与运维

### 8.1 日志/指标

#### 关键日志字段
```java
// 请求开始
log.info("[EGRESS][ADAPTER] Received external call request: url={} method={}", url, method);

// 配置合并
log.debug("[EGRESS][APP] Merging caller config with system config: traceId={}", traceId);

// 调用完成
log.info("[EGRESS][APP] External call completed: statusCode={} duration={}ms traceId={}", 
         statusCode, duration, traceId);

// 调用失败
log.error("[EGRESS][APP] External call failed: url={} duration={}ms error={} traceId={}",
          url, duration, error, traceId, e);
```

#### Micrometer 指标（规划中）
```java
// 请求计数
@Timed(value = "egress.call", histogram = true)
Counter.builder("egress.call.total")
    .tag("method", method)
    .tag("status", statusCode)
    .register(meterRegistry);

// 请求时长分布
Timer.builder("egress.call.duration")
    .tag("url", sanitizeUrl(url))
    .publishPercentiles(0.5, 0.95, 0.99)
    .register(meterRegistry);

// 限流指标
Gauge.builder("egress.ratelimit.remaining", rateLimitStatus::getRemaining)
    .register(meterRegistry);
```

### 8.2 排障手册

| 问题 | 排查路径 | 解决方案 |
|------|---------|---------|
| 外部调用超时 | 1. 检查 traceId 日志<br>2. 查看 duration 是否接近 timeout<br>3. 检查外部服务健康度 | 1. 增大 timeout 配置<br>2. 检查网络连接<br>3. 联系外部服务提供方 |
| 限流触发 | 1. 查看 `X-RateLimit-Remaining` 响应头<br>2. 检查 rateLimit 配置 | 1. 增大 rateLimit 配置<br>2. 优化调用频率<br>3. 使用批量接口 |
| 熔断打开 | 1. 查看失败率指标<br>2. 检查 circuitBreakerThreshold 配置 | 1. 等待熔断窗口过期<br>2. 检查外部服务稳定性<br>3. 调整熔断阈值 |
| 响应头丢失 | 1. 检查 responseHeaderWhitelist 配置<br>2. 查看原始响应头 | 1. 添加需要的响应头到白名单<br>2. 确认外部服务返回该响应头 |

### 8.3 健康检查
```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  health:
    circuitbreakers:
      enabled: true
    ratelimiters:
      enabled: true
```

访问健康检查端点：
```bash
# 整体健康状态
curl http://localhost:8082/actuator/health

# 熔断器状态
curl http://localhost:8082/actuator/health/circuitbreakers

# 限流器状态
curl http://localhost:8082/actuator/health/ratelimiters
```

---

## 9. 使用示例

### 9.1 调用 PubMed API
```java
@Service
@RequiredArgsConstructor
public class PubMedService {
    
    private final EgressGatewayClient egressClient;
    
    public String searchArticles(String query) {
        // 构建请求
        ExternalCallRequestDTO request = new ExternalCallRequestDTO(
            "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?db=pubmed&term=" + query,
            "GET",
            Map.of("User-Agent", "Papertrace/0.1.0"),
            null,
            // 自定义弹性配置：PubMed API 较慢，增大超时时间
            new ResilienceConfigDTO(60, 5, 10, null)
        );
        
        // 调用南向网关
        ExternalCallResponseDTO response = egressClient.call(request);
        
        // 检查响应
        if (response.envelope().statusCode() != 200) {
            throw new RuntimeException("PubMed API call failed: " + response.envelope().statusCode());
        }
        
        return response.envelope().body();
    }
}
```

### 9.2 调用 OSS API
```java
@Service
@RequiredArgsConstructor
public class OssService {
    
    private final EgressGatewayClient egressClient;
    
    public void uploadFile(String bucketName, String objectKey, byte[] fileContent) {
        // 生成签名（省略）
        String signature = generateSignature(bucketName, objectKey);
        
        // 构建请求
        ExternalCallRequestDTO request = new ExternalCallRequestDTO(
            "https://" + bucketName + ".oss-cn-hangzhou.aliyuncs.com/" + objectKey,
            "PUT",
            Map.of(
                "Authorization", "OSS " + accessKeyId + ":" + signature,
                "Content-Type", "application/pdf",
                "Content-Length", String.valueOf(fileContent.length)
            ),
            Base64.getEncoder().encodeToString(fileContent),
            // OSS 上传需要较长超时时间
            new ResilienceConfigDTO(120, 3, 5, List.of("ETag", "x-oss-request-id"))
        );
        
        // 调用南向网关
        ExternalCallResponseDTO response = egressClient.call(request);
        
        // 检查响应
        if (response.envelope().statusCode() != 200) {
            throw new RuntimeException("OSS upload failed: " + response.envelope().statusCode());
        }
        
        log.info("File uploaded successfully: ETag={}", 
                 response.envelope().headers().get("ETag"));
    }
}
```

### 9.3 处理限流和重试
```java
@Service
@RequiredArgsConstructor
public class ExternalApiService {
    
    private final EgressGatewayClient egressClient;
    
    public String callWithRetry(String url) {
        ExternalCallRequestDTO request = new ExternalCallRequestDTO(
            url, "GET", Map.of(), null, null
        );
        
        ExternalCallResponseDTO response = egressClient.call(request);
        
        // 检查限流状态
        RateLimitStatusDTO rateLimit = response.envelope().rateLimitStatus();
        if (rateLimit.remaining() < 10) {
            log.warn("Rate limit approaching: remaining={} resetAt={}", 
                     rateLimit.remaining(), rateLimit.resetAt());
        }
        
        // 检查重试建议
        RetryAdviceDTO retryAdvice = response.envelope().retryAdvice();
        if (retryAdvice.shouldRetry()) {
            log.info("Retry suggested after {}s: reason={}", 
                     retryAdvice.retryAfter().toSeconds(), retryAdvice.reason());
            // 业务方可以选择是否遵循重试建议
        }
        
        return response.envelope().body();
    }
}
```

---

## 10. 最佳实践

### 10.1 弹性配置建议

#### PubMed/PMC API
```java
new ResilienceConfigDTO(
    60,    // 超时 60s（官方 API 较慢）
    5,     // 最大重试 5 次
    10,    // 限流 10 req/s（符合官方限制）
    List.of("X-RateLimit-Limit", "X-RateLimit-Remaining", "Retry-After")
)
```

#### OSS/S3 API
```java
new ResilienceConfigDTO(
    120,   // 超时 120s（大文件上传）
    3,     // 最大重试 3 次
    20,    // 限流 20 req/s
    List.of("ETag", "x-oss-request-id", "x-amz-request-id")
)
```

#### 邮件/短信 API
```java
new ResilienceConfigDTO(
    30,    // 超时 30s
    3,     // 最大重试 3 次
    5,     // 限流 5 req/s
    List.of("Message-ID", "X-Message-Id")
)
```

### 10.2 错误处理策略

#### 可重试错误（4xx 客户端错误除外）
```java
// 南向网关会自动重试以下错误：
// - 网络超时（SocketTimeoutException）
// - 连接失败（ConnectException）
// - 5xx 服务器错误（HttpServerErrorException）
// - 429 Too Many Requests（需检查 Retry-After）

// 业务方只需捕获最终失败
try {
    response = egressClient.call(request);
} catch (FeignException.ServiceUnavailable e) {
    // 重试耗尽后仍失败
    log.error("External service unavailable after retries", e);
}
```

#### 不可重试错误（业务方处理）
```java
// 以下错误不会重试，业务方需自行处理：
// - 400 Bad Request（参数错误）
// - 401 Unauthorized（认证失败）
// - 403 Forbidden（权限不足）
// - 404 Not Found（资源不存在）

if (response.envelope().statusCode() == 401) {
    // 刷新 token 并重试
    refreshToken();
    response = egressClient.call(request);
}
```

### 10.3 响应头白名单策略
```java
// 推荐配置：只保留必要的响应头，避免泄露敏感信息
List<String> whitelist = List.of(
    // 标准响应头
    "Content-Type",
    "Content-Length",
    "Content-Encoding",
    "Content-Language",
    
    // 限流相关
    "X-RateLimit-Limit",
    "X-RateLimit-Remaining",
    "X-RateLimit-Reset",
    "Retry-After",
    
    // 缓存相关
    "ETag",
    "Last-Modified",
    "Cache-Control",
    
    // 业务相关
    "X-Request-Id",
    "X-Correlation-Id"
);

// 避免返回以下响应头：
// - Set-Cookie（敏感信息）
// - Authorization（认证信息）
// - X-Powered-By（暴露技术栈）
// - Server（暴露服务器信息）
```

### 10.4 性能优化建议
```java
// 1. 复用 HTTP 连接（RestClient 默认启用连接池）
@Bean
public RestClient.Builder restClientBuilder() {
    return RestClient.builder()
        .requestFactory(new JdkClientHttpRequestFactory(
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build()
        ));
}

// 2. 使用批量接口（减少调用次数）
List<ExternalCallRequestDTO> batchRequests = ...; // 一次请求多个资源

// 3. 启用响应缓存（基于 contentHash）
if (cachedResponse != null && cachedResponse.contentHash().equals(previousHash)) {
    return cachedResponse; // 使用缓存
}

// 4. 异步调用（非阻塞）
CompletableFuture<ExternalCallResponseDTO> future = 
    CompletableFuture.supplyAsync(() -> egressClient.call(request));
```

---

## 11. 扩展指南

### 11.1 接入新的外部服务

#### Step 1: 创建专用 Client（可选）
```java
@FeignClient(name = "patra-egress-gateway", path = "/internal/egress")
public interface PubMedClient {
    
    default String searchArticles(String query) {
        ExternalCallRequestDTO request = new ExternalCallRequestDTO(
            "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/esearch.fcgi?term=" + query,
            "GET",
            Map.of("User-Agent", "Papertrace/0.1.0"),
            null,
            new ResilienceConfigDTO(60, 5, 10, null)
        );
        return call(request).envelope().body();
    }
    
    @PostMapping("/call")
    ExternalCallResponseDTO call(ExternalCallRequestDTO request);
}
```

#### Step 2: 配置专用弹性策略
```yaml
# Nacos: egress-gateway.yaml
patra:
  egress:
    services:
      pubmed:
        timeout: 60s
        maxRetries: 5
        rateLimit: 10
        responseHeaderWhitelist:
          - X-RateLimit-Limit
          - X-RateLimit-Remaining
```

#### Step 3: 使用专用 Client
```java
@Service
@RequiredArgsConstructor
public class ArticleCollectorService {
    
    private final PubMedClient pubMedClient;
    
    public void collectArticles(String query) {
        String result = pubMedClient.searchArticles(query);
        // 处理结果...
    }
}
```

### 11.2 自定义弹性策略
```java
// 业务方可以为特定场景自定义弹性配置
public class CustomResilienceConfig {
    
    // 场景1：大文件上传（超长超时）
    public static ResilienceConfigDTO largeFileUpload() {
        return new ResilienceConfigDTO(
            300,  // 5 分钟超时
            1,    // 不重试（避免重复上传）
            5,    // 低限流
            List.of("ETag")
        );
    }
    
    // 场景2：实时查询（快速失败）
    public static ResilienceConfigDTO realtimeQuery() {
        return new ResilienceConfigDTO(
            5,    // 5 秒超时
            0,    // 不重试
            100,  // 高限流
            List.of("Content-Type")
        );
    }
    
    // 场景3：批量导入（高容错）
    public static ResilienceConfigDTO batchImport() {
        return new ResilienceConfigDTO(
            60,   // 1 分钟超时
            10,   // 最多重试 10 次
            10,   // 低限流
            List.of("X-Batch-Id")
        );
    }
}
```

### 11.3 集成新的弹性组件
```java
// 未来规划：支持更多弹性模式

// 1. 舱壁隔离（Bulkhead）
@Bulkhead(name = "pubmed", type = Bulkhead.Type.THREADPOOL)
public HttpResponse callPubMed(HttpRequest request);

// 2. 缓存（Cache）
@Cacheable(value = "external-calls", key = "#request.url")
public HttpResponse callWithCache(HttpRequest request);

// 3. 降级（Fallback）
@CircuitBreaker(name = "pubmed", fallbackMethod = "fallbackResponse")
public HttpResponse callWithFallback(HttpRequest request);
```

---

## 12. 测试策略

### 12.1 单元测试重点
- **ResilienceConfigAggregate**：配置加载、合并、校验逻辑
- **ResponseEnvelopeBuilder**：响应封装、白名单过滤、哈希计算
- **ExternalCallConverter**：DTO ↔ Domain 模型转换

### 12.2 集成测试场景
```java
@SpringBootTest(webEnvironment = RANDOM_PORT)
class ExternalCallIntegrationTest {
    
    @Test
    void should_call_external_service_successfully() {
        // Given: Mock 外部服务
        mockServer.expect(requestTo("https://api.example.com/resource"))
                  .andRespond(withSuccess("{\"status\":\"ok\"}", APPLICATION_JSON));
        
        // When: 调用南向网关
        ExternalCallResponseDTO response = egressClient.call(request);
        
        // Then: 验证响应
        assertThat(response.envelope().statusCode()).isEqualTo(200);
        assertThat(response.retryCount()).isEqualTo(0);
    }
    
    @Test
    void should_retry_on_server_error() {
        // Given: 第一次失败，第二次成功
        mockServer.expect(times(2), requestTo("https://api.example.com/resource"))
                  .andRespond(withServerError())
                  .andRespond(withSuccess());
        
        // When: 调用南向网关
        ExternalCallResponseDTO response = egressClient.call(request);
        
        // Then: 验证重试次数
        assertThat(response.retryCount()).isEqualTo(1);
    }
}
```

### 12.3 测试数据
```yaml
# test-resilience-config.yaml
resilience4j:
  ratelimiter:
    configs:
      test:
        limit-for-period: 10
        limit-refresh-period: 1s
  retry:
    configs:
      test:
        max-attempts: 2
        wait-duration: 100ms
  circuitbreaker:
    configs:
      test:
        failure-rate-threshold: 50
        sliding-window-size: 5
```

---

## 13. Roadmap 与风险

### 13.1 近期计划
| 优先级 | 事项 | 预期完成时间 |
|--------|------|-------------|
| 🔴 高 | 实现完整的 Resilience4j 集成（限流、重试、熔断） | 2025-11 |
| 🔴 高 | 添加 Micrometer 指标和 Prometheus 导出 | 2025-11 |
| 🟡 中 | 实现响应缓存（基于 contentHash） | 2025-12 |
| 🟡 中 | 支持批量调用接口 | 2025-12 |
| 🟢 低 | 实现舱壁隔离（Bulkhead） | 2026-01 |
| 🟢 低 | 添加 GraphQL 网关支持 | 2026-02 |

### 13.2 既有风险
| 风险 | 影响 | 缓解方案 |
|------|------|---------|
| 外部服务不稳定导致大量重试 | 系统资源耗尽 | 1. 设置合理的重试上限<br>2. 启用熔断器<br>3. 监控重试指标 |
| 业务方配置不当（如超时过长） | 系统整体性能下降 | 1. 强制系统最大配置<br>2. 记录配置变更日志<br>3. 定期审计配置 |
| 响应头白名单配置不当 | 敏感信息泄露 | 1. 默认白名单只包含安全响应头<br>2. 代码审查配置变更<br>3. 安全扫描 |
| HTTP 连接池耗尽 | 请求排队或失败 | 1. 监控连接池指标<br>2. 调整连接池大小<br>3. 启用连接超时 |

### 13.3 回滚策略
```bash
# 1. 配置回滚（Nacos 配置历史）
# 在 Nacos 控制台选择历史版本并回滚

# 2. 代码回滚（Git）
git revert <commit-hash>
mvn clean package -DskipTests
kubectl rollout undo deployment/patra-egress-gateway

# 3. 流量切换（金丝雀发布）
# 逐步降低新版本流量比例，直至 0%
kubectl patch service patra-egress-gateway -p '{"spec":{"selector":{"version":"v1.0.0"}}}'
```

---

## 14. 参考资料

### 14.1 内部文档
- [需求文档](.kiro/specs/patra-egress-gateway/requirements.md)
- [设计文档](.kiro/specs/patra-egress-gateway/design.md)
- [任务列表](.kiro/specs/patra-egress-gateway/tasks.md)
- [Feign API 设计指南](../../standards/feign-api-design-guide.md)
- [平台错误处理规范](../../standards/platform-error-handling.md)

### 14.2 外部参考
- [Resilience4j 官方文档](https://resilience4j.readme.io/)
- [Spring RestClient 文档](https://docs.spring.io/spring-framework/reference/integration/rest-clients.html)
- [Spring Cloud OpenFeign 文档](https://docs.spring.io/spring-cloud-openfeign/docs/current/reference/html/)
- [PubMed API 文档](https://www.ncbi.nlm.nih.gov/books/NBK25501/)

---

**更新记录**

| 版本 | 日期 | 变更说明 | 作者 |
|-----|------|---------|------|
| 1.0 | 2025-10-08 | 初始版本：完整的南向网关文档 | docs-engineer |

---

**许可证**

Copyright © 2025 Papertrace
