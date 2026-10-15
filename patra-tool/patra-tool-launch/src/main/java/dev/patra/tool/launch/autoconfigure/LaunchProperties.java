package dev.patra.tool.launch.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/// 启动模块配置，前缀 `patra.launch`。
///
/// @param enabled 总开关
@Validated
@ConfigurationProperties(prefix = "patra.launch")
public record LaunchProperties(@DefaultValue("true") boolean enabled) {}
