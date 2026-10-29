package dev.patra.interaction.app.discussion.usecase.query;

import dev.patra.interaction.domain.subject.model.read.UserCard;

/// 「回复 @X」的对象：只在回复的不是根评论时给。
///
/// @param deleted 被回复的那条已是占位（「回复已删除的评论」）
/// @param anonymous 被回复的是匿名所有者（「回复 匿名作者」）
/// @param author 被回复者的名片（已遮蔽）；占位、匿名或 identity 省略时为 `null`
public record ReplyToView(boolean deleted, boolean anonymous, UserCard author) {

  /// 创建。
  ///
  /// @param deleted 是否已删除
  /// @param anonymous 是否匿名
  /// @param author 名片，可空
  /// @return 对象
  public static ReplyToView of(boolean deleted, boolean anonymous, UserCard author) {
    return new ReplyToView(deleted, anonymous, author);
  }
}
