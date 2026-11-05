package dev.patra.identity.app.account.usecase.oauth;

import java.time.Instant;

/// 验证码已发出。
///
/// @param email 规范化之后的邮箱
/// @param codeExpiresAt 验证码的过期时间
/// @param nextResendAt 最早可以重发的时间
public record RegistrationCodeSent(String email, Instant codeExpiresAt, Instant nextResendAt) {

  /// 创建结果。
  ///
  /// @param email 规范化之后的邮箱
  /// @param codeExpiresAt 验证码的过期时间
  /// @param nextResendAt 最早可以重发的时间
  /// @return 结果
  public static RegistrationCodeSent of(String email, Instant codeExpiresAt, Instant nextResendAt) {
    return new RegistrationCodeSent(email, codeExpiresAt, nextResendAt);
  }
}
