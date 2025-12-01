package com.patra.starter.core.async;

/// 单个线程池的配置属性。
///
/// 用于配置命名线程池的核心参数，支持通过 YAML 配置：
///
/// ```yaml
/// patra:
///   async:
///     pools:
///       cache-upload:
///         core-size: 2
///         max-size: 4
///         queue-capacity: 100
///         keep-alive-seconds: 60
/// ```
///
/// @author linqibin
/// @since 0.1.0
public class AsyncPoolProperties {

  /// 核心线程数（默认 2）。
  private int coreSize = 2;

  /// 最大线程数（默认 4）。
  private int maxSize = 4;

  /// 队列容量（默认 100）。
  private int queueCapacity = 100;

  /// 空闲线程存活时间（秒，默认 60）。
  private int keepAliveSeconds = 60;

  /// 线程名前缀（如果为空，使用 "async-{poolName}-"）。
  private String threadNamePrefix;

  /// 获取核心线程数。
  ///
  /// @return 核心线程数
  public int getCoreSize() {
    return coreSize;
  }

  /// 设置核心线程数。
  ///
  /// @param coreSize 核心线程数
  public void setCoreSize(int coreSize) {
    this.coreSize = coreSize;
  }

  /// 获取最大线程数。
  ///
  /// @return 最大线程数
  public int getMaxSize() {
    return maxSize;
  }

  /// 设置最大线程数。
  ///
  /// @param maxSize 最大线程数
  public void setMaxSize(int maxSize) {
    this.maxSize = maxSize;
  }

  /// 获取队列容量。
  ///
  /// @return 队列容量
  public int getQueueCapacity() {
    return queueCapacity;
  }

  /// 设置队列容量。
  ///
  /// @param queueCapacity 队列容量
  public void setQueueCapacity(int queueCapacity) {
    this.queueCapacity = queueCapacity;
  }

  /// 获取空闲线程存活时间（秒）。
  ///
  /// @return 空闲线程存活时间
  public int getKeepAliveSeconds() {
    return keepAliveSeconds;
  }

  /// 设置空闲线程存活时间（秒）。
  ///
  /// @param keepAliveSeconds 空闲线程存活时间
  public void setKeepAliveSeconds(int keepAliveSeconds) {
    this.keepAliveSeconds = keepAliveSeconds;
  }

  /// 获取线程名前缀。
  ///
  /// @return 线程名前缀
  public String getThreadNamePrefix() {
    return threadNamePrefix;
  }

  /// 设置线程名前缀。
  ///
  /// @param threadNamePrefix 线程名前缀
  public void setThreadNamePrefix(String threadNamePrefix) {
    this.threadNamePrefix = threadNamePrefix;
  }
}
