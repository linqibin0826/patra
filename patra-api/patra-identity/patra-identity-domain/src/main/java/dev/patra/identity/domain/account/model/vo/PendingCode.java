package dev.patra.identity.domain.account.model.vo;

import dev.patra.identity.domain.account.model.enums.RegistrationCodePurpose;
import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/// 发出去还没用掉的验证码的公开部分：补填页刷新后靠它回到输验证码这一步，重发也要用用户名和邮箱。
///
/// @param purpose 这个码是拿来做什么的
/// @param username 用户填的用户名，已规范化；绑定用途没有用户名，为 `null`
/// @param email 用户填的邮箱，已规范化
/// @param codeExpiresAt 验证码的过期时间
/// @param nextResendAt 最早可以重发的时间
@Builder
public record PendingCode(
    RegistrationCodePurpose purpose,
    String username,
    String email,
    Instant codeExpiresAt,
    Instant nextResendAt) {

  /// 除用户名外的字段都不能为 `null`；注册用途必须有用户名。
  public PendingCode {
    Objects.requireNonNull(purpose, "purpose 不能为 null");
    Objects.requireNonNull(email, "email 不能为 null");
    Objects.requireNonNull(codeExpiresAt, "codeExpiresAt 不能为 null");
    Objects.requireNonNull(nextResendAt, "nextResendAt 不能为 null");
    if (purpose == RegistrationCodePurpose.REGISTER) {
      Objects.requireNonNull(username, "注册用途的 username 不能为 null");
    }
  }
}
