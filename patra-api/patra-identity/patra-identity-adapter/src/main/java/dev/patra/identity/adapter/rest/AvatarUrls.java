package dev.patra.identity.adapter.rest;

import dev.patra.identity.domain.model.vo.avatar.Avatar;

/// 响应里的 `avatarUrl`：identity 自己的相对路径，带版本、不带尺寸和基地址。门户换成自己的同源地址并追加
/// `size=128|512`（决策 G）。
public final class AvatarUrls {

  private AvatarUrls() {}

  /// 拼头像地址。
  ///
  /// @param username 当前用户名
  /// @param avatar 头像，可为 `null`
  /// @return `/users/{username}/avatar?v={hash}`；没有头像时为 `null`
  public static String of(String username, Avatar avatar) {
    if (avatar == null) {
      return null;
    }
    return "/users/" + username + "/avatar?v=" + avatar.hash();
  }
}
