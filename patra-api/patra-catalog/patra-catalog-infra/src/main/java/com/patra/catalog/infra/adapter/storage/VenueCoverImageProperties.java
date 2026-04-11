package com.patra.catalog.infra.adapter.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/// Venue 封面图对象存储桶配置。
///
/// 通过 `patra.catalog.object-storage.buckets.venue-cover` 覆盖默认桶名。
/// 单字段设计避免未来扩展时的破坏性迁移。
///
/// @param venueCover 封面图桶名（默认 `patra-catalog`）
/// @author linqibin
/// @since 0.1.0
@ConfigurationProperties("patra.catalog.object-storage.buckets")
public record VenueCoverImageProperties(@DefaultValue("patra-catalog") String venueCover) {}
