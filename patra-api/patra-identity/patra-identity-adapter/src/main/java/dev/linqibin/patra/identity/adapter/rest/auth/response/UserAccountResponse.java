package dev.linqibin.patra.identity.adapter.rest.auth.response;

/// 注册、登录成功的响应体。`userId` 按全局 Jackson 设置输出成字符串。PAP-64 再加上会话令牌。
///
/// @param userId 用户 ID
/// @param email 规范化之后的邮箱
public record UserAccountResponse(Long userId, String email) {

  /// 创建响应。
  ///
  /// @param userId 用户 ID
  /// @param email 邮箱
  /// @return 响应
  public static UserAccountResponse of(long userId, String email) {
    return new UserAccountResponse(userId, email);
  }
}
