package dev.patra.interaction.domain.discussion.model.vo;

import dev.patra.interaction.domain.discussion.event.CommentEditedEvent;
import dev.patra.interaction.domain.discussion.model.aggregate.CommentAggregate;
import dev.patra.interaction.domain.discussion.model.read.RevisionRecord;
import dev.patra.interaction.domain.subject.model.read.SubjectView;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/// 一次编辑的结果。
///
/// @param updated 改完的评论
/// @param archived 要存进修改历史的旧版本；发布 5 分钟内的修改不记版本，为 `null`
/// @param addedMentionedUserIds 这条评论第一次 @ 到的人（提醒只发给他们）
public record CommentEdit(
    CommentAggregate updated, RevisionRecord archived, List<Long> addedMentionedUserIds) {

  /// 防御性拷贝。
  public CommentEdit {
    addedMentionedUserIds = List.copyOf(addedMentionedUserIds);
  }

  /// 创建。
  ///
  /// @param updated 改完的评论
  /// @param archived 要存的旧版本，可空
  /// @param addedMentionedUserIds 新增的提及
  /// @return 结果
  public static CommentEdit of(
      CommentAggregate updated, RevisionRecord archived, List<Long> addedMentionedUserIds) {
    return new CommentEdit(updated, archived, addedMentionedUserIds);
  }

  /// 「评论已编辑」事件。
  ///
  /// @param subject 所在对象
  /// @param parentAuthorId 编辑时父评论的作者，父评论已成占位时为 `null`
  /// @param mentionedUserIds 编辑后正文 @ 到的人（`updated` 里存的是曾经 @ 过的全部，不能代替）
  /// @param plainText 编辑后的正文转成的全文纯文本
  /// @param editedAt 编辑时刻
  /// @return 事件
  public CommentEditedEvent editedEvent(
      SubjectView subject,
      Long parentAuthorId,
      List<Long> mentionedUserIds,
      String plainText,
      Instant editedAt) {
    return CommentEditedEvent.builder()
        .eventId(UUID.randomUUID())
        .commentId(updated.getCommentId())
        .version(updated.getVersion())
        .subjectId(subject.subjectId())
        .subjectType(subject.type())
        .subjectOwner(subject.owner())
        .rootCommentId(updated.getRootId())
        .parentCommentId(updated.getParentId())
        .parentAuthorId(parentAuthorId)
        .authorId(updated.getAuthorId())
        .authorAnonymous(subject.isAnonymousOwner(updated.getAuthorId()))
        .mentionedUserIds(mentionedUserIds)
        .addedMentionedUserIds(addedMentionedUserIds)
        .plainText(plainText)
        .editedAt(editedAt)
        .build();
  }
}
