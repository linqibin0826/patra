package dev.patra.identity.domain.account.model.vo;

import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/// 发出去还没用掉的验证码的公开部分：提交时的用户名和邮箱绑在码上，下一步不能换。
///
/// @param username 用户填的用户名，已规范化
/// @param email 用户填的邮箱，已规范化
/// @param codeExpiresAt 验证码的过期时间
/// @param nextResendAt 最早可以重发的时间
@Builder
public record PendingCode(
    String username, String email, Instant codeExpiresAt, Instant nextResendAt) {

  /// 字段都不能为 `null`。
  public PendingCode {
    Objects.requireNonNull(username, "username 不能为 null");
    Objects.requireNonNull(email, "email 不能为 null");
    Objects.requireNonNull(codeExpiresAt, "codeExpiresAt 不能为 null");
    Objects.requireNonNull(nextResendAt, "nextResendAt 不能为 null");
  }
}
