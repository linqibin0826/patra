package dev.patra.identity.api.session.dto;

/// 查会话的请求。
///
/// @param token 客户端 `Authorization: Bearer` 里的令牌原文
public record SessionIntrospectionReq(String token) {

  /// 创建请求。
  ///
  /// @param token 令牌原文
  /// @return 请求
  public static SessionIntrospectionReq of(String token) {
    return new SessionIntrospectionReq(token);
  }
}
