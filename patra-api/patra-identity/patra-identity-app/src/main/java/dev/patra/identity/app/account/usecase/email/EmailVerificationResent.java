package dev.patra.identity.app.account.usecase.email;

import java.time.Instant;
import java.util.Objects;

/// 重发验证邮件的结果：发到了哪个邮箱、冷却过后最早什么时候能再发。
///
/// @param email 收件邮箱
/// @param nextResendAt 下次最早可以重发的时间（只反映冷却；小时 / 天窗口满了由 429 说）
public record EmailVerificationResent(String email, Instant nextResendAt) {

  /// 两项都不能为 `null`。
  public EmailVerificationResent {
    Objects.requireNonNull(email, "email 不能为 null");
    Objects.requireNonNull(nextResendAt, "nextResendAt 不能为 null");
  }

  /// 创建结果。
  ///
  /// @param email 收件邮箱
  /// @param nextResendAt 下次最早可以重发的时间
  /// @return 结果
  public static EmailVerificationResent of(String email, Instant nextResendAt) {
    return new EmailVerificationResent(email, nextResendAt);
  }
}
