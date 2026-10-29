package dev.patra.identity.app.account.usecase.email;

import java.time.Instant;
import java.util.Objects;

/// 验证邮箱的结果。
///
/// @param email 验证的邮箱
/// @param emailVerifiedAt 邮箱验证时间；之前已验证过时是第一次的时间
public record EmailVerified(String email, Instant emailVerifiedAt) {

  /// 两项都不能为 `null`。
  public EmailVerified {
    Objects.requireNonNull(email, "email 不能为 null");
    Objects.requireNonNull(emailVerifiedAt, "emailVerifiedAt 不能为 null");
  }

  /// 创建结果。
  ///
  /// @param email 验证的邮箱
  /// @param emailVerifiedAt 邮箱验证时间
  /// @return 结果
  public static EmailVerified of(String email, Instant emailVerifiedAt) {
    return new EmailVerified(email, emailVerifiedAt);
  }
}
