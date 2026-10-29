package dev.patra.identity.adapter.account.rest.response;

import dev.patra.identity.app.account.usecase.email.EmailVerified;
import java.time.Instant;

/// 验证邮箱的响应体。
///
/// @param email 验证的邮箱
/// @param emailVerifiedAt 邮箱验证时间
public record VerifyEmailResponse(String email, Instant emailVerifiedAt) {

  /// 从用例结果映射。
  ///
  /// @param result 用例结果
  /// @return 响应
  public static VerifyEmailResponse from(EmailVerified result) {
    return new VerifyEmailResponse(result.email(), result.emailVerifiedAt());
  }
}
