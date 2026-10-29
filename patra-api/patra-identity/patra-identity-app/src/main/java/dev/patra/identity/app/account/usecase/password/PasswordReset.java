package dev.patra.identity.app.account.usecase.password;

import java.util.Objects;

/// 重设密码的结果：不自动登录，门户据此跳登录页并预填邮箱。
///
/// @param email 重设了密码的账号邮箱
public record PasswordReset(String email) {

  /// 校验非空。
  public PasswordReset {
    Objects.requireNonNull(email, "email 不能为 null");
  }

  /// 创建结果。
  ///
  /// @param email 邮箱
  /// @return 结果
  public static PasswordReset of(String email) {
    return new PasswordReset(email);
  }
}
