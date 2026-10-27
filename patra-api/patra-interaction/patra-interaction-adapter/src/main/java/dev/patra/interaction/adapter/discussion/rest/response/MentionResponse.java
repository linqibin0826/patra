package dev.patra.interaction.adapter.discussion.rest.response;

import dev.patra.interaction.app.discussion.usecase.query.MentionView;

/// 正文里一个 `<@用户ID>` 对应的人。
///
/// @param userId 用户 ID
/// @param username 当前用户名；主页对访客不可见或已注销时为 `null`
/// @param displayName 昵称；已注销时为 `null`
/// @param deleted 是否已注销
public record MentionResponse(Long userId, String username, String displayName, boolean deleted) {

  /// 从视图转换。
  ///
  /// @param view 视图
  /// @return 响应
  public static MentionResponse from(MentionView view) {
    return new MentionResponse(view.userId(), view.username(), view.displayName(), view.deleted());
  }
}
