package dev.patra.interaction.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.interaction.api.discussion.event.CommentDeleted;
import dev.patra.interaction.api.discussion.event.CommentEdited;
import dev.patra.interaction.api.discussion.event.CommentPublished;
import dev.patra.interaction.api.post.event.PostDeleted;
import dev.patra.interaction.api.reaction.event.ExperienceOutcome;
import dev.patra.interaction.api.reaction.event.HelpfulMarked;
import dev.patra.interaction.api.subject.event.SubjectContext;
import dev.patra.interaction.domain.discussion.event.CommentDeletedEvent;
import dev.patra.interaction.domain.discussion.event.CommentEditedEvent;
import dev.patra.interaction.domain.discussion.event.CommentPublishedEvent;
import dev.patra.interaction.domain.post.event.PostDeletedEvent;
import dev.patra.interaction.domain.post.model.enums.FinalOutcome;
import dev.patra.interaction.domain.post.model.enums.FirstDecision;
import dev.patra.interaction.domain.post.model.read.ExperienceOutline;
import dev.patra.interaction.domain.reaction.event.HelpfulMarkedEvent;
import dev.patra.interaction.domain.reaction.model.enums.HelpfulTargetType;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/// 领域事件 → 集成事件（PAP-141）：只拷字段，事件 ID 原样，值对象拆成基本类型，枚举转常量名。
class IntegrationEventMapperTest {

  private static final UUID ID = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
  private static final Instant AT = Instant.parse("2026-10-31T01:02:03.456789Z");

  /// 评论已发表：经验下的回复（所有者匿名）。
  @Test
  void should_map_comment_published() {
    CommentPublishedEvent event =
        CommentPublishedEvent.builder()
            .eventId(ID)
            .commentId(10L)
            .version(1)
            .subjectId(SubjectId.of(100L))
            .subjectType(SubjectType.POST)
            .subjectOwner(SubjectOwner.of(7L, true))
            .rootCommentId(9L)
            .parentCommentId(9L)
            .parentAuthorId(8L)
            .authorId(7L)
            .authorAnonymous(true)
            .mentionedUserIds(List.of(5L, 6L))
            .plainText("<@5> 纯文本")
            .publishedAt(AT)
            .build();

    assertThat(IntegrationEventMapper.commentPublished(event))
        .isEqualTo(
            CommentPublished.builder()
                .eventId(ID)
                .commentId(10L)
                .version(1)
                .subject(SubjectContext.of(100L, "POST", 7L, true))
                .rootCommentId(9L)
                .parentCommentId(9L)
                .parentAuthorId(8L)
                .authorId(7L)
                .authorAnonymous(true)
                .mentionedUserIds(List.of(5L, 6L))
                .plainText("<@5> 纯文本")
                .publishedAt(AT)
                .build());
  }

  /// 文献没有所有者：`ownerId` 为空、不算匿名。
  @Test
  void should_map_publication_without_owner() {
    CommentPublishedEvent event =
        CommentPublishedEvent.builder()
            .eventId(ID)
            .commentId(10L)
            .version(1)
            .subjectId(SubjectId.of(100L))
            .subjectType(SubjectType.PUBLICATION)
            .rootCommentId(10L)
            .authorId(7L)
            .plainText("x")
            .publishedAt(AT)
            .build();

    CommentPublished mapped = IntegrationEventMapper.commentPublished(event);

    assertThat(mapped.subject()).isEqualTo(SubjectContext.of(100L, "PUBLICATION", null, false));
    assertThat(mapped.parentCommentId()).isNull();
    assertThat(mapped.parentAuthorId()).isNull();
    assertThat(mapped.mentionedUserIds()).isEmpty();
  }

