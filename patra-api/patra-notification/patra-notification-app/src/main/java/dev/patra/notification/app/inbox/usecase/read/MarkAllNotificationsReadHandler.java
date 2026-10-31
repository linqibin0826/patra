package dev.patra.notification.app.inbox.usecase.read;

import dev.patra.notification.domain.inbox.event.UnreadCountsChangedEvent;
import dev.patra.notification.domain.inbox.model.read.UnreadCounts;
import dev.patra.notification.domain.inbox.port.read.InboxReadPort;
import dev.patra.notification.domain.inbox.port.repository.NotificationRepository;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 全部已读：三个标签一起清（PAP-140）；真的标了才发未读数变化事件。
@Component
@RequiredArgsConstructor
public class MarkAllNotificationsReadHandler
    implements CommandHandler<MarkAllNotificationsReadCommand, UnreadCounts> {

  private final NotificationRepository notifications;
  private final InboxReadPort reads;
  private final ApplicationEventPublisher events;
  private final CurrentUserPort currentUser;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 全部标已读。
  ///
  /// @param command 命令
  /// @return 新的未读数
  @Override
  public UnreadCounts handle(MarkAllNotificationsReadCommand command) {
    long recipient = currentUser.require().userId();
    Instant now = clock.instant();
    transactions.executeWithoutResult(
        status -> {
          if (notifications.markAllRead(recipient, now) > 0) {
            events.publishEvent(UnreadCountsChangedEvent.of(List.of(recipient)));
          }
        });
    return reads.unreadCounts(recipient);
  }
}
