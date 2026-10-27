package dev.patra.interaction.app.notification.usecase.read;

import dev.patra.interaction.domain.notification.exception.NotificationNotFoundException;
import dev.patra.interaction.domain.notification.model.read.UnreadCounts;
import dev.patra.interaction.domain.notification.port.read.NotificationReadPort;
import dev.patra.interaction.domain.notification.port.repository.NotificationRepository;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/// 单条已读：点开即已读（spec 7.3）；已读的再标不报错。
@Component
@RequiredArgsConstructor
public class MarkNotificationReadHandler
    implements CommandHandler<MarkNotificationReadCommand, UnreadCounts> {

  private final NotificationRepository notifications;
  private final NotificationReadPort reads;
  private final CurrentUserPort currentUser;
  private final Clock clock;

  /// 标已读。
  ///
  /// @param command 命令
  /// @return 新的未读数
  /// @throws NotificationNotFoundException 不存在或不是自己的
  @Override
  public UnreadCounts handle(MarkNotificationReadCommand command) {
    long recipient = currentUser.require().userId();
    if (!notifications.markRead(recipient, command.notificationId(), clock.instant())) {
      throw new NotificationNotFoundException();
    }
    return reads.unreadCounts(recipient);
  }
}
