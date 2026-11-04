package dev.patra.identity.adapter.account.rest.response;

import dev.patra.identity.app.account.usecase.oauth.RegistrationCodeSent;
import java.time.Instant;

/// 验证码已发出的响应体。
///
/// @param email 规范化之后的邮箱
/// @param codeExpiresAt 验证码的过期时间
/// @param nextResendAt 最早可以重发的时间
public record RegistrationCodeSentResponse(
    String email, Instant codeExpiresAt, Instant nextResendAt) {

  /// 从用例结果映射。
  ///
  /// @param result 用例结果
  /// @return 响应
  public static RegistrationCodeSentResponse from(RegistrationCodeSent result) {
    return new RegistrationCodeSentResponse(
        result.email(), result.codeExpiresAt(), result.nextResendAt());
  }
}
