package dev.patra.interaction.adapter.discussion.rest.response;

import dev.patra.interaction.app.discussion.usecase.query.ReplyToView;

/// 「回复 @X」的对象。
///
/// @param deleted 被回复的那条已删除（「回复已删除的评论」）
/// @param anonymous 被回复的是匿名作者（「回复 匿名作者」）
/// @param author 被回复者；已删除、匿名或已注销时为 `null`
public record ReplyToResponse(boolean deleted, boolean anonymous, AuthorResponse author) {

  /// 从视图转换。
  ///
  /// @param view 视图，可空
  /// @return 响应；`view` 为空时为 `null`
  public static ReplyToResponse from(ReplyToView view) {
    return view == null
        ? null
        : new ReplyToResponse(view.deleted(), view.anonymous(), AuthorResponse.from(view.author()));
  }
}
