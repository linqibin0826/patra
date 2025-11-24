package com.patra.starter.restclient.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// REST Client 配置属性。
///
/// 绑定 {@code patra.rest-client} 配置前缀，支持超时、重试、拦截器和多客户端配置。
///
/// ## 配置示例
///
/// ```yaml
/// patra:
///   rest-client:
///     enabled: true
///     timeout:
///       connect: 10s
///       read: 30s
///     retry:
///       enabled: true
///       max-attempts: 3
///     interceptors:
///       logging:
///         enabled: true
///     clients:
///       pubmed:
///         base-url: "https://eutils.ncbi.nlm.nih.gov/entrez/eutils"
/// ```
///
/// @author linqibin
/// @since 0.1.0
@ConfigurationProperties(prefix = "patra.rest-client")
public class RestClientProperties {

  /// 是否启用自动配置（默认 true）
  private boolean enabled = true;

  /// 全局超时配置
  private TimeoutConfig timeout = new TimeoutConfig();

  /// 重试配置
  private RetryConfig retry = new RetryConfig();

  /// 拦截器配置
  private InterceptorsConfig interceptors = new InterceptorsConfig();

  /// 客户端配置映射（按用途分组）
  private Map<String, ClientConfig> clients = new HashMap<>();

  /// 获取是否启用自动配置。
  ///
  /// @return 是否启用
  public boolean isEnabled() {
    return enabled;
  }

  /// 设置是否启用自动配置。
  ///
  /// @param enabled 是否启用
  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  /// 获取全局超时配置。
  ///
  /// @return 超时配置
  public TimeoutConfig getTimeout() {
    return timeout;
  }

  /// 设置全局超时配置。
  ///
  /// @param timeout 超时配置
  public void setTimeout(TimeoutConfig timeout) {
    this.timeout = timeout;
  }

  /// 获取重试配置。
  ///
  /// @return 重试配置
  public RetryConfig getRetry() {
    return retry;
  }

  /// 设置重试配置。
  ///
  /// @param retry 重试配置
  public void setRetry(RetryConfig retry) {
    this.retry = retry;
  }

  /// 获取拦截器配置。
  ///
  /// @return 拦截器配置
  public InterceptorsConfig getInterceptors() {
    return interceptors;
  }

  /// 设置拦截器配置。
  ///
  /// @param interceptors 拦截器配置
  public void setInterceptors(InterceptorsConfig interceptors) {
    this.interceptors = interceptors;
  }

  /// 获取客户端配置映射。
  ///
  /// @return 客户端配置映射
  public Map<String, ClientConfig> getClients() {
    return clients;
  }

  /// 设置客户端配置映射。
  ///
  /// @param clients 客户端配置映射
  public void setClients(Map<String, ClientConfig> clients) {
    this.clients = clients;
  }

  /// 超时配置。
  ///
  /// @param connect 连接超时时间（默认 10 秒）
  /// @param read 读取超时时间（默认 30 秒）
  /// @param write 写入超时时间（默认 30 秒）
  public record TimeoutConfig(Duration connect, Duration read, Duration write) {
    public TimeoutConfig() {
      this(Duration.ofSeconds(10), Duration.ofSeconds(30), Duration.ofSeconds(30));
    }
  }

  /// 重试配置。
  public static class RetryConfig {
    /// 是否启用重试（默认 false，避免过于激进）
    private boolean enabled = false;

    /// 最大重试次数（默认 3）
    private int maxAttempts = 3;

    /// 初始重试等待时间（默认 1000 毫秒）
    private long waitDuration = 1000;

    /// 退避倍数（默认 2.0）
    private double backoffMultiplier = 2.0;

    /// 最大等待时间（默认 30000 毫秒）
    private long maxWaitDuration = 30000;

    /// 获取是否启用重试。
    ///
    /// @return 是否启用重试
    public boolean isEnabled() {
      return enabled;
    }

    /// 设置是否启用重试。
    ///
    /// @param enabled 是否启用重试
    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    /// 获取最大重试次数。
    ///
    /// @return 最大重试次数
    public int getMaxAttempts() {
      return maxAttempts;
    }

    /// 设置最大重试次数。
    ///
    /// @param maxAttempts 最大重试次数
    public void setMaxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts;
    }

    /// 获取初始重试等待时间（毫秒）。
    ///
    /// @return 初始等待时间
    public long getWaitDuration() {
      return waitDuration;
    }

    /// 设置初始重试等待时间（毫秒）。
    ///
    /// @param waitDuration 初始等待时间
    public void setWaitDuration(long waitDuration) {
      this.waitDuration = waitDuration;
    }

    /// 获取退避倍数。
    ///
    /// @return 退避倍数
    public double getBackoffMultiplier() {
      return backoffMultiplier;
    }

    /// 设置退避倍数。
    ///
    /// @param backoffMultiplier 退避倍数
    public void setBackoffMultiplier(double backoffMultiplier) {
      this.backoffMultiplier = backoffMultiplier;
    }

    /// 获取最大等待时间（毫秒）。
    ///
    /// @return 最大等待时间
    public long getMaxWaitDuration() {
      return maxWaitDuration;
    }

