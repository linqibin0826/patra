package dev.patra.catalog.config;

import dev.patra.catalog.app.fulltext.config.FullTextJobProperties;
import dev.patra.catalog.infra.fulltext.config.FullTextSourceProperties;
import dev.patra.catalog.infra.venue.config.VenueCoverImageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/// Catalog 对象存储相关配置注册。
///
/// 启用 `VenueCoverImageProperties` 绑定以支持通过 application.yml 覆盖
/// 封面图存储桶名（默认 `patra-catalog`）。
///
/// @author linqibin
/// @since 0.1.0
@Configuration
@EnableConfigurationProperties({
  VenueCoverImageProperties.class,
  FullTextSourceProperties.class,
  FullTextJobProperties.class
})
public class CatalogStorageConfiguration {}
