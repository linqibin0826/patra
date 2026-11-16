package dev.patra.notification.app.preference.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import dev.patra.notification.domain.inbox.event.NotificationsCreatedEvent;
import dev.patra.notification.domain.inbox.model.read.CreatedNotification;
import dev.patra.notification.domain.kernel.model.enums.NotificationCategory;
import dev.patra.notification.domain.preference.model.aggregate.EmailPreference;
import dev.patra.notification.domain.preference.model.read.EmailMode;
import dev.patra.notification.domain.preference.port.repository.EmailPreferenceRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/// 提醒写入信箱时，为会收到邮件的收件人预建默认偏好行，发信时读端口只读、不再建行。
class EmailPreferenceCreationListenerTest {

  private static final Instant NOW = Instant.parse("2026-11-16T08:00:00Z");

  private final EmailPreferenceRepository preferences = mock(EmailPreferenceRepository.class);
  private final EmailPreferenceCreationListener listener =
      new EmailPreferenceCreationListener(preferences);

  /// 同一个人一次收到回复和 @ 只建一次；默认两类都是即时，带退订令牌；有帮助没有邮件，不建。
  @Test
  void should_insert_default_preference_once_per_recipient() {
    listener.on(
        NotificationsCreatedEvent.of(
            List.of(
                created(1L, 7L, NotificationCategory.REPLY),
                created(2L, 7L, NotificationCategory.MENTION),
                created(3L, 8L, NotificationCategory.REPLY),
                created(4L, 9L, NotificationCategory.HELPFUL))));

    ArgumentCaptor<EmailPreference> inserted = ArgumentCaptor.forClass(EmailPreference.class);
    verify(preferences, times(2)).insertIfAbsent(inserted.capture());
    assertThat(inserted.getAllValues())
        .extracting(EmailPreference::getUserId)
        .containsExactlyInAnyOrder(7L, 8L);
    assertThat(inserted.getAllValues())
        .allSatisfy(
            preference -> {
              assertThat(preference.getReply()).isEqualTo(EmailMode.INSTANT);
              assertThat(preference.getMention()).isEqualTo(EmailMode.INSTANT);
              assertThat(preference.getUnsubscribeToken()).isNotNull();
            });
  }

  /// 只有有帮助的事件不建任何行。
  @Test
  void should_skip_when_no_email_category() {
    listener.on(
        NotificationsCreatedEvent.of(List.of(created(4L, 9L, NotificationCategory.HELPFUL))));

    verify(preferences, never()).insertIfAbsent(any());
  }

  private static CreatedNotification created(
      long notificationId, long recipientId, NotificationCategory category) {
    return new CreatedNotification(notificationId, recipientId, category, NOW, NOW);
  }
}