    /// 设置最大等待时间（毫秒）。
    ///
    /// @param maxWaitDuration 最大等待时间
    public void setMaxWaitDuration(long maxWaitDuration) {
      this.maxWaitDuration = maxWaitDuration;
    }
  }

  /// 拦截器配置。
  public static class InterceptorsConfig {
    /// 日志拦截器配置
    private LoggingConfig logging = new LoggingConfig();

    /// 追踪拦截器配置
    private TracingConfig tracing = new TracingConfig();

    /// 指标拦截器配置
    private MetricsConfig metrics = new MetricsConfig();

    /// 获取日志拦截器配置。
    ///
    /// @return 日志拦截器配置
    public LoggingConfig getLogging() {
      return logging;
    }

    /// 设置日志拦截器配置。
    ///
    /// @param logging 日志拦截器配置
    public void setLogging(LoggingConfig logging) {
      this.logging = logging;
    }

    /// 获取追踪拦截器配置。
    ///
    /// @return 追踪拦截器配置
    public TracingConfig getTracing() {
      return tracing;
    }

    /// 设置追踪拦截器配置。
    ///
    /// @param tracing 追踪拦截器配置
    public void setTracing(TracingConfig tracing) {
      this.tracing = tracing;
    }

    /// 获取指标拦截器配置。
    ///
    /// @return 指标拦截器配置
    public MetricsConfig getMetrics() {
      return metrics;
    }

    /// 设置指标拦截器配置。
    ///
    /// @param metrics 指标拦截器配置
    public void setMetrics(MetricsConfig metrics) {
      this.metrics = metrics;
    }
  }

  /// 日志拦截器配置。
  public static class LoggingConfig {
    /// 是否启用（默认 true）
    private boolean enabled = true;

    /// 是否记录 HTTP 头（默认 false，避免敏感信息泄露）
    private boolean logHeaders = false;

    /// 是否记录请求/响应体（默认 false）
    private boolean logBody = false;

    /// 记录 Body 的最大字节数（默认 1024 字节，防止大文件导致内存溢出）
    private int maxBodyLogLength = 1024;

    /// 获取是否启用日志拦截器。
    ///
    /// @return 是否启用
    public boolean isEnabled() {
      return enabled;
    }

    /// 设置是否启用日志拦截器。
    ///
    /// @param enabled 是否启用
    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    /// 获取是否记录 HTTP 头。
    ///
    /// @return 是否记录 HTTP 头
    public boolean isLogHeaders() {
      return logHeaders;
    }

    /// 设置是否记录 HTTP 头。
    ///
    /// @param logHeaders 是否记录 HTTP 头
    public void setLogHeaders(boolean logHeaders) {
      this.logHeaders = logHeaders;
    }

    /// 获取是否记录请求/响应体。
    ///
    /// @return 是否记录请求/响应体
    public boolean isLogBody() {
      return logBody;
    }

    /// 设置是否记录请求/响应体。
    ///
    /// @param logBody 是否记录请求/响应体
    public void setLogBody(boolean logBody) {
      this.logBody = logBody;
    }

    /// 获取 Body 日志的最大字节数。
    ///
    /// @return 最大字节数
    public int getMaxBodyLogLength() {
      return maxBodyLogLength;
    }

    /// 设置 Body 日志的最大字节数。
    ///
    /// @param maxBodyLogLength 最大字节数
    public void setMaxBodyLogLength(int maxBodyLogLength) {
      this.maxBodyLogLength = maxBodyLogLength;
    }
  }

  /// 追踪拦截器配置。
  public static class TracingConfig {
    /// 是否启用（默认 true）
    private boolean enabled = true;

    /// 追踪 ID 的 HTTP 头名称列表
    private List<String> headerNames = List.of("X-Trace-ID", "X-B3-TraceId");

    /// 获取是否启用追踪拦截器。
    ///
    /// @return 是否启用
    public boolean isEnabled() {
      return enabled;
    }

    /// 设置是否启用追踪拦截器。
    ///
    /// @param enabled 是否启用
    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    /// 获取追踪 ID 的 HTTP 头名称列表。
    ///
    /// @return HTTP 头名称列表
    public List<String> getHeaderNames() {
      return headerNames;
    }

    /// 设置追踪 ID 的 HTTP 头名称列表。
    ///
    /// @param headerNames HTTP 头名称列表
    public void setHeaderNames(List<String> headerNames) {
      this.headerNames = headerNames;
    }
  }

  /// 指标拦截器配置。
  public static class MetricsConfig {
    /// 是否启用（默认 true）
    private boolean enabled = true;

    /// 获取是否启用指标拦截器。
    ///
    /// @return 是否启用
    public boolean isEnabled() {
      return enabled;
    }

    /// 设置是否启用指标拦截器。
    ///
    /// @param enabled 是否启用
    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }
  }

  /// 客户端配置。
  public static class ClientConfig {
    /// 基础 URL（可选）
    private String baseUrl;

    /// 默认 HTTP 头
    private Map<String, String> defaultHeaders = new HashMap<>();

    /// 客户端级超时配置（覆盖全局配置）
    private TimeoutConfig timeout;

    /// 获取基础 URL。
    ///
    /// @return 基础 URL
    public String getBaseUrl() {
      return baseUrl;
    }

    /// 设置基础 URL。
    ///
    /// @param baseUrl 基础 URL
    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    /// 获取默认 HTTP 头映射。
    ///
    /// @return HTTP 头映射
    public Map<String, String> getDefaultHeaders() {
      return defaultHeaders;
    }

    /// 设置默认 HTTP 头映射。
    ///
    /// @param defaultHeaders HTTP 头映射
    public void setDefaultHeaders(Map<String, String> defaultHeaders) {
      this.defaultHeaders = defaultHeaders;
    }

    /// 获取客户端级超时配置。
    ///
    /// @return 超时配置
    public TimeoutConfig getTimeout() {
      return timeout;
    }

    /// 设置客户端级超时配置。
    ///
    /// @param timeout 超时配置
    public void setTimeout(TimeoutConfig timeout) {
      this.timeout = timeout;
    }
  }
}
