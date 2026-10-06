package dev.linqibin.patra.identity.app.usecase.register;

/// 注册结果。
///
/// @param userId 用户 ID
/// @param email 规范化之后的邮箱
public record RegisterUserResult(long userId, String email) {

  /// 创建结果。
  ///
  /// @param userId 用户 ID
  /// @param email 邮箱
  /// @return 结果
  public static RegisterUserResult of(long userId, String email) {
    return new RegisterUserResult(userId, email);
  }
}
