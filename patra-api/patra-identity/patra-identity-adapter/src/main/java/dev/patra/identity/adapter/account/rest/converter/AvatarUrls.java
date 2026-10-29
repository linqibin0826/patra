package dev.patra.identity.adapter.account.rest.converter;

import dev.patra.identity.domain.account.model.vo.avatar.Avatar;

/// 响应里的 `avatarUrl`：identity 自己的相对路径，按用户 ID（改用户名后不变，PAP-108），带版本、不带尺寸和
/// 基地址。门户换成自己的同源地址并追加 `size=128|512`（v0.12 决策 G）。
public final class AvatarUrls {

  private AvatarUrls() {}

  /// 拼头像地址。
  ///
  /// @param avatar 头像，可为 `null`
  /// @return `/users/{userId}/avatar?v={hash}`；没有头像时为 `null`
  public static String of(Avatar avatar) {
    if (avatar == null) {
      return null;
    }
    return "/users/" + avatar.userId() + "/avatar?v=" + avatar.hash();
  }
}
