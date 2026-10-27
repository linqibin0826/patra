package dev.patra.interaction.adapter.post.rest.response;

import dev.patra.interaction.app.post.usecase.query.AuthorView;
import lombok.Builder;

/// 经验作者。主页对访客不可见时 `username`、`avatarUrl` 为 `null`；已注销时 `username`、`displayName`、`avatarUrl`
/// 都为 `null`（门户显示「已注销用户」）。
///
/// @param userId 用户 ID
/// @param username 用户名
/// @param displayName 昵称
/// @param avatarUrl 头像地址
/// @param avatarPalette 默认头像色号（0–11）
/// @param state 账号状态：`ACTIVE` / `BANNED` / `PENDING_DELETION` / `DELETED`
/// @param profileVisible 主页对访客是否可见
@Builder
public record AuthorResponse(
    Long userId,
    String username,
    String displayName,
    String avatarUrl,
    int avatarPalette,
    String state,
    boolean profileVisible) {

  /// 转换。
  ///
  /// @param view 作者，可空
  /// @return 响应，可空
  public static AuthorResponse from(AuthorView view) {
    if (view == null) {
      return null;
    }
    return AuthorResponse.builder()
        .userId(view.userId())
        .username(view.username())
        .displayName(view.displayName())
        .avatarUrl(view.avatarUrl())
        .avatarPalette(view.avatarPalette())
        .state(view.state().name())
        .profileVisible(view.profileVisible())
        .build();
  }
}
