package com.patra.starter.core.cqrs;

import org.springframework.boot.context.properties.ConfigurationProperties;

/// CommandBus 配置属性。
///
/// ## 配置示例
///
/// ```yaml
/// patra:
///   command-bus:
///     async:
///       core-pool-size: 4
///       max-pool-size: 16
///       queue-capacity: 100
///     interceptors:
///       logging: true
///       metrics: true
///       tracing: true
/// ```
@ConfigurationProperties(prefix = "patra.command-bus")
public class CommandBusProperties {

  /// 异步执行配置。
  private Async async = new Async();

  /// 拦截器开关配置。
  private Interceptors interceptors = new Interceptors();

  public Async getAsync() {
    return async;
  }

  public void setAsync(Async async) {
    this.async = async;
  }

  public Interceptors getInterceptors() {
    return interceptors;
  }

  public void setInterceptors(Interceptors interceptors) {
    this.interceptors = interceptors;
  }

  /// 异步线程池配置。
  public static class Async {

    /// 核心线程数，默认 4。
    private int corePoolSize = 4;

    /// 最大线程数，默认 16。
    private int maxPoolSize = 16;

    /// 队列容量，默认 100。
    private int queueCapacity = 100;

    /// 线程名前缀，默认 "cmd-bus-"。
    private String threadNamePrefix = "cmd-bus-";

    public int getCorePoolSize() {
      return corePoolSize;
    }

    public void setCorePoolSize(int corePoolSize) {
      this.corePoolSize = corePoolSize;
    }

    public int getMaxPoolSize() {
      return maxPoolSize;
    }

    public void setMaxPoolSize(int maxPoolSize) {
      this.maxPoolSize = maxPoolSize;
    }

    public int getQueueCapacity() {
      return queueCapacity;
    }

    public void setQueueCapacity(int queueCapacity) {
      this.queueCapacity = queueCapacity;
    }

    public String getThreadNamePrefix() {
      return threadNamePrefix;
    }

    public void setThreadNamePrefix(String threadNamePrefix) {
      this.threadNamePrefix = threadNamePrefix;
    }
  }

  /// 内置拦截器开关配置。
  public static class Interceptors {

    /// 是否启用日志拦截器，默认 true。
    private boolean logging = true;

    /// 是否启用指标拦截器，默认 true。
    private boolean metrics = true;

    /// 是否启用追踪拦截器，默认 true。
    private boolean tracing = true;

    public boolean isLogging() {
      return logging;
    }

    public void setLogging(boolean logging) {
      this.logging = logging;
    }

    public boolean isMetrics() {
      return metrics;
    }

    public void setMetrics(boolean metrics) {
      this.metrics = metrics;
    }

    public boolean isTracing() {
      return tracing;
    }

    public void setTracing(boolean tracing) {
      this.tracing = tracing;
    }
  }
}
