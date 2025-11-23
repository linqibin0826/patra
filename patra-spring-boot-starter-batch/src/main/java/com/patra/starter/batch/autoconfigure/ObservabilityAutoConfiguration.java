package com.patra.starter.batch.autoconfigure;

import com.patra.starter.batch.listener.LoggingJobListener;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Spring Batch 可观测性自动配置
 *
 * <p>自动配置 Spring Batch Job 监听器，提供可观测性能力：
 *
 * <ul>
 *   <li>日志记录：{@link LoggingJobListener}
 *   <li>指标收集：MetricsJobListener（未来实现）
 *   <li>追踪集成：SkyWalkingJobListener（未来实现）
 * </ul>
 *
 * <p>通过配置属性控制各监听器的启用/禁用
 *
 * @author Patra Team
 * @since 1.0.0
 */
@AutoConfiguration
public class ObservabilityAutoConfiguration {

  /**
   * 配置日志监听器
   *
   * <p>记录 Job 启动、完成、失败等关键事件
   *
   * <p>条件激活：{@code patra.batch.observability.logging.enabled=true}（默认启用）
   *
   * @return LoggingJobListener 实例
   */
  @Bean
  @ConditionalOnProperty(
      prefix = "patra.batch.observability.logging",
      name = "enabled",
      havingValue = "true",
      matchIfMissing = true)
  public LoggingJobListener loggingJobListener() {
    return new LoggingJobListener();
  }

  // TODO: 未来实现 MetricsJobListener
  // @Bean
  // @ConditionalOnBean(MeterRegistry.class)
  // @ConditionalOnProperty(
  //     prefix = "patra.batch.observability.metrics",
  //     name = "enabled",
  //     havingValue = "true",
  //     matchIfMissing = true)
  // public MetricsJobListener metricsJobListener(MeterRegistry meterRegistry) {
  //   return new MetricsJobListener(meterRegistry);
  // }

  // TODO: 未来实现 SkyWalkingJobListener
  // @Bean
  // @ConditionalOnProperty(
  //     prefix = "patra.batch.observability.tracing",
  //     name = "enabled",
  //     havingValue = "true",
  //     matchIfMissing = true)
  // public SkyWalkingJobListener skyWalkingJobListener() {
  //   return new SkyWalkingJobListener();
  // }
}
