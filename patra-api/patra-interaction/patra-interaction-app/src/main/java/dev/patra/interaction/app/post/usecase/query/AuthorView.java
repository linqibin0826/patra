package dev.patra.interaction.app.post.usecase.query;

import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import lombok.Builder;

/// 经验作者的名片。主页对访客不可见时不带用户名与头像（v0.13 名片规则，2026-10-27 用户确认讨论区同样做）。
///
/// @param userId 用户 ID
/// @param username 用户名，主页不可见或已注销时为 `null`
/// @param displayName 昵称，已注销时为 `null`
/// @param avatarUrl 头像地址，主页不可见、已注销或没传头像时为 `null`
/// @param avatarPalette 默认头像色号
/// @param state 账号状态
/// @param profileVisible 主页对访客是否可见
@Builder
public record AuthorView(
    long userId,
    String username,
    String displayName,
    String avatarUrl,
    int avatarPalette,
    AccountState state,
    boolean profileVisible) {

  /// 从名片转换，主页不可见时去掉用户名与头像。
  ///
  /// @param card 名片
  /// @return 作者
  public static AuthorView of(UserCard card) {
    boolean visible = card.profileVisible();
    return AuthorView.builder()
        .userId(card.userId())
        .username(visible ? card.username() : null)
        .displayName(card.displayName())
        .avatarUrl(visible ? card.avatarUrl() : null)
        .avatarPalette(card.avatarPalette())
        .state(card.state())
        .profileVisible(visible)
        .build();
  }
}
