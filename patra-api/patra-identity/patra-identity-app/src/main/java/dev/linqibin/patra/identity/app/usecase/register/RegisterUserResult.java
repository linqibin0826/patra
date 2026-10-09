package dev.linqibin.patra.identity.app.usecase.register;

/// 注册结果：会话令牌、用户 ID、邮箱。
///
/// @param sessionToken 会话令牌原文，只出现在响应里
/// @param userId 用户 ID
/// @param email 规范化之后的邮箱
public record RegisterUserResult(String sessionToken, long userId, String email) {

  /// 创建结果。
  ///
  /// @param sessionToken 会话令牌
  /// @param userId 用户 ID
  /// @param email 邮箱
  /// @return 结果
  public static RegisterUserResult of(String sessionToken, long userId, String email) {
    return new RegisterUserResult(sessionToken, userId, email);
  }

  /// 不输出令牌。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "RegisterUserResult[sessionToken=***, userId=" + userId + ", email=" + email + "]";
  }
}
