package dev.patra.notification.adapter.realtime.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/// 实时推送的配置，前缀 `patra.notification.realtime`（PAP-145）。默认值在 boot 的 `application.yml`。
///
/// @param heartbeatInterval 心跳间隔：短于网关读超时 60 秒、Cloudflare 空闲超时 125 秒
/// @param maxConnectionAge 一条连接最长活多久，也是 `SseEmitter` 的异步超时；到点服务端正常结束
@ConfigurationProperties(prefix = "patra.notification.realtime")
public record RealtimeProperties(Duration heartbeatInterval, Duration maxConnectionAge) {}
