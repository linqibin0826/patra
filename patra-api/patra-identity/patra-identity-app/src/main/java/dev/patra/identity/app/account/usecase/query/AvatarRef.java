package dev.patra.identity.app.account.usecase.query;

import dev.patra.identity.domain.account.model.vo.avatar.Avatar;
import dev.patra.identity.domain.account.model.vo.avatar.AvatarSize;

/// 一次头像读取命中的对象：哪张头像、哪一档。
///
/// @param avatar 头像
/// @param size 尺寸
public record AvatarRef(Avatar avatar, AvatarSize size) {

  /// 工厂方法。
  ///
  /// @param avatar 头像
  /// @param size 尺寸
  /// @return 命中
  public static AvatarRef of(Avatar avatar, AvatarSize size) {
    return new AvatarRef(avatar, size);
  }

  /// 这一档的强 ETag，带引号。
  ///
  /// @return ETag
  public String etag() {
    return avatar.etag(size);
  }
}
