package dev.patra.identity.adapter.account.rest.response;

import dev.patra.identity.app.account.usecase.query.PendingEmailChange;
import java.time.Instant;

/// 待确认的改邮箱的响应体。
///
/// @param newEmail 等待确认的新邮箱
/// @param expiresAt 确认链接的过期时间
public record PendingEmailChangeResponse(String newEmail, Instant expiresAt) {

  /// 从查询结果映射。
  ///
  /// @param pending 查询结果
  /// @return 响应
  public static PendingEmailChangeResponse from(PendingEmailChange pending) {
    return new PendingEmailChangeResponse(pending.newEmail(), pending.expiresAt());
  }
}
