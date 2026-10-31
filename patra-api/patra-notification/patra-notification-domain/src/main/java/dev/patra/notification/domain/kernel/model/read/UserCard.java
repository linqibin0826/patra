package dev.patra.notification.domain.kernel.model.read;

import dev.patra.notification.domain.kernel.model.enums.AccountState;
import java.util.Objects;
import lombok.Builder;

/// identity 替某位访客给出的一个用户的名片（`/_internal/users/summaries`），显示的那一刻现查，不存副本。
///
/// `profileVisible` 为假时不得把这个人的 `username`、`avatarUrl` 放进给这位访客的响应。已注销的是占位：
/// `username`、`displayName`、`avatarUrl` 为 `null`。
///
/// @param userId 用户 ID
/// @param username 用户名，只用来拼主页链接；已注销为 `null`
/// @param displayName 昵称，没设时已回退成用户名；已注销为 `null`
/// @param avatarUrl 头像地址，可空
/// @param avatarPalette 默认头像色号（0–11）
/// @param state 账号状态
/// @param profileVisible identity 替访客算出的「主页是否可见」
@Builder
public record UserCard(
    long userId,
    String username,
    String displayName,
    String avatarUrl,
    int avatarPalette,
    AccountState state,
    boolean profileVisible) {

  /// 校验非空。
  public UserCard {
    Objects.requireNonNull(state, "state 不能为 null");
  }
}
