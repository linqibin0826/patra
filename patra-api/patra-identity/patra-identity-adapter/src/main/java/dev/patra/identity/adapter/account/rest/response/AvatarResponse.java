package dev.patra.identity.adapter.account.rest.response;

/// `POST /me/avatar` 的响应。
///
/// @param avatarUrl 新头像地址（identity 相对路径，带版本）
public record AvatarResponse(String avatarUrl) {

  /// 工厂方法。
  ///
  /// @param avatarUrl 头像地址
  /// @return 响应
  public static AvatarResponse of(String avatarUrl) {
    return new AvatarResponse(avatarUrl);
  }
}
