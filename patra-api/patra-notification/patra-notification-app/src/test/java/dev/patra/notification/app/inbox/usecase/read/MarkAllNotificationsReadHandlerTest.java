package dev.patra.notification.app.inbox.usecase.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.notification.domain.inbox.event.UnreadCountsChangedEvent;
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

/// 全部已读：三个标签一起；真标了才发事件。
class MarkAllNotificationsReadHandlerTest {

  private static final Instant NOW = Instant.parse("2026-10-31T08:00:00Z");

  private final NotificationRepository notifications = mock(NotificationRepository.class);
  private final InboxReadPort reads = mock(InboxReadPort.class);
  private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
  private final CurrentUserPort currentUser = mock(CurrentUserPort.class);
  private final MarkAllNotificationsReadHandler handler =
      new MarkAllNotificationsReadHandler(
          notifications,
          reads,
          events,
          currentUser,
          TransactionOperations.withoutTransaction(),
          Clock.fixed(NOW, ZoneOffset.UTC));

  /// 当前用户是 2；全部已读后未读数为 0。
  @BeforeEach
  void signIn() {
    when(currentUser.require())
        .thenReturn(CurrentUser.of(2L, 1L, AccountType.USER, ClientType.WEB));
    when(reads.unreadCounts(2L)).thenReturn(UnreadCounts.of(0, 0, 0));
  }

  /// 标了几条：发事件，返回新的未读数。
  @Test
  void should_publish_when_marked_any() {
    when(notifications.markAllRead(2L, NOW)).thenReturn(3);

    assertThat(handler.handle(MarkAllNotificationsReadCommand.of()))
        .isEqualTo(UnreadCounts.of(0, 0, 0));
    verify(events).publishEvent(UnreadCountsChangedEvent.of(Set.of(2L)));
  }

  /// 本来就没有未读：不发事件。
  @Test
  void should_not_publish_when_nothing_marked() {
    when(notifications.markAllRead(2L, NOW)).thenReturn(0);

    handler.handle(MarkAllNotificationsReadCommand.of());

    verify(events, never()).publishEvent(any(Object.class));
  }
}
