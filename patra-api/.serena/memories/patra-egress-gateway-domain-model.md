# patra-egress-gateway 领域模型

## 核心值对象

### ResilienceConfig（弹性配置）
```java
public record ResilienceConfig(
    Duration timeout,                   // 超时时间
    int maxRetries,                     // 最大重试次数
    Duration retryBackoff,              // 重试退避时间
    int circuitBreakerThreshold,        // 熔断阈值（失败次数）
    Duration circuitBreakerWindow,      // 熔断时间窗口
    List<String> responseHeaderWhitelist // 响应头白名单
) {
    void validate();                    // 校验配置有效性
    ResilienceConfig mergeWithMax(ResilienceConfig max); // 合并配置（不超过最大值）
}
```

### HttpRequest / HttpResponse
```java
public record HttpRequest(String url, HttpMethod method, Map<String, String> headers, String body) {}
public record HttpResponse(int statusCode, Map<String, List<String>> headers, String body) {
    boolean isSuccess(); // 2xx = 成功
}
```

### ResponseEnvelope（响应封装）
```java
public record ResponseEnvelope(
    boolean success,                    // 成功/失败标识
    int statusCode,                     // HTTP 状态码
    Map<String, String> headers,        // 白名单过滤后的响应头
    String body,                        // 原始响应 Body
    String bodyHash,                    // 响应 Body 的哈希值（SHA-256）
    RateLimitStatus rateLimitStatus,    // 限流状态
    RetryAdvice retryAdvice,            // 重试建议
    String snapshotMode                 // 快照模式（META_PLUS_BODY）
) {}
```

### RateLimitStatus（限流状态）
```java
public record RateLimitStatus(
    int limit,                          // 限流上限
    int remaining,                      // 剩余配额
    Duration resetAfter,                // 重置时间
    ExternalRateLimitInfo externalInfo  // 外部服务返回的限流信息
) {}
```

### RetryAdvice（重试建议）
```java
public record RetryAdvice(
    boolean retryable,                  // 是否可重试
    Duration suggestedDelay,            // 建议延迟时间
    String reason                       // 重试建议原因
) {
    static RetryAdvice fromResponse(HttpResponse response, ResilienceConfig config);
}
```

## 聚合根

### ResilienceConfigAggregate
```java
public class ResilienceConfigAggregate {
    static ResilienceConfigAggregate loadSystemConfig(ConfigPort configPort);
    ResilienceConfig mergeWithCallerConfig(ResilienceConfig callerConfig);
    void validate();
}
```

## 领域端口

### ConfigPort（配置端口）
```java
public interface ConfigPort {
    ResilienceConfig loadSystemDefaultConfig();
    ResilienceConfig loadSystemMaxConfig();
}
```

### HttpClientPort（HTTP 客户端端口）
```java
public interface HttpClientPort {
    HttpResponse call(HttpRequest request, ResilienceConfig config);
}
```

### RateLimiterPort（限流端口）
```java
public interface RateLimiterPort {
    boolean tryAcquire(String key, int rateLimit);
    RateLimitStatus getStatus(String key);
}
```

### CircuitBreakerPort（熔断端口）
```java
public interface CircuitBreakerPort {
    <T> T executeWithCircuitBreaker(String key, Supplier<T> supplier, ResilienceConfig config);
    CircuitBreakerState getState(String key);
}
```

## Command/Result 对象

### ExternalCallCommand
```java
public record ExternalCallCommand(
    HttpRequest request,                // HTTP 请求
    ResilienceConfig callerConfig       // 业务方传递的配置（可选）
) {}
```

### ExternalCallResult
```java
public record ExternalCallResult(
    ResponseEnvelope envelope,          // 响应封装
    Duration duration,                  // 调用耗时
    int retryCount,                     // 实际重试次数
    String traceId                      // 追踪 ID
) {}
```
