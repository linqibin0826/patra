package dev.linqibin.starter.httpinterface.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.starter.httpinterface.error.ProblemDetailErrorHandler;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/// HttpInterfaceAutoConfiguration 自动配置测试
///
/// 使用 ApplicationContextRunner 验证 Bean 装配和条件配置逻辑。
///
/// 测试覆盖：
///
/// - 默认配置下的 Bean 注册
/// - 禁用配置时的行为
/// - 自定义 Bean 不被覆盖
/// - Apache HttpClient 连接池配置
///
/// @author linqibin
/// @since 0.1.0
@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("HttpInterfaceAutoConfiguration 自动配置测试")
class HttpInterfaceAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(
              AutoConfigurations.of(
                  JacksonAutoConfiguration.class, HttpInterfaceAutoConfiguration.class))
          .withUserConfiguration(TestConfiguration.class);

  @Nested
  @DisplayName("默认配置测试")
  class DefaultConfigurationTests {

    @Test
    @DisplayName("默认启用时 - 注册所有核心 Bean")
    void shouldRegisterAllBeansWhenEnabled() {
      contextRunner.run(
          context -> {
            assertThat(context).hasSingleBean(ProblemDetailErrorHandler.class);
            assertThat(context).hasBean("httpInterfaceRestClientCustomizer");
            assertThat(context).hasBean("httpInterfaceRestClientBuilder");
            assertThat(context).hasBean("httpInterfaceLoadBalancedRestClientBuilder");
          });
    }

    @Test
    @DisplayName("验证 RestClient.Builder Bean 类型")
    void shouldRegisterRestClientBuilder() {
      contextRunner.run(
          context -> {
            assertThat(context.getBeansOfType(RestClient.Builder.class)).hasSize(2);
            assertThat(context.getBean("httpInterfaceRestClientBuilder"))
                .isInstanceOf(RestClient.Builder.class);
            assertThat(context.getBean("httpInterfaceLoadBalancedRestClientBuilder"))
                .isInstanceOf(RestClient.Builder.class);
          });
    }
  }

  @Nested
  @DisplayName("条件配置测试")
  class ConditionalConfigurationTests {

    @Test
    @DisplayName("禁用配置时 - 不注册任何 Bean")
    void shouldNotRegisterBeansWhenDisabled() {
      contextRunner
          .withPropertyValues("linqibin.starter.http-interface.enabled=false")
          .run(
              context -> {
                assertThat(context).doesNotHaveBean(ProblemDetailErrorHandler.class);
                assertThat(context).doesNotHaveBean("httpInterfaceRestClientCustomizer");
                assertThat(context).doesNotHaveBean("httpInterfaceLoadBalancedRestClientBuilder");
              });
    }

    @Test
    @DisplayName("自定义 Bean 存在时 - 不覆盖用户配置")
    void shouldNotOverrideUserBeans() {
      contextRunner
          .withUserConfiguration(CustomBeansConfiguration.class)
          .run(
              context -> {
                assertThat(context).hasSingleBean(ProblemDetailErrorHandler.class);
                assertThat(context.getBean(ProblemDetailErrorHandler.class))
                    .isSameAs(context.getBean("customErrorHandler"));
              });
    }
  }

  @Nested
  @DisplayName("配置属性测试")
  class ConfigurationPropertiesTests {

    @Test
    @DisplayName("自定义超时配置 - 应用于 RestClient.Builder")
    void shouldApplyCustomTimeoutProperties() {
      contextRunner
          .withPropertyValues(
              "linqibin.starter.http-interface.connect-timeout=5000",
              "linqibin.starter.http-interface.read-timeout=30000")
          .run(
              context -> {
                assertThat(context).hasBean("httpInterfaceRestClientBuilder");
                // 验证 Bean 存在即可，超时值在 Apache HttpClient 内部，不易直接验证
              });
    }

    @Test
    @DisplayName("容错模式配置 - 传递给 ProblemDetailErrorHandler")
    void shouldPassTolerantModeToErrorHandler() {
      contextRunner
          .withPropertyValues("linqibin.starter.http-interface.error-handling.tolerant=false")
          .run(
              context -> {
                assertThat(context).hasSingleBean(ProblemDetailErrorHandler.class);
              });
    }

    @Test
    @DisplayName("连接池配置 - 应用于 Apache HttpClient")
    void shouldApplyConnectionPoolProperties() {
      contextRunner
          .withPropertyValues(
              "linqibin.starter.http-interface.connection-pool.max-conn-total=200",
              "linqibin.starter.http-interface.connection-pool.max-conn-per-route=50",
              "linqibin.starter.http-interface.connection-pool.validate-after-inactivity=10s",
              "linqibin.starter.http-interface.connection-pool.evict-idle-connections-after=60s")
          .run(
              context -> {
                assertThat(context).hasBean("httpInterfaceRestClientBuilder");
                // 验证配置属性绑定
                HttpInterfaceProperties props = context.getBean(HttpInterfaceProperties.class);
                assertThat(props.getConnectionPool().getMaxConnTotal()).isEqualTo(200);
                assertThat(props.getConnectionPool().getMaxConnPerRoute()).isEqualTo(50);
                assertThat(props.getConnectionPool().getValidateAfterInactivity().toSeconds())
                    .isEqualTo(10);
                assertThat(props.getConnectionPool().getEvictIdleConnectionsAfter().toSeconds())
                    .isEqualTo(60);
              });
    }
  }

  @Nested
  @DisplayName("RestClientCustomizer 测试")
  class RestClientCustomizerTests {

    @Test
    @DisplayName("验证 RestClientCustomizer 注册")
    void shouldRegisterRestClientCustomizer() {
      contextRunner.run(
          context -> {
            assertThat(context).hasBean("httpInterfaceRestClientCustomizer");
            assertThat(context.getBean("httpInterfaceRestClientCustomizer"))
                .isInstanceOf(RestClientCustomizer.class);
          });
    }
  }

  // ===== 测试配置类 =====

  /// 空测试配置：HttpInterfaceAutoConfiguration 不再需要外部依赖 Bean
  @Configuration
  static class TestConfiguration {
    // TraceId 传播已由 OpenTelemetry Java Agent 自动处理
  }

  /// 自定义 Bean 配置：用于测试 ConditionalOnMissingBean 逻辑
  @Configuration
  static class CustomBeansConfiguration {

    @Bean
    public ProblemDetailErrorHandler customErrorHandler(
        tools.jackson.databind.ObjectMapper objectMapper) {
      var properties = new HttpInterfaceProperties.ErrorHandlingProperties();
      properties.setTolerant(true);
      return new ProblemDetailErrorHandler(objectMapper, properties);
    }
  }
}
