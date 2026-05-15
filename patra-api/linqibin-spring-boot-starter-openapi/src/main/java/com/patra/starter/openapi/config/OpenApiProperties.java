package com.patra.starter.openapi.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// Patra OpenAPI 配置属性。
///
/// 提供 API 文档的基本信息配置，支持通过 `patra.openapi.*` 前缀进行自定义。
///
/// - `title`: API 文档标题，默认从 `spring.application.name` 推导
/// - `version`: API 版本号，默认 `0.1.0`
/// - `description`: API 文档描述
/// - `enabled`: 全局开关，默认启用
///
/// @author linqibin
/// @since 0.1.0
@ConfigurationProperties(prefix = "patra.openapi")
@Data
public class OpenApiProperties {

  /// 全局开关（默认启用）。
  private boolean enabled = true;

  /// API 文档标题（未设置时从 `spring.application.name` 推导）。
  private String title;

  /// API 版本号。
  private String version = "0.1.0";

  /// API 文档描述。
  private String description;
}
