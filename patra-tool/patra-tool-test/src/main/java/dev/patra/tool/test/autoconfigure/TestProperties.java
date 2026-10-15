package dev.patra.tool.test.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// 测试支持模块配置，前缀 `patra.test`。
///
/// @param enabled 总开关
@Validated
@ConfigurationProperties(prefix = "patra.test")
public record TestProperties(@DefaultValue("true") boolean enabled) {}
