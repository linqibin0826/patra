package dev.patra.interaction.domain.notification.policy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.domain.discussion.event.CommentEditedEvent;
import dev.patra.interaction.domain.discussion.event.CommentPublishedEvent;
import dev.patra.interaction.domain.notification.model.enums.NotificationType;
import dev.patra.interaction.domain.notification.model.vo.Recipient;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/// 生成规则（spec 4.2、4.3）：回复、经验根评论、@ 的先后；不提醒自己；同一人只发一条。
class NotificationRulesTest {

  private static final long A = 1L;
  private static final long B = 2L;
  private static final long C = 3L;
  private static final long OWNER = 9L;

  /// B 回复 A：A 收「回复了你的评论」。
  @Test
  void should_notify_parent_author_as_reply() {
    assertThat(NotificationRules.forPublished(reply(B, A, List.of())))
        .containsExactly(Recipient.of(A, NotificationType.COMMENT_REPLY));
  }

  /// 自己回复自己、@ 自己：没有提醒。
  @Test
  void should_not_notify_self() {
    assertThat(NotificationRules.forPublished(reply(A, A, List.of(A)))).isEmpty();
  }

  /// B 回复 A 且 @A、@C：A 只收一条回复，C 收 @。
  @Test
  void should_send_one_reply_when_parent_author_is_also_mentioned() {
    assertThat(NotificationRules.forPublished(reply(B, A, List.of(A, C))))
        .containsExactly(
            Recipient.of(A, NotificationType.COMMENT_REPLY),
            Recipient.of(C, NotificationType.MENTION));
  }

  /// 经验下的根评论提醒经验作者；同时 @ 了作者也只发一条。
  @Test
  void should_notify_post_owner_on_root_comment() {
    assertThat(NotificationRules.forPublished(postRoot(B, List.of(OWNER))))
        .containsExactly(Recipient.of(OWNER, NotificationType.POST_COMMENT));
  }

  /// 经验下楼里回复别人：不提醒经验作者（用户 2026-10-27 拍板）。
  @Test
  void should_not_notify_post_owner_on_reply_to_others() {
    assertThat(NotificationRules.forPublished(postReply(B, C)))
        .containsExactly(Recipient.of(C, NotificationType.COMMENT_REPLY));
  }

  /// 楼里回复的正是经验作者：「回复了你的评论」。
  @Test
  void should_notify_owner_as_reply_when_replied_directly() {
    assertThat(NotificationRules.forPublished(postReply(B, OWNER)))
        .containsExactly(Recipient.of(OWNER, NotificationType.COMMENT_REPLY));
  }

  /// 文献没有所有者：根评论只有 @。
  @Test
  void should_ignore_owner_on_publication() {
    CommentPublishedEvent event =
        base(B).subjectType(SubjectType.PUBLICATION).mentionedUserIds(List.of(C)).build();

    assertThat(NotificationRules.forPublished(event))
        .containsExactly(Recipient.of(C, NotificationType.MENTION));
  }

  /// 编辑：只对新增的 @，去掉作者本人。
  @Test
  void should_notify_only_added_mentions_on_edit() {
    CommentEditedEvent event =
        CommentEditedEvent.builder()
            .eventId(UUID.randomUUID())
            .commentId(10L)
            .subjectId(SubjectId.of(100L))
            .subjectType(SubjectType.PUBLICATION)
            .rootCommentId(10L)
            .authorId(A)
            .mentionedUserIds(List.of(B, C, A))
            .addedMentionedUserIds(List.of(C, A, C))
            .body("x")
            .editedAt(Instant.EPOCH)
            .build();

    assertThat(NotificationRules.forEdited(event)).containsExactly(C);
  }

  /// 文献下的回复。
  ///
  /// @param author 作者
  /// @param parentAuthor 被回复的人
  /// @param mentions @ 到的人
  /// @return 事件
  private static CommentPublishedEvent reply(long author, long parentAuthor, List<Long> mentions) {
    return base(author)
        .subjectType(SubjectType.PUBLICATION)
        .parentCommentId(9L)
        .parentAuthorId(parentAuthor)
        .mentionedUserIds(mentions)
        .build();
  }

  /// 经验下的根评论。
  ///
  /// @param author 作者
  /// @param mentions @ 到的人
  /// @return 事件
  private static CommentPublishedEvent postRoot(long author, List<Long> mentions) {
    return base(author)
        .subjectType(SubjectType.POST)
        .subjectOwner(SubjectOwner.of(OWNER, false))
        .mentionedUserIds(mentions)
        .build();
  }

  /// 经验下楼里的回复。
  ///
  /// @param author 作者
  /// @param parentAuthor 被回复的人
  /// @return 事件
  private static CommentPublishedEvent postReply(long author, long parentAuthor) {
    return base(author)
        .subjectType(SubjectType.POST)
        .subjectOwner(SubjectOwner.of(OWNER, false))
        .parentCommentId(9L)
        .parentAuthorId(parentAuthor)
        .build();
  }

  /// 事件的共同部分。
  ///
  /// @param author 作者
  /// @return 构建器
  private static CommentPublishedEvent.CommentPublishedEventBuilder base(long author) {
    return CommentPublishedEvent.builder()
        .eventId(UUID.randomUUID())
        .commentId(10L)
        .subjectId(SubjectId.of(100L))
        .rootCommentId(10L)
        .authorId(author)
        .body("x")
        .publishedAt(Instant.EPOCH);
  }
}
