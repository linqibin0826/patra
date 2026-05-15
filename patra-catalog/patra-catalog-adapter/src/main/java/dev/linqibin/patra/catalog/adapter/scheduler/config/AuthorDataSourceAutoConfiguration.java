package dev.linqibin.patra.catalog.adapter.scheduler.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/// PubMed Computed Authors 数据源自动配置。
///
/// 启用 `AuthorDataSourceProperties` 配置属性绑定。
///
/// @author linqibin
/// @since 0.1.0
@Configuration
@EnableConfigurationProperties(AuthorDataSourceProperties.class)
public class AuthorDataSourceAutoConfiguration {
  // 仅用于启用配置属性
}
