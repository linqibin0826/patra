package dev.patra.notification.config;

import dev.patra.notification.adapter.realtime.config.RealtimeProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/// 实时推送的装配（PAP-145）：
///
/// - 绑定 `patra.notification.realtime.*`；
/// - 开启进程内定时任务：推送连接的心跳（`UnreadCountStreamHeartbeatScheduleJob`）；
/// - 开启 `@Async`：未读数变化在提交后换到 Boot 的 `applicationTaskExecutor` 上推（`UnreadCountPushListener`），
///   不占提交事务的消费线程或请求线程。服务里没有别的 `Executor` Bean，Boot 的那个才不会让位。
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RealtimeProperties.class)
@EnableAsync
@EnableScheduling
public class RealtimeConfiguration {}
