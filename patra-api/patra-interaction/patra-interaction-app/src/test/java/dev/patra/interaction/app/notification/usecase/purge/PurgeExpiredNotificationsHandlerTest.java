package dev.patra.interaction.app.notification.usecase.purge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.notification.port.repository.NotificationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/// 保留期清理：删除北京时间 5 个自然月前的提醒。
class PurgeExpiredNotificationsHandlerTest {

  private final NotificationRepository notifications = mock(NotificationRepository.class);

  /// 截止时刻按注入的时钟算，返回删除条数。
  @Test
  void should_delete_notifications_older_than_five_months() {
    Clock clock = Clock.fixed(Instant.parse("2026-10-27T01:00:00Z"), ZoneOffset.UTC);
    when(notifications.deleteCreatedBefore(Instant.parse("2026-05-27T01:00:00Z"))).thenReturn(3);

    int deleted =
        new PurgeExpiredNotificationsHandler(notifications, clock)
            .handle(PurgeExpiredNotificationsCommand.of());

    assertThat(deleted).isEqualTo(3);
  }
}
