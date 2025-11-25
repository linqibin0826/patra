package com.patra.starter.test.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/// 测试环境 MeterRegistry 自动配置。
///
/// 在没有 Spring Boot Actuator 的测试环境中，自动提供 SimpleMeterRegistry。
/// 避免各测试类重复配置 MeterRegistry Bean。
///
/// ### 功能特性
///
/// - **自动配置条件**: 仅当 `MeterRegistry` 类存在且容器中没有 `MeterRegistry` Bean 时生效
/// - **@Primary 标记**: 确保在存在多个 Registry 时优先使用此 Bean
/// - **测试友好**: SimpleMeterRegistry 是内存实现，适合单元测试和集成测试
///
/// ### 使用场景
///
/// 当测试代码依赖 `MeterRegistry`（如测试可观测性相关功能）但不想引入 Actuator 时：
///
/// ```java
/// @SpringBootTest
/// class ObservabilityTest {
///     @Autowired
///     private MeterRegistry registry; // 自动注入 SimpleMeterRegistry
///
///     @Test
///     void shouldRecordMetrics() {
///         // registry 已经可用，无需手动配置
///     }
/// }
/// ```
///
/// @author linqibin
/// @since 0.1.0
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
public class TestMeterRegistryAutoConfiguration {

  /// 提供测试用的 SimpleMeterRegistry。
  ///
  /// @return SimpleMeterRegistry 实例
  @Bean
  @Primary
  @ConditionalOnMissingBean(MeterRegistry.class)
  public MeterRegistry testMeterRegistry() {
    return new SimpleMeterRegistry();
  }
}
