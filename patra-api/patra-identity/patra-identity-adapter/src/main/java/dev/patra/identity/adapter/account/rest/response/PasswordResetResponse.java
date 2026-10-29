package dev.patra.identity.adapter.account.rest.response;

import dev.patra.identity.app.account.usecase.password.PasswordReset;

/// 重设密码的响应体：不自动登录，门户据此跳登录页并预填邮箱。
///
/// @param email 重设了密码的账号邮箱
public record PasswordResetResponse(String email) {

  /// 从用例结果映射。
  ///
  /// @param result 用例结果
  /// @return 响应
  public static PasswordResetResponse from(PasswordReset result) {
    return new PasswordResetResponse(result.email());
  }
}
