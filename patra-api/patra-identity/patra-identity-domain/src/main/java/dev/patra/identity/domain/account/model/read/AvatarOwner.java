package dev.patra.identity.domain.account.model.read;

import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import java.util.Objects;

/// 读头像时要的最少信息：按用户 ID 找到的用户、状态和资料里的头像。
///
/// @param userId 用户 ID
/// @param status 状态
/// @param avatar 头像，没上传时为 `null`
public record AvatarOwner(long userId, UserStatus status, Avatar avatar) {

  /// 校验非空。
  public AvatarOwner {
    Objects.requireNonNull(status, "status 不能为 null");
  }

  /// 工厂方法。
  ///
  /// @param userId 用户 ID
  /// @param status 状态
  /// @param avatar 头像，可为 `null`
  /// @return 归属
  public static AvatarOwner of(long userId, UserStatus status, Avatar avatar) {
    return new AvatarOwner(userId, status, avatar);
  }
}
