package dev.patra.identity.app.account.usecase.emailchange;

import java.time.Instant;
import java.util.Objects;

/// 改邮箱申请的结果。
///
/// @param newEmail 规范化之后的新邮箱
/// @param expiresAt 确认链接的过期时间
public record EmailChangeRequested(String newEmail, Instant expiresAt) {

  /// 校验非空。
  public EmailChangeRequested {
    Objects.requireNonNull(newEmail, "newEmail 不能为 null");
    Objects.requireNonNull(expiresAt, "expiresAt 不能为 null");
  }

  /// 创建结果。
  ///
  /// @param newEmail 新邮箱
  /// @param expiresAt 过期时间
  /// @return 结果
  public static EmailChangeRequested of(String newEmail, Instant expiresAt) {
    return new EmailChangeRequested(newEmail, expiresAt);
  }
}
