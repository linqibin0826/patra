package dev.patra.identity.adapter.account.rest.request;

/// 找回密码申请的请求体。
///
/// @param email 邮箱
/// @param verificationToken 人机验证令牌，可以没有
public record ForgotPasswordRequest(String email, String verificationToken) {

  /// 不输出人机验证令牌。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "ForgotPasswordRequest[email=" + email + "]";
  }
}
