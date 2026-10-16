package dev.patra.tool.jpa.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// JPA 模块配置，前缀 `patra.jpa`。
///
/// @param enabled 总开关
@Validated
@ConfigurationProperties(prefix = "patra.jpa")
public record JpaProperties(@DefaultValue("true") boolean enabled) {}
