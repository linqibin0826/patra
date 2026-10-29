package dev.patra.identity.adapter.account.rest.request;

/// 确认改邮箱的请求体。
///
/// @param token 邮件链接 URL 片段里的令牌原文
public record ConfirmEmailChangeRequest(String token) {

  /// 不输出令牌。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "ConfirmEmailChangeRequest[token=***]";
  }
}
