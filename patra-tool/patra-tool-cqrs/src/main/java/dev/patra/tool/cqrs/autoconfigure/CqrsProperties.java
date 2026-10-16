package dev.patra.tool.cqrs.autoconfigure;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// 命令总线配置，前缀 `patra.cqrs`。
///
/// @param enabled 总开关，关掉后本模块不注册任何 bean
/// @param async 异步执行器
/// @param interceptors 三个内置拦截器的开关
@Validated
@ConfigurationProperties(prefix = "patra.cqrs")
public record CqrsProperties(
    @DefaultValue("true") boolean enabled,
    @DefaultValue Async async,
    @DefaultValue Interceptors interceptors) {

  /// 异步执行器的线程池参数。
  ///
  /// @param corePoolSize 核心线程数
  /// @param maxPoolSize 最大线程数
  /// @param queueCapacity 队列容量
  /// @param threadNamePrefix 线程名前缀
  public record Async(
      @DefaultValue("4") @Min(1) int corePoolSize,
      @DefaultValue("16") @Min(1) int maxPoolSize,
      @DefaultValue("100") @Min(0) int queueCapacity,
      @DefaultValue("cmd-bus-") String threadNamePrefix) {}

  /// 内置拦截器开关。
  ///
  /// @param logging 日志拦截器
  /// @param tracing 追踪拦截器（还要类路径上有 Observation 且容器里有 `ObservationRegistry`）
  /// @param metrics 指标拦截器（还要容器里有 `MeterRegistry`）
  public record Interceptors(
      @DefaultValue("true") boolean logging,
      @DefaultValue("true") boolean tracing,
      @DefaultValue("true") boolean metrics) {}
}
