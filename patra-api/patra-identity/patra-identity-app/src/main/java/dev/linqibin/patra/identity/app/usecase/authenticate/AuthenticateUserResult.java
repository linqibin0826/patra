package dev.linqibin.patra.identity.app.usecase.authenticate;

/// 凭据校验通过后的结果。
///
/// @param userId 用户 ID
/// @param email 规范化之后的邮箱
public record AuthenticateUserResult(long userId, String email) {

  /// 创建结果。
  ///
  /// @param userId 用户 ID
  /// @param email 邮箱
  /// @return 结果
  public static AuthenticateUserResult of(long userId, String email) {
    return new AuthenticateUserResult(userId, email);
  }
}
