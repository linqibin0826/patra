package dev.patra.interaction.app.notification.usecase.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.notification.exception.NotificationNotFoundException;
import dev.patra.interaction.domain.notification.model.read.UnreadCounts;
import dev.patra.interaction.domain.notification.port.read.NotificationReadPort;
import dev.patra.interaction.domain.notification.port.repository.NotificationRepository;
import dev.patra.tool.core.security.AccountType;
import dev.patra.tool.core.security.ClientType;
import dev.patra.tool.core.security.CurrentUser;
import dev.patra.tool.core.security.CurrentUserPort;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/// 单条已读与全部已读（spec 7.3）：返回新的未读数；不存在或不是自己的 404。
class MarkNotificationReadHandlerTest {

  private static final long R = 1L;
  private static final Instant NOW = Instant.parse("2026-10-27T01:00:00Z");

  private final NotificationRepository notifications = mock(NotificationRepository.class);
  private final NotificationReadPort reads = mock(NotificationReadPort.class);
  private final CurrentUserPort currentUser = mock(CurrentUserPort.class);
  private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

  @BeforeEach
  void signedIn() {
    CurrentUser user = CurrentUser.of(R, 1L, AccountType.USER, ClientType.WEB);
    when(currentUser.current()).thenReturn(Optional.of(user));
    when(currentUser.require()).thenReturn(user);
    when(reads.unreadCounts(R)).thenReturn(UnreadCounts.of(1, 2));
  }

  /// 标了之后返回读端口的新计数。
  @Test
  void should_return_new_counts_after_marking_one() {
    when(notifications.markRead(R, 10L, NOW)).thenReturn(true);

    UnreadCounts counts =
        new MarkNotificationReadHandler(notifications, reads, currentUser, clock)
            .handle(MarkNotificationReadCommand.of(10L));

    assertThat(counts).isEqualTo(UnreadCounts.of(1, 2));
  }

  /// 不存在或不是自己的：404。
  @Test
  void should_reject_missing_notification() {
    when(notifications.markRead(R, 10L, NOW)).thenReturn(false);

    assertThatThrownBy(
            () ->
                new MarkNotificationReadHandler(notifications, reads, currentUser, clock)
                    .handle(MarkNotificationReadCommand.of(10L)))
        .isInstanceOf(NotificationNotFoundException.class);
  }

  /// 全部已读：标完返回新计数。
  @Test
  void should_mark_all_and_return_counts() {
    UnreadCounts counts =
        new MarkAllNotificationsReadHandler(notifications, reads, currentUser, clock)
            .handle(MarkAllNotificationsReadCommand.of());

    verify(notifications).markAllRead(R, NOW);
    assertThat(counts).isEqualTo(UnreadCounts.of(1, 2));
  }
}
