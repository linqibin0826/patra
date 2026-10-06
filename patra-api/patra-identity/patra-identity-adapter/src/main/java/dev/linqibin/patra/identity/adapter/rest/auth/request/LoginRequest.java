package dev.linqibin.patra.identity.adapter.rest.auth.request;

/// 登录请求体。
///
/// @param email 邮箱
/// @param password 密码
public record LoginRequest(String email, String password) {

  /// 不输出密码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "LoginRequest[email=" + email + ", password=***]";
  }
}
