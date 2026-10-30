package dev.patra.notification.domain.inbox.policy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.notification.domain.inbox.model.enums.NotificationType;
import dev.patra.notification.domain.inbox.model.vo.CommentFacts;
import dev.patra.notification.domain.inbox.model.vo.Recipient;
import dev.patra.notification.domain.kernel.model.enums.SubjectType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/// 谁该收到提醒（搬自 interaction 的 `NotificationRules`，spec 7.3）。
class RecipientRulesTest {

  private static final long A = 1L;
  private static final long B = 2L;
  private static final long C = 3L;
  private static final long OWNER = 9L;

  /// B 回复 A：A 收「回复了你的评论」。
  @Test
  void should_notify_parent_author_as_reply() {
    assertThat(RecipientRules.forPublished(reply(B, A, List.of())))
        .containsExactly(Recipient.of(A, NotificationType.COMMENT_REPLY));
  }

  /// 自己回复自己、@ 自己：没有提醒。
  @Test
  void should_not_notify_self() {
    assertThat(RecipientRules.forPublished(reply(A, A, List.of(A)))).isEmpty();
  }

  /// B 回复 A 且 @A、@C：A 只收一条回复，C 收 @。
  @Test
  void should_send_one_reply_when_parent_author_is_also_mentioned() {
    assertThat(RecipientRules.forPublished(reply(B, A, List.of(A, C))))
        .containsExactly(
            Recipient.of(A, NotificationType.COMMENT_REPLY),
            Recipient.of(C, NotificationType.MENTION));
  }

  /// 经验下的根评论提醒经验作者；同时 @ 了作者也只发一条。
  @Test
  void should_notify_post_owner_on_root_comment() {
    assertThat(RecipientRules.forPublished(postRoot(B, List.of(OWNER))))
        .containsExactly(Recipient.of(OWNER, NotificationType.POST_COMMENT));
  }

  /// 经验下楼里回复别人：不提醒经验作者。
  @Test
  void should_not_notify_post_owner_on_reply_to_others() {
    assertThat(RecipientRules.forPublished(postReply(B, C)))
        .containsExactly(Recipient.of(C, NotificationType.COMMENT_REPLY));
  }

  /// 楼里回复的正是经验作者：「回复了你的评论」。
  @Test
  void should_notify_owner_as_reply_when_replied_directly() {
    assertThat(RecipientRules.forPublished(postReply(B, OWNER)))
        .containsExactly(Recipient.of(OWNER, NotificationType.COMMENT_REPLY));
  }

  /// 文献没有所有者：根评论只有 @。
  @Test
  void should_ignore_owner_on_publication() {
    CommentFacts facts =
        base(B).subjectType(SubjectType.PUBLICATION).mentionedUserIds(List.of(C)).build();

    assertThat(RecipientRules.forPublished(facts))
        .containsExactly(Recipient.of(C, NotificationType.MENTION));
  }

  /// 不认识的对象类型（事件里的字符串解析不出来）：不当经验处理。
  @Test
  void should_ignore_owner_when_subject_type_unknown() {
    CommentFacts facts = base(B).subjectType(null).build();

    assertThat(RecipientRules.forPublished(facts)).isEmpty();
  }

  /// 编辑：只给这条评论第一次 @ 到的人发 @，去掉作者本人。
  @Test
  void should_notify_only_added_mentions_on_edit() {
    CommentFacts facts = base(B).subjectType(SubjectType.PUBLICATION).build();

    assertThat(RecipientRules.forEdited(facts, List.of(C, B))).containsExactly(C);
  }

  /// 编辑新 @ 了被回复的人、根评论下的经验作者：他们由发表负责，编辑不碰，结果与到达顺序无关。
  @Test
  void should_skip_parent_author_and_post_owner_on_edit() {
    assertThat(RecipientRules.forEdited(reply(B, A, List.of()), List.of(A, C))).containsExactly(C);
    assertThat(RecipientRules.forEdited(postRoot(B, List.of()), List.of(OWNER, C)))
        .containsExactly(C);
    assertThat(RecipientRules.forEdited(postReply(B, C), List.of(OWNER))).containsExactly(OWNER);
  }

  /// 文献下 B 回复 A，带 @。
  ///
  /// @param author 作者
  /// @param parentAuthor 被回复评论的作者
  /// @param mentioned @ 到的人
  /// @return 事实
  private static CommentFacts reply(long author, long parentAuthor, List<Long> mentioned) {
    return base(author)
        .subjectType(SubjectType.PUBLICATION)
        .parentCommentId(100L)
        .parentAuthorId(parentAuthor)
        .mentionedUserIds(mentioned)
        .build();
  }

  /// 经验下的根评论。
  ///
  /// @param author 作者
  /// @param mentioned @ 到的人
  /// @return 事实
  private static CommentFacts postRoot(long author, List<Long> mentioned) {
    return base(author)
        .subjectType(SubjectType.POST)
        .subjectOwnerId(OWNER)
        .mentionedUserIds(mentioned)
        .build();
  }

  /// 经验下楼里回复某人。
  ///
  /// @param author 作者
  /// @param parentAuthor 被回复评论的作者
  /// @return 事实
  private static CommentFacts postReply(long author, long parentAuthor) {
    return base(author)
        .subjectType(SubjectType.POST)
        .subjectOwnerId(OWNER)
        .parentCommentId(100L)
        .parentAuthorId(parentAuthor)
        .build();
  }

  /// 公共字段。
  ///
  /// @param author 作者
  /// @return 构建器
  private static CommentFacts.CommentFactsBuilder base(long author) {
    return CommentFacts.builder()
        .commentId(500L)
        .version(1)
        .subjectId(40L)
        .authorId(author)
        .plainText("正文")
        .occurredAt(Instant.parse("2026-10-31T00:00:00Z"));
  }
}
