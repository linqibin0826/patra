package dev.patra.tool.openapi.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// OpenAPI 文档配置，前缀 `patra.openapi`。
///
/// @param enabled 总开关
/// @param title 文档标题；空则用 `spring.application.name`
/// @param version 文档版本
/// @param description 文档描述
@Validated
@ConfigurationProperties(prefix = "patra.openapi")
public record OpenApiProperties(
    @DefaultValue("true") boolean enabled,
    String title,
    @DefaultValue("0.1.0") String version,
    String description) {}
