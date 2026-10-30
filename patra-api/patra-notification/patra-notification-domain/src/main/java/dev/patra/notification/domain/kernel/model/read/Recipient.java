package dev.patra.notification.domain.kernel.model.read;

import dev.patra.notification.domain.kernel.model.enums.AccountState;
import java.util.Objects;
import lombok.Builder;

/// 一个用户的收件信息，发送或显示的那一刻向 identity 现查，notification 不存副本（决策 F）。
///
/// @param userId 用户 ID
/// @param email 账号邮箱；已注销为 `null`
/// @param emailVerified 邮箱是否已验证；已注销为 `false`
/// @param state 账号状态
/// @param username 用户名；已注销为 `null`
/// @param displayName 昵称，没设时已回退成用户名；已注销为 `null`
@Builder
public record Recipient(
    long userId,
    String email,
    boolean emailVerified,
    AccountState state,
    String username,
    String displayName) {

  /// 校验非空。
  public Recipient {
    Objects.requireNonNull(state, "state 不能为 null");
  }
}
