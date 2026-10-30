package dev.patra.interaction.app.notification.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import dev.patra.interaction.domain.discussion.event.CommentDeletedEvent;
import dev.patra.interaction.domain.discussion.event.CommentEditedEvent;
import dev.patra.interaction.domain.discussion.event.CommentPublishedEvent;
import dev.patra.interaction.domain.notification.exception.NotificationNotFoundException;
import dev.patra.interaction.domain.notification.model.enums.NotificationType;
import dev.patra.interaction.domain.notification.model.vo.NotificationDraft;
import dev.patra.interaction.domain.notification.port.repository.NotificationRepository;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.PlainText;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.port.markdown.PlainTextPort;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.dao.DataIntegrityViolationException;

/// 订阅讨论事件（spec 6.1）：按规则写入、编辑刷新摘录、删除标记；数据库异常包成 500。
class CommentNotificationListenerTest {

  private static final long A = 1L;
  private static final long B = 2L;
  private static final long C = 3L;
  private static final Instant AT = Instant.parse("2026-10-27T02:00:00Z");

  private final NotificationRepository notifications = mock(NotificationRepository.class);
  private final PlainTextPort plainText = mock(PlainTextPort.class);
  private final CommentNotificationListener listener =
      new CommentNotificationListener(notifications, plainText);

  @BeforeEach
  void excerpt() {
    when(plainText.toPlainText(anyString(), anySet())).thenReturn(PlainText.of("摘录"));
  }

  /// B 回复 A 且 @C：两条，时间取事件的发表时刻，匿名标记照事件。
  @Test
  void should_insert_drafts_for_published_comment() {
    listener.on(published(B, A, List.of(C), true));

    ArgumentCaptor<List<NotificationDraft>> drafts = ArgumentCaptor.captor();
    verify(notifications).insertAll(drafts.capture());
    assertThat(drafts.getValue())
        .extracting(NotificationDraft::recipientId, NotificationDraft::type)
        .containsExactly(
            tuple(A, NotificationType.COMMENT_REPLY), tuple(C, NotificationType.MENTION));
    assertThat(drafts.getValue())
        .allSatisfy(
            d -> {
              assertThat(d.createdAt()).isEqualTo(AT);
              assertThat(d.actorAnonymous()).isTrue();
              assertThat(d.actorId()).isEqualTo(B);
              assertThat(d.commentId()).isEqualTo(10L);
            });
    verify(plainText).toPlainText("正文", Set.of(C));
  }

  /// 没人要提醒：不转摘录、不写库。
  @Test
  void should_skip_excerpt_when_nobody_to_notify() {
    listener.on(published(A, A, List.of(A), false));

    verifyNoInteractions(plainText, notifications);
  }

  /// 编辑：先刷新已有提醒的摘录，再给新增的 @ 写入。
  @Test
  void should_refresh_excerpt_then_notify_added_mentions_on_edit() {
    listener.on(edited(A, List.of(B, C), List.of(C)));

    InOrder order = inOrder(notifications);
    order.verify(notifications).refreshExcerpt(eq(10L), any());
    ArgumentCaptor<List<NotificationDraft>> drafts = ArgumentCaptor.captor();
    order.verify(notifications).insertAll(drafts.capture());
    assertThat(drafts.getValue())
        .singleElement()
        .satisfies(
            d -> {
              assertThat(d.recipientId()).isEqualTo(C);
              assertThat(d.type()).isEqualTo(NotificationType.MENTION);
              assertThat(d.createdAt()).isEqualTo(AT);
            });
  }

  /// 删除：删掉这条评论的全部提醒。
  @Test
  void should_delete_notifications_on_comment_deleted() {
    listener.on(commentDeleted(true));

    verify(notifications).deleteByComment(10L);
    verifyNoMoreInteractions(notifications);
  }

  /// 数据库异常包成 500，原始异常留在原因链里。
  @Test
  void should_wrap_data_access_failure_as_internal_error() {
    DataIntegrityViolationException failure = new DataIntegrityViolationException("x");
    when(notifications.insertAll(anyList())).thenThrow(failure);

    assertThatThrownBy(() -> listener.on(published(B, A, List.of(), false)))
        .isInstanceOfSatisfying(
            NotificationWriteFailedException.class,
            ex -> {
              assertThat(ex.getErrorCode().httpStatus()).isEqualTo(500);
              assertThat(ex.getCause()).isSameAs(failure);
            });
  }

  /// 领域异常原样传播。
  @Test
  void should_propagate_domain_exception() {
    NotificationNotFoundException failure = new NotificationNotFoundException();
    when(notifications.deleteByComment(10L)).thenThrow(failure);

    assertThatThrownBy(() -> listener.on(commentDeleted(false))).isSameAs(failure);
  }

  /// 文献下的回复事件。
  ///
  /// @param author 作者
  /// @param parentAuthor 被回复的人
  /// @param mentions @ 到的人
  /// @param anonymous 作者是否匿名所有者
  /// @return 事件
  private static CommentPublishedEvent published(
      long author, long parentAuthor, List<Long> mentions, boolean anonymous) {
    return CommentPublishedEvent.builder()
        .commentId(10L)
        .subjectId(SubjectId.of(100L))
        .subjectType(SubjectType.PUBLICATION)
        .rootCommentId(9L)
        .parentCommentId(9L)
        .parentAuthorId(parentAuthor)
        .authorId(author)
        .authorAnonymous(anonymous)
        .mentionedUserIds(mentions)
        .body("正文")
        .publishedAt(AT)
        .build();
  }

  /// 编辑事件。
  ///
  /// @param author 作者
  /// @param mentions 编辑后的 @
  /// @param added 新增的 @
  /// @return 事件
  private static CommentEditedEvent edited(long author, List<Long> mentions, List<Long> added) {
    return CommentEditedEvent.builder()
        .commentId(10L)
        .subjectId(SubjectId.of(100L))
        .subjectType(SubjectType.PUBLICATION)
        .rootCommentId(10L)
        .authorId(author)
        .mentionedUserIds(mentions)
        .addedMentionedUserIds(added)
        .body("改过")
        .editedAt(AT)
        .build();
  }

  /// 评论 10 的删除事件。
  ///
  /// @param placeholder 是否留下占位
  /// @return 事件
  private static CommentDeletedEvent commentDeleted(boolean placeholder) {
    return CommentDeletedEvent.builder()
        .eventId(UUID.randomUUID())
        .commentId(10L)
        .version(1)
        .subjectId(SubjectId.of(100L))
        .authorId(A)
        .placeholder(placeholder)
        .deletedAt(AT)
        .build();
  }
}
