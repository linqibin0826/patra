package dev.patra.interaction.app.notification.usecase.read;

import dev.patra.interaction.domain.notification.model.read.UnreadCounts;
import dev.patra.interaction.domain.notification.port.read.NotificationReadPort;
import dev.patra.interaction.domain.notification.port.repository.NotificationRepository;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 全部已读：两个标签一起清（spec 7.3）。
@Component
@RequiredArgsConstructor
public class MarkAllNotificationsReadHandler
    implements CommandHandler<MarkAllNotificationsReadCommand, UnreadCounts> {

  private final NotificationRepository notifications;
  private final NotificationReadPort reads;
  private final CurrentUserPort currentUser;
  private final Clock clock;

  /// 全部标已读。
  ///
  /// @param command 命令
  /// @return 新的未读数
  @Override
  public UnreadCounts handle(MarkAllNotificationsReadCommand command) {
    long recipient = currentUser.require().userId();
    notifications.markAllRead(recipient, clock.instant());
    return reads.unreadCounts(recipient);
  }
}
