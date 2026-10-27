package dev.patra.interaction.domain.discussion.model.vo;

import dev.patra.interaction.domain.discussion.model.aggregate.CommentAggregate;
import dev.patra.interaction.domain.discussion.model.read.RevisionRecord;
import java.util.List;

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
}
