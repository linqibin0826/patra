package dev.patra.identity.app.account.usecase.query;

import java.time.Instant;
import java.util.Objects;

/// 待确认的改邮箱：一条未过期的确认令牌。
///
/// @param newEmail 等待确认的新邮箱
/// @param expiresAt 确认链接的过期时间
public record PendingEmailChange(String newEmail, Instant expiresAt) {

  /// 校验非空。
  public PendingEmailChange {
    Objects.requireNonNull(newEmail, "newEmail 不能为 null");
    Objects.requireNonNull(expiresAt, "expiresAt 不能为 null");
  }

  /// 创建。
  ///
  /// @param newEmail 新邮箱
  /// @param expiresAt 过期时间
  /// @return 结果
  public static PendingEmailChange of(String newEmail, Instant expiresAt) {
    return new PendingEmailChange(newEmail, expiresAt);
  }
}
