package dev.linqibin.patra.identity.adapter.rest.auth.response;

/// 注册、登录成功的响应体。`userId` 按全局 Jackson 设置输出成字符串。
///
/// @param sessionToken 会话令牌，客户端原样保存，之后放进 `Authorization: Bearer`
/// @param userId 用户 ID
/// @param email 规范化之后的邮箱
public record AuthenticatedUserResponse(String sessionToken, Long userId, String email) {

  /// 创建响应。
  ///
  /// @param sessionToken 会话令牌
  /// @param userId 用户 ID
  /// @param email 邮箱
  /// @return 响应
  public static AuthenticatedUserResponse of(String sessionToken, long userId, String email) {
    return new AuthenticatedUserResponse(sessionToken, userId, email);
  }

  /// 不输出令牌。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "AuthenticatedUserResponse[sessionToken=***, userId="
        + userId
        + ", email="
        + email
        + "]";
  }
}
