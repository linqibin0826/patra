package dev.patra.identity.app.usecase.avatar;

import dev.patra.identity.domain.model.vo.avatar.Avatar;

/// 换头像的结果：适配器据此拼出 `avatarUrl`。
///
/// @param username 当前用户名
/// @param avatar 生效的头像
public record AvatarChanged(String username, Avatar avatar) {

  /// 工厂方法。
  ///
  /// @param username 当前用户名
  /// @param avatar 头像
  /// @return 结果
  public static AvatarChanged of(String username, Avatar avatar) {
    return new AvatarChanged(username, avatar);
  }
}
