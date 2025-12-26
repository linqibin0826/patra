package com.patra.catalog.adapter.scheduler.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/// LSIOU 数据源自动配置。
///
/// 启用 {@link LsiouDataSourceProperties} 配置属性绑定。
///
/// @author linqibin
/// @since 0.1.0
@Configuration
@EnableConfigurationProperties(LsiouDataSourceProperties.class)
public class LsiouDataSourceAutoConfiguration {
  // 仅用于启用配置属性绑定，无需额外 Bean 定义
}
