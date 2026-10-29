package dev.patra.identity.app.account.usecase.deletion;

import java.time.Instant;
import java.util.Objects;

/// 发起注销的结果：门户的结果屏要写到期日，而这时本机会话已被撤销，取不到 `/auth/me`。
///
/// @param deletionDueAt 注销到期时间
public record AccountDeletionRequested(Instant deletionDueAt) {

  /// 到期时间不能为 `null`。
  public AccountDeletionRequested {
    Objects.requireNonNull(deletionDueAt, "deletionDueAt 不能为 null");
  }

  /// 创建结果。
  ///
  /// @param deletionDueAt 注销到期时间
  /// @return 结果
  public static AccountDeletionRequested of(Instant deletionDueAt) {
    return new AccountDeletionRequested(deletionDueAt);
  }
}