  /// 评论已编辑：带新增的 @ 与改完后的版本。
  @Test
  void should_map_comment_edited() {
    CommentEditedEvent event =
        CommentEditedEvent.builder()
            .eventId(ID)
            .commentId(10L)
            .version(3)
            .subjectId(SubjectId.of(100L))
            .subjectType(SubjectType.PUBLICATION)
            .rootCommentId(10L)
            .authorId(7L)
            .authorAnonymous(false)
            .mentionedUserIds(List.of(5L, 6L))
            .addedMentionedUserIds(List.of(6L))
            .plainText("改了")
            .editedAt(AT)
            .build();

    assertThat(IntegrationEventMapper.commentEdited(event))
        .isEqualTo(
            CommentEdited.builder()
                .eventId(ID)
                .commentId(10L)
                .version(3)
                .subject(SubjectContext.of(100L, "PUBLICATION", null, false))
                .rootCommentId(10L)
                .authorId(7L)
                .authorAnonymous(false)
                .mentionedUserIds(List.of(5L, 6L))
                .addedMentionedUserIds(List.of(6L))
                .plainText("改了")
                .editedAt(AT)
                .build());
  }

  /// 评论已删除。
  @Test
  void should_map_comment_deleted() {
    CommentDeletedEvent event =
        CommentDeletedEvent.builder()
            .eventId(ID)
            .commentId(10L)
            .version(2)
            .subjectId(SubjectId.of(100L))
            .authorId(7L)
            .placeholder(true)
            .deletedAt(AT)
            .build();

    assertThat(IntegrationEventMapper.commentDeleted(event))
        .isEqualTo(
            CommentDeleted.builder()
                .eventId(ID)
                .commentId(10L)
                .version(2)
                .subjectId(100L)
                .authorId(7L)
                .placeholder(true)
                .deletedAt(AT)
                .build());
  }

  /// 投稿经验已删除。
  @Test
  void should_map_post_deleted() {
    PostDeletedEvent event =
        PostDeletedEvent.builder()
            .eventId(ID)
            .postId(500L)
            .subjectId(SubjectId.of(12L))
            .authorId(7L)
            .deletedAt(AT)
            .build();

    assertThat(IntegrationEventMapper.postDeleted(event))
        .isEqualTo(
            PostDeleted.builder()
                .eventId(ID)
                .postId(500L)
                .subjectId(12L)
                .authorId(7L)
                .deletedAt(AT)
                .build());
  }

  /// 有帮助已点经验：结果的枚举转常量名；没写正文时纯文本为空。
  @Test
  void should_map_helpful_on_post_with_outcome() {
    HelpfulMarkedEvent event =
        HelpfulMarkedEvent.builder()
            .eventId(ID)
            .targetType(HelpfulTargetType.POST)
            .targetId(500L)
            .targetAuthorId(7L)
            .targetAnonymous(true)
            .userId(8L)
            .markedAt(AT)
            .subjectId(SubjectId.of(12L))
            .subjectType(SubjectType.POST)
            .subjectOwner(SubjectOwner.of(7L, true))
            .experience(
                ExperienceOutline.of(
                    FirstDecision.MAJOR_REVISION, FinalOutcome.IN_PROGRESS, 10, null))
            .targetPlainText(null)
            .build();

    assertThat(IntegrationEventMapper.helpfulMarked(event))
        .isEqualTo(
            HelpfulMarked.builder()
                .eventId(ID)
                .targetType("POST")
                .targetId(500L)
                .targetAuthorId(7L)
                .targetAnonymous(true)
                .userId(8L)
                .markedAt(AT)
                .subject(SubjectContext.of(12L, "POST", 7L, true))
                .experience(ExperienceOutcome.of("MAJOR_REVISION", "IN_PROGRESS", 10, null))
                .targetPlainText(null)
                .build());
  }

  /// 有帮助已点评论：没有经验结果。
  @Test
  void should_map_helpful_on_comment_without_experience() {
    HelpfulMarkedEvent event =
        HelpfulMarkedEvent.builder()
            .eventId(ID)
            .targetType(HelpfulTargetType.COMMENT)
            .targetId(42L)
            .targetAuthorId(7L)
            .userId(8L)
            .markedAt(AT)
            .subjectId(SubjectId.of(100L))
            .subjectType(SubjectType.PUBLICATION)
            .targetPlainText("评论")
            .build();

    HelpfulMarked mapped = IntegrationEventMapper.helpfulMarked(event);

    assertThat(mapped.targetType()).isEqualTo("COMMENT");
    assertThat(mapped.experience()).isNull();
    assertThat(mapped.subject()).isEqualTo(SubjectContext.of(100L, "PUBLICATION", null, false));
    assertThat(mapped.targetPlainText()).isEqualTo("评论");
  }
}
