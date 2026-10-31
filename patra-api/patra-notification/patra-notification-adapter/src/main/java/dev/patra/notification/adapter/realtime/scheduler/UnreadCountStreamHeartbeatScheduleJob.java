package dev.patra.notification.adapter.realtime.scheduler;

import dev.patra.notification.app.realtime.service.UnreadCountStreamRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/// 推送连接的心跳（PAP-145）：每 `patra.notification.realtime.heartbeat-interval`（25 秒）一轮。
///
/// 用进程内定时器、不用 XXL-Job：连接只在这个进程的内存里，心跳必须由持有连接的进程自己发；XXL-Job 是集群调度，
/// 粒度和语义都不合适。
@Component
@RequiredArgsConstructor
public class UnreadCountStreamHeartbeatScheduleJob {

  private final UnreadCountStreamRegistry registry;

  /// 给所有推送连接发一次心跳，顺带给在线的人记在站。
  @Scheduled(fixedRateString = "${patra.notification.realtime.heartbeat-interval}")
  public void heartbeat() {
    registry.heartbeat();
  }
}
