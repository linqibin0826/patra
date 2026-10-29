package dev.patra.identity.adapter.account.rest.response;

import dev.patra.identity.app.account.usecase.password.PasswordResetRequested;
import java.time.Instant;

/// 找回密码申请的响应体，注册与否一样。
///
/// @param email 规范化之后的邮箱
/// @param nextRequestAt 下次最早可以再申请的时间
public record PasswordResetRequestedResponse(String email, Instant nextRequestAt) {

  /// 从用例结果映射。
  ///
  /// @param result 用例结果
  /// @return 响应
  public static PasswordResetRequestedResponse from(PasswordResetRequested result) {
    return new PasswordResetRequestedResponse(result.email(), result.nextRequestAt());
  }
}
