package com.patra.starter.observability.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.patra.starter.observability.config.ObservabilityProperties;
import com.patra.starter.observability.interceptor.ObservationResolutionInterceptor;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

/**
 * ObservabilityAutoConfiguration 单元测试。
 *
 * <p>验证自动配置类正确创建并注册所有必要的 Bean。
 *
 * @author Jobs
 * @since 1.0.0
 */
@SpringBootTest(classes = ObservabilityAutoConfigurationTest.TestConfiguration.class)
@TestPropertySource(
    properties = {
      "patra.observability.enabled=true",
      "patra.observability.application-name=test-app",
      "patra.observability.environment=test",
      "patra.observability.region=cn-test",
      "patra.observability.cluster=test-cluster",
      "management.observations.annotations.enabled=false" // 禁用 @Observed 注解支持（测试环境无 AspectJ）
    })
class ObservabilityAutoConfigurationTest {

  @Autowired private ApplicationContext context;

  @Autowired(required = false)
  private ObservationRegistry observationRegistry;

  @Autowired(required = false)
  private MeterRegistry meterRegistry;

  @Autowired(required = false)
  private ObservabilityProperties properties;

  /** 测试 ObservationRegistry Bean 创建。 */
  @Test
  void shouldCreateObservationRegistry() {
    assertThat(observationRegistry).isNotNull();
  }

  /** 测试 MeterRegistry Bean 创建。 */
  @Test
  void shouldCreateMeterRegistry() {
    assertThat(meterRegistry).isNotNull();
  }

  /** 测试 ObservabilityProperties Bean 创建和配置绑定。 */
  @Test
  void shouldLoadObservabilityProperties() {
    assertThat(properties).isNotNull();
    assertThat(properties.getApplicationName()).isEqualTo("test-app");
    assertThat(properties.getEnvironment()).isEqualTo("test");
    assertThat(properties.getRegion()).isEqualTo("cn-test");
    assertThat(properties.getCluster()).isEqualTo("test-cluster");
  }

  /** 测试拦截器注册。 */
  @Test
  void shouldRegisterInterceptors() {
    // 验证 ObservationResolutionInterceptor 已注册
    assertThat(context.getBeanNamesForType(ObservationResolutionInterceptor.class)).isNotEmpty();

    // 注意：HTTP 客户端观测由 Spring Boot 3.x 内置的 RestClient.Builder 自动配置处理
    // Batch 可观测性已迁移至 patra-spring-boot-starter-batch
  }

  /**
   * 测试配置类。
   *
   * <p>启用自动配置，让 Spring Boot 自动加载所有 AutoConfiguration。 由于测试环境没有 Spring Boot Actuator，需要手动创建
   * SimpleMeterRegistry。
   */
  @Configuration
  @EnableAutoConfiguration(
      exclude = {
        DataSourceAutoConfiguration.class,
        DataRedisAutoConfiguration.class,
        DataRedisRepositoriesAutoConfiguration.class
      })
  static class TestConfiguration {
    /**
     * 创建 SimpleMeterRegistry 用于测试。
     *
     * <p>注意：在没有 Actuator 的环境中，Spring Boot 不会自动创建 MeterRegistry。 使用 @Primary 标记为主要 Bean，避免与其他
     * MeterRegistry 实现冲突。
     */
    @org.springframework.context.annotation.Primary
    @Bean
    @ConditionalOnMissingBean
    public MeterRegistry meterRegistry() {
      return new SimpleMeterRegistry();
    }
  }
}
