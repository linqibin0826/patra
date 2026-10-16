package dev.patra.tool.batch.autoconfigure;

import dev.patra.tool.batch.metrics.BatchProgressMetricsListener;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.configuration.JobRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/// Spring Batch 进度指标自动配置。
///
/// 当以下条件满足时自动创建 `BatchProgressMetricsListener`：
///
/// - 存在 `MeterRegistry` Bean（Micrometer 指标注册表）
/// - 配置属性 `patra.batch.metrics.enabled` 为 `true`（默认启用）
///
/// **使用方式**：
///
/// 1. 引入 `patra-spring-boot-starter-batch` 依赖
/// 2. 确保存在 `MeterRegistry` Bean（通常由 `spring-boot-starter-actuator` 提供）
/// 3. 在 Job/Step 定义中注册 Listener：
///
/// ```java
/// @Bean
/// public Step myStep(BatchProgressMetricsListener metricsListener) {
///     return stepBuilder.get("myStep")
///         .<Input, Output>chunk(100)
///         .listener(metricsListener)  // 注册指标监听器
///         .reader(reader())
///         .writer(writer())
///         .build();
/// }
/// ```
///
/// **禁用指标**：
///
/// ```yaml
/// patra:
///   batch:
///     metrics:
///       enabled: false
/// ```
///
/// @author linqibin
/// @since 0.1.0
/// @see BatchProgressMetricsListener
@Slf4j
@AutoConfiguration(
    after = BatchAutoConfiguration.class,
    afterName = {
      // MeterRegistry bean 由 CompositeMeterRegistryAutoConfiguration 建，排在它之后 @ConditionalOnBean
      // 才判得准
      "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration",
      "dev.patra.tool.observability.autoconfigure.ObservabilityMicrometerAutoConfiguration"
    })
@ConditionalOnClass({MeterRegistry.class, JobRegistry.class})
@ConditionalOnBean(MeterRegistry.class)
@ConditionalOnProperty(
    prefix = "patra.batch",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@ConditionalOnProperty(
    prefix = "patra.batch.metrics",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public final class BatchMetricsAutoConfiguration {

  /// 创建进度指标监听器。
  ///
  /// @param meterRegistry Micrometer 指标注册表
  /// @return BatchProgressMetricsListener 实例
  @Bean
  public BatchProgressMetricsListener batchProgressMetricsListener(MeterRegistry meterRegistry) {
    log.info("创建 BatchProgressMetricsListener，进度指标已启用");
    return new BatchProgressMetricsListener(meterRegistry);
  }
}
