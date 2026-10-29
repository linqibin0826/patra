package dev.patra.identity.app.account.usecase.password;

import java.time.Instant;
import java.util.Objects;

/// 找回密码申请的结果：已注册与未注册的邮箱返回同样的结构。
///
/// @param email 规范化之后的邮箱
/// @param nextRequestAt 下次最早可以再申请的时间
public record PasswordResetRequested(String email, Instant nextRequestAt) {

  /// 校验非空。
  public PasswordResetRequested {
    Objects.requireNonNull(email, "email 不能为 null");
    Objects.requireNonNull(nextRequestAt, "nextRequestAt 不能为 null");
  }

  /// 创建结果。
  ///
  /// @param email 邮箱
  /// @param nextRequestAt 下次可申请时间
  /// @return 结果
  public static PasswordResetRequested of(String email, Instant nextRequestAt) {
    return new PasswordResetRequested(email, nextRequestAt);
  }
}
