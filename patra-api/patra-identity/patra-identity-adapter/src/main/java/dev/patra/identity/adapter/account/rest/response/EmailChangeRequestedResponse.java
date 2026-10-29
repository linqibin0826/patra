package dev.patra.identity.adapter.account.rest.response;

import dev.patra.identity.app.account.usecase.emailchange.EmailChangeRequested;
import java.time.Instant;

/// 改邮箱申请的响应体。
///
/// @param newEmail 规范化之后的新邮箱
/// @param expiresAt 确认链接的过期时间
public record EmailChangeRequestedResponse(String newEmail, Instant expiresAt) {

  /// 从用例结果映射。
  ///
  /// @param result 用例结果
  /// @return 响应
  public static EmailChangeRequestedResponse from(EmailChangeRequested result) {
    return new EmailChangeRequestedResponse(result.newEmail(), result.expiresAt());
  }
}
