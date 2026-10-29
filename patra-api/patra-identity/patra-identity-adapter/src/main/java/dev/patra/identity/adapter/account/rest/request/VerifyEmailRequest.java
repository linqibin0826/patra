package dev.patra.identity.adapter.account.rest.request;

/// 验证邮箱的请求体。
///
/// @param token 邮件链接 URL 片段里的令牌原文
public record VerifyEmailRequest(String token) {

  /// 不输出令牌。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "VerifyEmailRequest[token=***]";
  }
}
