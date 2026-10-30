package dev.patra.notification.adapter.inbox.rest.response;

import dev.patra.notification.domain.kernel.model.read.UserCard;
import lombok.Builder;

/// 触发人名片，与讨论、投稿经验的作者对象同形。主页对收件人不可见时不给用户名与头像。
///
/// @param userId 用户 ID
/// @param username 用户名；主页不可见时为空
/// @param displayName 昵称
/// @param avatarUrl 头像地址；主页不可见或没传头像时为空
/// @param avatarPalette 默认头像色号
/// @param state 账号状态
/// @param profileVisible 主页是否对收件人可见
@Builder
public record ActorResponse(
    Long userId,
    String username,
    String displayName,
    String avatarUrl,
    int avatarPalette,
    String state,
    boolean profileVisible) {

  /// 转换。
  ///
  /// @param card 名片，可空
  /// @return 响应，可空
  public static ActorResponse from(UserCard card) {
    if (card == null) {
      return null;
    }
    return ActorResponse.builder()
        .userId(card.userId())
        .username(card.profileVisible() ? card.username() : null)
        .displayName(card.displayName())
        .avatarUrl(card.profileVisible() ? card.avatarUrl() : null)
        .avatarPalette(card.avatarPalette())
        .state(card.state().name())
        .profileVisible(card.profileVisible())
        .build();
  }
}
