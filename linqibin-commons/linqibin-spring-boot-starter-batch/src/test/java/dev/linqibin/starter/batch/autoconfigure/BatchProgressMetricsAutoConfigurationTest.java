package dev.linqibin.starter.batch.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.starter.batch.metrics.BatchProgressMetricsListener;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// BatchProgressMetricsAutoConfiguration 自动配置测试。
///
/// 验证：
///
/// - 有 MeterRegistry Bean 时应该创建 BatchProgressMetricsListener
/// - 没有 MeterRegistry Bean 时不应该创建 BatchProgressMetricsListener
/// - 可以通过配置属性禁用
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("BatchProgressMetricsAutoConfiguration 自动配置测试")
@Timeout(value = 2, unit = TimeUnit.SECONDS)
class BatchProgressMetricsAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(BatchProgressMetricsAutoConfiguration.class));

  @Test
  @DisplayName("有 MeterRegistry 时应该创建 BatchProgressMetricsListener")
  void withMeterRegistry_shouldCreateListener() {
    contextRunner
        .withUserConfiguration(MeterRegistryConfiguration.class)
        .run(
            context -> {
              assertThat(context).hasSingleBean(BatchProgressMetricsListener.class);
            });
  }

  @Test
  @DisplayName("没有 MeterRegistry 时不应该创建 BatchProgressMetricsListener")
  void withoutMeterRegistry_shouldNotCreateListener() {
    contextRunner.run(
        context -> {
          assertThat(context).doesNotHaveBean(BatchProgressMetricsListener.class);
        });
  }

  @Test
  @DisplayName("禁用配置时不应该创建 BatchProgressMetricsListener")
  void withDisabledConfig_shouldNotCreateListener() {
    contextRunner
        .withUserConfiguration(MeterRegistryConfiguration.class)
        .withPropertyValues("linqibin.starter.batch.metrics.enabled=false")
        .run(
            context -> {
              assertThat(context).doesNotHaveBean(BatchProgressMetricsListener.class);
            });
  }

  @Configuration
  static class MeterRegistryConfiguration {
    @Bean
    MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}
