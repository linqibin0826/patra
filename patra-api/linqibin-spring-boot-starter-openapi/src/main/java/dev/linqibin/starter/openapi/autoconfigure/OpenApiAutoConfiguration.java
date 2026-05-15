package dev.linqibin.starter.openapi.autoconfigure;

import dev.linqibin.starter.openapi.config.OpenApiProperties;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/// Patra OpenAPI 自动配置。
///
/// 提供基于 SpringDoc + Scalar UI 的 API 文档自动配置：
///
/// - 根据 `spring.application.name` 自动推导文档标题
/// - 支持通过 `patra.openapi.*` 属性自定义标题、版本、描述
/// - 用户可通过自定义 `OpenAPI` bean 完全覆盖默认配置
/// - 通过 `patra.openapi.enabled=false` 完全禁用
///
/// @author linqibin
/// @since 0.1.0
@AutoConfiguration
@ConditionalOnClass(OpenAPI.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(prefix = "patra.openapi", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(OpenApiProperties.class)
public class OpenApiAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(OpenApiAutoConfiguration.class);

  private static final String DEFAULT_TITLE = "Patra API";

  /// 创建默认的 OpenAPI 配置 bean。
  ///
  /// 标题优先级：`patra.openapi.title` > `spring.application.name` > `"Patra API"`
  ///
  /// @param properties OpenAPI 配置属性
  /// @param applicationName Spring 应用名称（来自 `spring.application.name`）
  /// @return OpenAPI 实例
  @Bean
  @ConditionalOnMissingBean
  public OpenAPI patraOpenApi(
      OpenApiProperties properties, @Value("${spring.application.name:}") String applicationName) {
    var title = resolveTitle(properties.getTitle(), applicationName);
    log.info("初始化 Patra OpenAPI 文档 [标题: {}, 版本: {}]", title, properties.getVersion());

    var info = new Info().title(title).version(properties.getVersion());
    if (properties.getDescription() != null) {
      info.description(properties.getDescription());
    }
    return new OpenAPI().info(info);
  }

  /// 解析文档标题。
  ///
  /// 优先级：显式配置 > 应用名称 > 默认值。
  ///
  /// @param configuredTitle 显式配置的标题
  /// @param applicationName Spring 应用名称
  /// @return 解析后的标题
  private String resolveTitle(String configuredTitle, String applicationName) {
    if (configuredTitle != null && !configuredTitle.isBlank()) {
      return configuredTitle;
    }
    if (applicationName != null && !applicationName.isBlank()) {
      return applicationName;
    }
    return DEFAULT_TITLE;
  }
}
