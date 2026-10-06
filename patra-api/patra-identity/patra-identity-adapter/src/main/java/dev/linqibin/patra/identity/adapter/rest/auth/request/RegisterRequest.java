package dev.linqibin.patra.identity.adapter.rest.auth.request;

/// 注册请求体。确认密码只在前端校验，不传给后端。
///
/// @param email 邮箱
/// @param password 密码
public record RegisterRequest(String email, String password) {

  /// 不输出密码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "RegisterRequest[email=" + email + ", password=***]";
  }
}
