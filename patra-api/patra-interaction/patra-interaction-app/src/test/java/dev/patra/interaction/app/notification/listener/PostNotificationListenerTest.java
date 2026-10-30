package dev.patra.interaction.app.notification.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.notification.exception.NotificationNotFoundException;
import dev.patra.interaction.domain.notification.port.repository.NotificationRepository;
import dev.patra.interaction.domain.post.event.PostDeletedEvent;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

/// 投稿经验删除后删掉它下面的全部提醒（PAP-127）。
class PostNotificationListenerTest {

  private static final SubjectId SUBJECT = SubjectId.of(77L);
  private static final PostDeletedEvent DELETED =
      PostDeletedEvent.builder()
          .eventId(UUID.randomUUID())
          .postId(500L)
          .subjectId(SUBJECT)
          .authorId(7L)
          .deletedAt(Instant.parse("2026-10-29T02:00:00Z"))
          .build();

  private final NotificationRepository notifications = mock(NotificationRepository.class);
  private final PostNotificationListener listener = new PostNotificationListener(notifications);

  /// 按这条经验的登记对象删。
  @Test
  void should_delete_notifications_of_subject_on_post_deleted() {
    listener.on(DELETED);

    verify(notifications).deleteBySubject(SUBJECT);
    verifyNoMoreInteractions(notifications);
  }

  /// 数据库异常包成 500，原始异常留在原因链里。
  @Test
  void should_wrap_unexpected_failure() {
    DataIntegrityViolationException failure = new DataIntegrityViolationException("x");
    when(notifications.deleteBySubject(SUBJECT)).thenThrow(failure);

    assertThatThrownBy(() -> listener.on(DELETED))
        .isInstanceOfSatisfying(
            NotificationWriteFailedException.class,
            ex -> assertThat(ex.getCause()).isSameAs(failure));
  }

  /// 领域异常原样传播。
  @Test
  void should_propagate_domain_exception() {
    NotificationNotFoundException failure = new NotificationNotFoundException();
    when(notifications.deleteBySubject(SUBJECT)).thenThrow(failure);

    assertThatThrownBy(() -> listener.on(DELETED)).isSameAs(failure);
  }
}
