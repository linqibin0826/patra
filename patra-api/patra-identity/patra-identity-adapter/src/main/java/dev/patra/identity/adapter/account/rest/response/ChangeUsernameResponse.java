package dev.patra.identity.adapter.account.rest.response;

import java.time.Instant;

/// 改用户名的响应体。
///
/// @param username 规范化之后的新用户名
/// @param nextUsernameChangeAt 下次可以改名的时间
public record ChangeUsernameResponse(String username, Instant nextUsernameChangeAt) {

  /// 创建响应。
  ///
  /// @param username 新用户名
  /// @param nextUsernameChangeAt 下次可以改名的时间
  /// @return 响应
  public static ChangeUsernameResponse of(String username, Instant nextUsernameChangeAt) {
    return new ChangeUsernameResponse(username, nextUsernameChangeAt);
  }
}
