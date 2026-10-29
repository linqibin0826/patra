package dev.patra.identity.adapter.account.rest.response;

import dev.patra.identity.app.account.usecase.email.EmailVerificationResent;
import java.time.Instant;

/// 重发验证邮件的响应体。
///
/// @param email 收件邮箱
/// @param nextResendAt 下次最早可以重发的时间
public record ResendEmailVerificationResponse(String email, Instant nextResendAt) {

  /// 从用例结果映射。
  ///
  /// @param result 用例结果
  /// @return 响应
  public static ResendEmailVerificationResponse from(EmailVerificationResent result) {
    return new ResendEmailVerificationResponse(result.email(), result.nextResendAt());
  }
}
