package dev.patra.identity.adapter.account.security;

/// 平台回了 2xx，但 JSON 里是错误而不是令牌：GitHub 拒绝授权码时就这样。
///
/// 框架把它和连不上、响应读不了都归成 `invalid_token_response`，失败处理器顺着原因链认出这个类型，才能分开：
/// 这是授权没成功，不是平台不可用。
public final class ProviderTokenRefusedException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /// 创建异常。
  ///
  /// @param error 平台给的错误码，如 `bad_verification_code`
  public ProviderTokenRefusedException(String error) {
    super("平台拒绝了换令牌请求: " + error);
  }
}
