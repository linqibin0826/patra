package dev.patra.identity.app.account.usecase.username;

import java.time.Instant;

/// 改名结果：新用户名和下次可以改名的时间。
///
/// @param username 规范化之后的新用户名
/// @param nextUsernameChangeAt 下次可以改名的时间
public record ChangeUsernameResult(String username, Instant nextUsernameChangeAt) {

  /// 创建结果。
  ///
  /// @param username 新用户名
  /// @param nextUsernameChangeAt 下次可以改名的时间
  /// @return 结果
  public static ChangeUsernameResult of(String username, Instant nextUsernameChangeAt) {
    return new ChangeUsernameResult(username, nextUsernameChangeAt);
  }
}
