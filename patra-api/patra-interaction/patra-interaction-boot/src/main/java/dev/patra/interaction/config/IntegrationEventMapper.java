package dev.patra.interaction.config;

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
import dev.patra.interaction.domain.post.model.read.ExperienceOutline;
import dev.patra.interaction.domain.reaction.event.HelpfulMarkedEvent;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.interaction.domain.subject.model.vo.SubjectId;
import dev.patra.interaction.domain.subject.model.vo.SubjectOwner;

/// 领域事件 → 集成事件（PAP-141）。只拷字段：值对象拆成基本类型，枚举转常量名。
///
/// 不查库、不调转换器：映射在每次外送（含补投）时执行，查库会让补投的结果随时间变化，抛异常会让这条事件永远发不出去。
/// 事件 ID 原样带过去（补投时不变，消费方据此去重）。
final class IntegrationEventMapper {

  /// 只有静态方法。
  private IntegrationEventMapper() {}

  /// 评论已发表。
  ///
  /// @param event 领域事件
  /// @return 集成事件
  static CommentPublished commentPublished(CommentPublishedEvent event) {
    return CommentPublished.builder()
        .eventId(event.eventId())
        .commentId(event.commentId())
        .version(event.version())
        .subject(subject(event.subjectId(), event.subjectType(), event.subjectOwner()))
        .rootCommentId(event.rootCommentId())
        .parentCommentId(event.parentCommentId())
        .parentAuthorId(event.parentAuthorId())
        .authorId(event.authorId())
        .authorAnonymous(event.authorAnonymous())
        .mentionedUserIds(event.mentionedUserIds())
        .plainText(event.plainText())
        .publishedAt(event.publishedAt())
        .build();
  }

  /// 评论已编辑。
  ///
  /// @param event 领域事件
  /// @return 集成事件
  static CommentEdited commentEdited(CommentEditedEvent event) {
    return CommentEdited.builder()
        .eventId(event.eventId())
        .commentId(event.commentId())
        .version(event.version())
        .subject(subject(event.subjectId(), event.subjectType(), event.subjectOwner()))
        .rootCommentId(event.rootCommentId())
        .parentCommentId(event.parentCommentId())
        .parentAuthorId(event.parentAuthorId())
        .authorId(event.authorId())
        .authorAnonymous(event.authorAnonymous())
        .mentionedUserIds(event.mentionedUserIds())
        .addedMentionedUserIds(event.addedMentionedUserIds())
        .plainText(event.plainText())
        .editedAt(event.editedAt())
        .build();
  }

  /// 评论已删除。
  ///
  /// @param event 领域事件
  /// @return 集成事件
  static CommentDeleted commentDeleted(CommentDeletedEvent event) {
    return CommentDeleted.builder()
        .eventId(event.eventId())
        .commentId(event.commentId())
        .version(event.version())
        .subjectId(event.subjectId().value())
        .authorId(event.authorId())
        .placeholder(event.placeholder())
        .deletedAt(event.deletedAt())
        .build();
  }

  /// 投稿经验已删除。
  ///
  /// @param event 领域事件
  /// @return 集成事件
  static PostDeleted postDeleted(PostDeletedEvent event) {
    return PostDeleted.builder()
        .eventId(event.eventId())
        .postId(event.postId())
        .subjectId(event.subjectId().value())
        .authorId(event.authorId())
        .deletedAt(event.deletedAt())
        .build();
  }

  /// 有帮助已点。
  ///
  /// @param event 领域事件
  /// @return 集成事件
  static HelpfulMarked helpfulMarked(HelpfulMarkedEvent event) {
    return HelpfulMarked.builder()
        .eventId(event.eventId())
        .targetType(event.targetType().name())
        .targetId(event.targetId())
        .targetAuthorId(event.targetAuthorId())
        .targetAnonymous(event.targetAnonymous())
        .userId(event.userId())
        .markedAt(event.markedAt())
        .subject(subject(event.subjectId(), event.subjectType(), event.subjectOwner()))
        .experience(experience(event.experience()))
        .targetPlainText(event.targetPlainText())
        .build();
  }

  /// 对象：本地 ID、种类、所有者与是否匿名（文献没有所有者）。
  ///
  /// @param subjectId 本地 ID
  /// @param type 种类
  /// @param owner 所有者，可空
  /// @return 对象
  private static SubjectContext subject(SubjectId subjectId, SubjectType type, SubjectOwner owner) {
    return SubjectContext.of(
        subjectId.value(),
        type.name(),
        owner == null ? null : owner.userId(),
        owner != null && owner.anonymous());
  }

  /// 经验的结果，枚举转常量名。
  ///
  /// @param outline 结果，可空
  /// @return 结果；入参为 `null` 时为 `null`
  private static ExperienceOutcome experience(ExperienceOutline outline) {
    if (outline == null) {
      return null;
    }
    return ExperienceOutcome.of(
        outline.firstDecision().name(),
        outline.finalOutcome().name(),
        outline.firstRoundDays(),
        outline.totalDays());
  }
}
