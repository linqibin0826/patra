package com.patra.starter.batch.autoconfigure;

import com.patra.starter.batch.listener.LoggingJobListener;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/// Spring Batch 可观测性自动配置。
///
/// 自动配置 Spring Batch Job 监听器，提供基础日志记录能力。
///
/// - 日志记录：`LoggingJobListener`（基础设施层调试工具）
///
/// 注意: 指标收集和追踪集成功能已移至 patra-spring-boot-starter-observability，
/// 通过扩展点机制提供，符合关注点分离原则。
///
/// 通过配置属性控制各监听器的启用/禁用。
///
/// @author Patra Team
/// @since 1.0.0
@AutoConfiguration
public class ObservabilityAutoConfiguration {

  /// 配置日志监听器（基础设施层调试工具）。
  ///
  /// 记录 Job 启动、完成、失败等关键事件。
  ///
  /// 注意：日志监听器不属于可观测性范畴，而是基础设施层的调试工具，因此保留在本模块中。
  /// 指标收集和追踪集成功能由 patra-spring-boot-starter-observability 提供。
  ///
  /// 条件激活：`patra.batch.observability.logging.enabled=true`（默认启用）
  ///
  /// @return LoggingJobListener 实例
  @Bean
  @ConditionalOnProperty(
      prefix = "patra.batch.observability.logging",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public LoggingJobListener loggingJobListener() {
    return new LoggingJobListener();
  }
}
