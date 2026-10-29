package dev.patra.identity.domain.account.port.avatar;

import dev.patra.identity.domain.account.exception.InvalidAvatarException;
import dev.patra.identity.domain.account.model.vo.avatar.AvatarRenditions;

/// 头像出图：按魔数识别格式、校验尺寸与像素、居中裁方、缩放到两档并重编码为 WebP（不带任何元数据）。
public interface AvatarImagePort {

  /// 把上传的原图重出成两档。
  ///
  /// @param upload 原图字节
  /// @return 512 与 128 两档 WebP
  /// @throws InvalidAvatarException 类型不对、太小、像素过多或读不出
  AvatarRenditions render(byte[] upload);
}
