package com.patra.starter.core.async;

import lombok.Getter;
import lombok.Setter;

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
@Getter
@Setter
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
}
