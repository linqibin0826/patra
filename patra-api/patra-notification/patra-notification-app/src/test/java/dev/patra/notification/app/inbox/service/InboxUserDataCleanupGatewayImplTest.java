package dev.patra.notification.app.inbox.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import dev.patra.notification.domain.inbox.port.repository.NotificationRepository;
import java.util.List;
import org.junit.jupiter.api.Test;

/// 信箱的注销清理：删已注销用户作为收件人的全部提醒。
class InboxUserDataCleanupGatewayImplTest {

  private final NotificationRepository notifications = mock(NotificationRepository.class);
  private final InboxUserDataCleanupGatewayImpl gateway =
      new InboxUserDataCleanupGatewayImpl(notifications);

  /// 日志里按子域名列出删掉的行数。
  @Test
  void subdomain_is_inbox() {
    assertThat(gateway.subdomain()).isEqualTo("inbox");
  }

  /// 游标与条数原样交给仓储。
  @Test
  void find_users_after_delegates_to_repository() {
    when(notifications.findRecipientsAfter(3L, 100)).thenReturn(List.of(5L, 9L));

    assertThat(gateway.findUsersAfter(3L, 100)).containsExactly(5L, 9L);
  }

  /// 删这个人的全部提醒。
  @Test
  void delete_all_of_removes_recipient_notifications() {
    when(notifications.deleteAllOf(7L)).thenReturn(4);

    assertThat(gateway.deleteAllOf(7L)).isEqualTo(4);
  }
}
