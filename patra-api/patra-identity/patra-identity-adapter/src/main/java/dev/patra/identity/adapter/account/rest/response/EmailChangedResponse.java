package dev.patra.identity.adapter.account.rest.response;

import dev.patra.identity.app.account.usecase.emailchange.EmailChanged;
import java.time.Instant;

/// 确认改邮箱的响应体。
///
/// @param email 新邮箱
/// @param emailVerifiedAt 验证时间
public record EmailChangedResponse(String email, Instant emailVerifiedAt) {

  /// 从用例结果映射。
  ///
  /// @param result 用例结果
  /// @return 响应
  public static EmailChangedResponse from(EmailChanged result) {
    return new EmailChangedResponse(result.email(), result.emailVerifiedAt());
  }
}
