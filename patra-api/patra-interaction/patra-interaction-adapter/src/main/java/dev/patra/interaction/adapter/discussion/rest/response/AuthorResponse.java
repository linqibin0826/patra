package dev.patra.interaction.adapter.discussion.rest.response;

import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import lombok.Builder;

/// 评论作者或被回复者的名片（与 PAP-122 的作者对象同形）。主页对访客不可见时没有用户名与头像地址；已注销时
/// 用户名、昵称、头像都为 `null`，门户显示「已注销用户」。
///
/// @param userId 用户 ID
/// @param username 用户名，只用来拼主页链接；不可见或已注销为 `null`
/// @param displayName 昵称（没设时就是用户名）；已注销为 `null`
/// @param avatarUrl 头像地址；不可见、没传头像、封禁、已注销为 `null`
/// @param avatarPalette 默认头像色号（0–11）
/// @param state 账号状态
/// @param profileVisible 主页是否对访客可见
@Builder
public record AuthorResponse(
    Long userId,
    String username,
    String displayName,
    String avatarUrl,
    int avatarPalette,
    AccountState state,
    boolean profileVisible) {

  /// 从名片转换。
  ///
  /// @param card 名片，可空
  /// @return 响应；`card` 为空时为 `null`
  public static AuthorResponse from(UserCard card) {
    if (card == null) {
      return null;
    }
    return AuthorResponse.builder()
        .userId(card.userId())
        .username(card.username())
        .displayName(card.displayName())
        .avatarUrl(card.avatarUrl())
        .avatarPalette(card.avatarPalette())
        .state(card.state())
        .profileVisible(card.profileVisible())
        .build();
  }
}
