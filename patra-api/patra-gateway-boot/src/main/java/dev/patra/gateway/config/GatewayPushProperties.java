package dev.patra.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/// 推送专用连接池的配置，前缀 `patra.gateway.push`（PAP-145）。默认值在 `application.yml`。
///
/// @param maxConnections 到 notification 的推送连接最多几条；满了新的推送请求立即 503，不排队
@ConfigurationProperties(prefix = "patra.gateway.push")
public record GatewayPushProperties(int maxConnections) {}
