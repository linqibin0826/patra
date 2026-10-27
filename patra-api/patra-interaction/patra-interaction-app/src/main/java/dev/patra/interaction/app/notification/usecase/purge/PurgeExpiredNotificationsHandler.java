package dev.patra.interaction.app.notification.usecase.purge;

import dev.patra.interaction.domain.notification.policy.NotificationRetention;
import dev.patra.interaction.domain.notification.port.repository.NotificationRepository;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/// 删除生成时刻早于「北京时间减 5 个自然月」的提醒，已读未读都删（spec 8.1）。量很小，一条语句完成。
@Slf4j
@Component
@RequiredArgsConstructor
public class PurgeExpiredNotificationsHandler
    implements CommandHandler<PurgeExpiredNotificationsCommand, Integer> {

  private final NotificationRepository notifications;
  private final Clock clock;

  /// 清理。
  ///
  /// @param command 命令
  /// @return 删除的条数
  @Override
  public Integer handle(PurgeExpiredNotificationsCommand command) {
    Instant cutoff = NotificationRetention.cutoff(clock.instant());
    int deleted = notifications.deleteCreatedBefore(cutoff);
    log.info("已清理 {} 条 {} 之前的提醒", deleted, cutoff);
    return deleted;
  }
}
