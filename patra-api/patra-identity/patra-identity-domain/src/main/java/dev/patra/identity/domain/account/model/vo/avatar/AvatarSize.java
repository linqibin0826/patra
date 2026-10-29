package dev.patra.identity.domain.account.model.vo.avatar;

import java.util.Arrays;
import java.util.Optional;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/// 头像的两档尺寸：512 给主页与设置页，128 给导航栏与菜单。
@Getter
@RequiredArgsConstructor
public enum AvatarSize {
  /// 512×512。
  LARGE(512),
  /// 128×128。
  SMALL(128);

  private final int pixels;

  /// 按查询参数里的像素数取尺寸。
  ///
  /// @param raw 查询参数原值，如 `"512"`
  /// @return 尺寸；不是 `512` 或 `128` 时为空
  public static Optional<AvatarSize> fromPixels(String raw) {
    return Arrays.stream(values())
        .filter(size -> String.valueOf(size.pixels).equals(raw))
        .findFirst();
  }
}
