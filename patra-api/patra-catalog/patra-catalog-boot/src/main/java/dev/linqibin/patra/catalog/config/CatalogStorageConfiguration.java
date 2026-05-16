package dev.linqibin.patra.catalog.config;

import dev.linqibin.patra.catalog.infra.adapter.storage.VenueCoverImageProperties;
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
@EnableConfigurationProperties(VenueCoverImageProperties.class)
public class CatalogStorageConfiguration {}
