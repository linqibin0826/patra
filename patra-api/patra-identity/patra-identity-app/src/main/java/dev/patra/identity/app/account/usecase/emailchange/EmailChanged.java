package dev.patra.identity.app.account.usecase.emailchange;

import java.time.Instant;
import java.util.Objects;

/// 改邮箱生效的结果。
///
/// @param email 新邮箱
/// @param emailVerifiedAt 验证时间（确认的那一刻）
public record EmailChanged(String email, Instant emailVerifiedAt) {

  /// 校验非空。
  public EmailChanged {
    Objects.requireNonNull(email, "email 不能为 null");
    Objects.requireNonNull(emailVerifiedAt, "emailVerifiedAt 不能为 null");
  }

  /// 创建结果。
  ///
  /// @param email 新邮箱
  /// @param emailVerifiedAt 验证时间
  /// @return 结果
  public static EmailChanged of(String email, Instant emailVerifiedAt) {
    return new EmailChanged(email, emailVerifiedAt);
  }
}
