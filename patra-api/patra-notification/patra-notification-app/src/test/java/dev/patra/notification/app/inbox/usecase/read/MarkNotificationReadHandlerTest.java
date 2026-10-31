package dev.patra.notification.app.inbox.usecase.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.notification.domain.inbox.event.UnreadCountsChangedEvent;
import dev.patra.notification.domain.inbox.exception.NotificationNotFoundException;
import dev.patra.notification.domain.inbox.model.enums.MarkReadOutcome;
import dev.patra.notification.domain.inbox.model.read.UnreadCounts;
import dev.patra.notification.domain.inbox.port.read.InboxReadPort;
import dev.patra.notification.domain.inbox.port.repository.NotificationRepository;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionOperations;

/// 单条已读：点开即已读，已读的再标不报错；不是自己的 404。
class MarkNotificationReadHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-31T08:00:00Z");

  private final NotificationRepository notifications = mock(NotificationRepository.class);
  private final InboxReadPort reads = mock(InboxReadPort.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final CurrentUserPort currentUser = mock(CurrentUserPort.class);
  private final MarkNotificationReadHandler handler =
      new MarkNotificationReadHandler(
          notifications,
          reads,
          events,
          currentUser,
          TransactionOperations.withoutTransaction(),
          Clock.fixed(NOW, ZoneOffset.UTC));

  /// 当前用户是 2。
  @BeforeEach
  void signIn() {
    when(currentUser.require())
        .thenReturn(CurrentUser.of(2L, 1L, AccountType.USER, ClientType.WEB));
  }

  /// 从未读变已读：返回新的未读数，发未读数变化事件。
  @Test
  void should_return_counts_and_publish_when_marked() {
    when(notifications.markRead(2L, 11L, NOW)).thenReturn(MarkReadOutcome.MARKED);
    when(reads.unreadCounts(2L)).thenReturn(UnreadCounts.of(1, 0, 0));

    assertThat(handler.handle(MarkNotificationReadCommand.of(11L)))
        .isEqualTo(UnreadCounts.of(1, 0, 0));
    verify(events).publishEvent(UnreadCountsChangedEvent.of(Set.of(2L)));
  }

  /// 本来就已读：不报错、不发事件。
  @Test
  void should_not_publish_when_already_read() {
    when(notifications.markRead(2L, 11L, NOW)).thenReturn(MarkReadOutcome.ALREADY_READ);
    when(reads.unreadCounts(2L)).thenReturn(UnreadCounts.of(0, 0, 0));

    handler.handle(MarkNotificationReadCommand.of(11L));

    verify(events, never()).publishEvent(any(Object.class));
  }

  /// 不存在或不是自己的：404。
  @Test
  void should_throw_not_found() {
    when(notifications.markRead(2L, 11L, NOW)).thenReturn(MarkReadOutcome.NOT_FOUND);

    assertThatThrownBy(() -> handler.handle(MarkNotificationReadCommand.of(11L)))
        .isInstanceOf(NotificationNotFoundException.class);
  }
}
