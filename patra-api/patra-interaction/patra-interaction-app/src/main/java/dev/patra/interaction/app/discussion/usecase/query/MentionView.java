package dev.patra.interaction.app.discussion.usecase.query;

/// 正文里一个提及标记对应的人。门户据此把 `<@用户ID>` 渲染成 `@用户名`（链主页）、`@昵称`（不链接）或 `@已注销用户`。
///
/// @param userId 用户 ID
/// @param username 当前用户名；主页对访客不可见或已注销时为 `null`
/// @param displayName 昵称（没设时就是用户名）；已注销时为 `null`
/// @param deleted 是否已注销（identity 省略的也算）
public record MentionView(long userId, String username, String displayName, boolean deleted) {

  /// 创建。
  ///
  /// @param userId 用户 ID
  /// @param username 用户名
  /// @param displayName 昵称
  /// @param deleted 是否已注销
  /// @return 提及
  public static MentionView of(long userId, String username, String displayName, boolean deleted) {
    return new MentionView(userId, username, displayName, deleted);
  }
}
