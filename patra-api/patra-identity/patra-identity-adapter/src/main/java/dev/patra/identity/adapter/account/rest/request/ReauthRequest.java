package dev.patra.identity.adapter.account.rest.request;

/// 再认证的请求体。
///
/// @param password 密码
public record ReauthRequest(String password) {

  /// 不输出密码。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "ReauthRequest[password=***]";
  }
}
