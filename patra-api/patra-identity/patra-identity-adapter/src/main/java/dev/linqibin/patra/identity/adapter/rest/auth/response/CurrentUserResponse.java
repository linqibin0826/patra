package dev.linqibin.patra.identity.adapter.rest.auth.response;

/// 「当前用户」接口的响应体。`userId` 按全局 Jackson 设置输出成字符串。
///
/// @param userId 用户 ID
/// @param email 规范化之后的邮箱
/// @param accountType 账号类型的 `code`，本版是 `user`
public record CurrentUserResponse(Long userId, String email, String accountType) {

  /// 创建响应。
  ///
  /// @param userId 用户 ID
  /// @param email 邮箱
  /// @param accountType 账号类型的 `code`
  /// @return 响应
  public static CurrentUserResponse of(long userId, String email, String accountType) {
    return new CurrentUserResponse(userId, email, accountType);
  }
}
