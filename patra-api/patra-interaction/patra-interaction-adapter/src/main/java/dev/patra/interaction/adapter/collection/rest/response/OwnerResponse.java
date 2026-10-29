package dev.patra.interaction.adapter.collection.rest.response;

import dev.patra.interaction.domain.subject.model.enums.AccountState;
import dev.patra.interaction.domain.subject.model.read.UserCard;
import lombok.Builder;

/// 收藏夹所有者（来自 identity 的摘要）。已注销时 `username`、`displayName`、`avatarUrl` 为 `null`，门户显示「已注销
/// 用户」。
///
/// @param userId 用户 ID
/// @param username 用户名，只用来拼主页链接
/// @param displayName 昵称
/// @param avatarUrl 头像地址，可空
/// @param avatarPalette 默认头像色号（0–11）
/// @param deleted 是否已注销
@Builder
public record OwnerResponse(
    Long userId,
    String username,
    String displayName,
    String avatarUrl,
    int avatarPalette,
    boolean deleted) {

  /// 从摘要转换。
  ///
  /// @param card 摘要
  /// @return 响应
  public static OwnerResponse from(UserCard card) {
    return OwnerResponse.builder()
        .userId(card.userId())
        .username(card.username())
        .displayName(card.displayName())
        .avatarUrl(card.avatarUrl())
        .avatarPalette(card.avatarPalette())
        .deleted(card.state() == AccountState.DELETED)
        .build();
  }
}
