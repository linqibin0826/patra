package com.patra.starter.openapi.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/// OpenApiAutoConfiguration 自动配置测试。
///
/// 使用 WebApplicationContextRunner 验证 Bean 装配和条件配置逻辑。
///
/// 测试覆盖：
///
/// - 默认配置下的 Bean 注册
/// - 属性覆盖默认标题
/// - 禁用配置时的行为
/// - 自定义 Bean 不被覆盖
///
/// @author linqibin
/// @since 0.1.0
@Timeout(value = 10, unit = TimeUnit.SECONDS)
@DisplayName("OpenApiAutoConfiguration 自动配置测试")
class OpenApiAutoConfigurationTest {

  private final WebApplicationContextRunner contextRunner =
      new WebApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(OpenApiAutoConfiguration.class));

  @Nested
  @DisplayName("默认配置测试")
  class DefaultConfigurationTests {

    @Test
    @DisplayName("默认配置 - 创建 OpenAPI bean，标题来自 spring.application.name")
    void shouldCreateOpenApiBeanWithApplicationName() {
      contextRunner
          .withPropertyValues("spring.application.name=patra-catalog")
          .run(
              context -> {
                assertThat(context).hasSingleBean(OpenAPI.class);
                OpenAPI openAPI = context.getBean(OpenAPI.class);
                assertThat(openAPI.getInfo()).isNotNull();
                assertThat(openAPI.getInfo().getTitle()).isEqualTo("patra-catalog");
              });
    }

    @Test
    @DisplayName("未设置 application.name - 使用默认标题")
    void shouldUseDefaultTitleWhenNoApplicationName() {
      contextRunner.run(
          context -> {
            assertThat(context).hasSingleBean(OpenAPI.class);
            OpenAPI openAPI = context.getBean(OpenAPI.class);
            assertThat(openAPI.getInfo().getTitle()).isEqualTo("Patra API");
          });
    }

    @Test
    @DisplayName("默认版本号为 0.1.0")
    void shouldUseDefaultVersion() {
      contextRunner.run(
          context -> {
            OpenAPI openAPI = context.getBean(OpenAPI.class);
            assertThat(openAPI.getInfo().getVersion()).isEqualTo("0.1.0");
          });
    }
  }

  @Nested
  @DisplayName("属性覆盖测试")
  class PropertyOverrideTests {

    @Test
    @DisplayName("自定义 title 覆盖默认标题")
    void shouldOverrideTitleWithProperty() {
      contextRunner
          .withPropertyValues(
              "spring.application.name=patra-catalog", "patra.openapi.title=Catalog Service API")
          .run(
              context -> {
                OpenAPI openAPI = context.getBean(OpenAPI.class);
                assertThat(openAPI.getInfo().getTitle()).isEqualTo("Catalog Service API");
              });
    }

    @Test
    @DisplayName("自定义 version 覆盖默认版本号")
    void shouldOverrideVersionWithProperty() {
      contextRunner
          .withPropertyValues("patra.openapi.version=1.0.0")
          .run(
              context -> {
                OpenAPI openAPI = context.getBean(OpenAPI.class);
                assertThat(openAPI.getInfo().getVersion()).isEqualTo("1.0.0");
              });
    }

    @Test
    @DisplayName("自定义 description 设置描述")
    void shouldSetDescription() {
      contextRunner
          .withPropertyValues("patra.openapi.description=Catalog service API documentation")
          .run(
              context -> {
                OpenAPI openAPI = context.getBean(OpenAPI.class);
                assertThat(openAPI.getInfo().getDescription())
                    .isEqualTo("Catalog service API documentation");
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
          .withPropertyValues("patra.openapi.enabled=false")
          .run(context -> assertThat(context).doesNotHaveBean(OpenAPI.class));
    }

    @Test
    @DisplayName("自定义 OpenAPI bean 存在时 - 不覆盖用户配置")
    void shouldNotOverrideUserDefinedOpenApiBean() {
      contextRunner
          .withUserConfiguration(CustomOpenApiConfiguration.class)
          .run(
              context -> {
                assertThat(context).hasSingleBean(OpenAPI.class);
                OpenAPI openAPI = context.getBean(OpenAPI.class);
                assertThat(openAPI.getInfo().getTitle()).isEqualTo("Custom API Title");
              });
    }
  }

  // ===== 测试配置类 =====

  /// 自定义 OpenAPI Bean 配置：用于测试 ConditionalOnMissingBean 逻辑。
  @Configuration
  static class CustomOpenApiConfiguration {

    @Bean
    public OpenAPI customOpenApi() {
      return new OpenAPI().info(new Info().title("Custom API Title").version("2.0.0"));
    }
  }
}
