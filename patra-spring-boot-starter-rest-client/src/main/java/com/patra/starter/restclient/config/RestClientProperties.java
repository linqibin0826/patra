package com.patra.starter.restclient.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// REST Client 配置属性。
///
/// <p>绑定 {@code patra.rest-client} 配置前缀，支持超时、重试、拦截器和多客户端配置。
///
/// <h2>配置示例</h2>
/// <pre>{@code
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
/// }</pre>
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

  // Getters and Setters

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public TimeoutConfig getTimeout() {
    return timeout;
  }

  public void setTimeout(TimeoutConfig timeout) {
    this.timeout = timeout;
  }

  public RetryConfig getRetry() {
    return retry;
  }

  public void setRetry(RetryConfig retry) {
    this.retry = retry;
  }

  public InterceptorsConfig getInterceptors() {
    return interceptors;
  }

  public void setInterceptors(InterceptorsConfig interceptors) {
    this.interceptors = interceptors;
  }

  public Map<String, ClientConfig> getClients() {
    return clients;
  }

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

    // Getters and Setters

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public int getMaxAttempts() {
      return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
      this.maxAttempts = maxAttempts;
    }

    public long getWaitDuration() {
      return waitDuration;
    }

    public void setWaitDuration(long waitDuration) {
      this.waitDuration = waitDuration;
    }

    public double getBackoffMultiplier() {
      return backoffMultiplier;
    }

    public void setBackoffMultiplier(double backoffMultiplier) {
      this.backoffMultiplier = backoffMultiplier;
    }

    public long getMaxWaitDuration() {
      return maxWaitDuration;
    }

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

    // Getters and Setters

    public LoggingConfig getLogging() {
      return logging;
    }

    public void setLogging(LoggingConfig logging) {
      this.logging = logging;
    }

    public TracingConfig getTracing() {
      return tracing;
    }

    public void setTracing(TracingConfig tracing) {
      this.tracing = tracing;
    }

    public MetricsConfig getMetrics() {
      return metrics;
    }

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

    // Getters and Setters

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public boolean isLogHeaders() {
      return logHeaders;
    }

    public void setLogHeaders(boolean logHeaders) {
      this.logHeaders = logHeaders;
    }

    public boolean isLogBody() {
      return logBody;
    }

    public void setLogBody(boolean logBody) {
      this.logBody = logBody;
    }
  }

  /// 追踪拦截器配置。
  public static class TracingConfig {
    /// 是否启用（默认 true）
    private boolean enabled = true;

    /// 追踪 ID 的 HTTP 头名称列表
    private List<String> headerNames = List.of("X-Trace-ID", "X-B3-TraceId");

    // Getters and Setters

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public List<String> getHeaderNames() {
      return headerNames;
    }

    public void setHeaderNames(List<String> headerNames) {
      this.headerNames = headerNames;
    }
  }

  /// 指标拦截器配置。
  public static class MetricsConfig {
    /// 是否启用（默认 true）
    private boolean enabled = true;

    // Getters and Setters

    public boolean isEnabled() {
      return enabled;
    }

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

    // Getters and Setters

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public Map<String, String> getDefaultHeaders() {
      return defaultHeaders;
    }

    public void setDefaultHeaders(Map<String, String> defaultHeaders) {
      this.defaultHeaders = defaultHeaders;
    }

    public TimeoutConfig getTimeout() {
      return timeout;
    }

    public void setTimeout(TimeoutConfig timeout) {
      this.timeout = timeout;
    }
  }
}
