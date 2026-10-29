package dev.patra.identity.adapter.account.rest.response;

/// 旧名 301 的响应体：门户按它做 `permanentRedirect('/u/<username>')`，不解析 `Location`。
///
/// @param username 原主的当前用户名
public record MovedUserResponse(String username) {

  /// 创建响应。
  ///
  /// @param username 当前用户名
  /// @return 响应
  public static MovedUserResponse of(String username) {
    return new MovedUserResponse(username);
  }
}
