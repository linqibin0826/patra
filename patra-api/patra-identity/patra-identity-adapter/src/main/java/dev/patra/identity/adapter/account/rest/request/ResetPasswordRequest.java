package dev.patra.identity.adapter.account.rest.request;

/// 重设密码的请求体。
///
/// @param token 邮件链接 URL 片段里的令牌原文
/// @param newPassword 新密码
public record ResetPasswordRequest(String token, String newPassword) {

  /// 不输出令牌与密码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "ResetPasswordRequest[token=***, newPassword=***]";
  }
}
