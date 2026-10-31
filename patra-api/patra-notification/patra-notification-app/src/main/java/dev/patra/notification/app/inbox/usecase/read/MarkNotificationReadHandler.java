package dev.patra.notification.app.inbox.usecase.read;

import dev.patra.notification.domain.inbox.event.UnreadCountsChangedEvent;
import dev.patra.notification.domain.inbox.exception.NotificationNotFoundException;
import dev.patra.notification.domain.inbox.model.enums.MarkReadOutcome;
import dev.patra.notification.domain.inbox.model.read.UnreadCounts;
import dev.patra.notification.domain.inbox.port.read.InboxReadPort;
import dev.patra.notification.domain.inbox.port.repository.NotificationRepository;
import dev.patra.tool.core.security.CurrentUserPort;
import dev.patra.tool.cqrs.CommandHandler;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionOperations;

/// 单条已读：点开即已读，已读的再标不报错；真的从未读变已读才发未读数变化事件（spec 8.4）。
@Component
@RequiredArgsConstructor
public class MarkNotificationReadHandler
    implements CommandHandler<MarkNotificationReadCommand, UnreadCounts> {

  private final NotificationRepository notifications;
  private final InboxReadPort reads;
  private final ApplicationEventPublisher events;
  private final CurrentUserPort currentUser;
  private final TransactionOperations transactions;
  private final Clock clock;

  /// 标已读。
  ///
  /// @param command 命令
  /// @return 新的未读数
  /// @throws NotificationNotFoundException 不存在或不是自己的
  @Override
  public UnreadCounts handle(MarkNotificationReadCommand command) {
    long recipient = currentUser.require().userId();
    Instant now = clock.instant();
    MarkReadOutcome outcome =
        Objects.requireNonNull(
            transactions.execute(
                status -> {
                  MarkReadOutcome result =
                      notifications.markRead(recipient, command.notificationId(), now);
                  if (result == MarkReadOutcome.MARKED) {
                    events.publishEvent(UnreadCountsChangedEvent.of(List.of(recipient)));
                  }
                  return result;
                }));
    if (outcome == MarkReadOutcome.NOT_FOUND) {
      throw new NotificationNotFoundException();
    }
    return reads.unreadCounts(recipient);
  }
}
