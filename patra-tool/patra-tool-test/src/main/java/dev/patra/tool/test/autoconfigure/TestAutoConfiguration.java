package dev.patra.tool.test.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/// 测试支持自动配置：测试上下文里缺什么基础设施 bean 就补什么。
@AutoConfiguration
@ConditionalOnProperty(
    prefix = "patra.test",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@EnableConfigurationProperties(TestProperties.class)
public final class TestAutoConfiguration {

  /// 类路径上有 Micrometer 时给一个内存 `MeterRegistry`，让带指标的 bean 能装配。
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(MeterRegistry.class)
  static class TestMeterRegistryConfiguration {

    /// 内存指标注册表。
    ///
    /// @return `SimpleMeterRegistry`
    @Bean
    @Primary
    @ConditionalOnMissingBean(MeterRegistry.class)
    MeterRegistry testMeterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}
