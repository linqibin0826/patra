package dev.patra.identity.domain.account.policy;

/// 默认头像的色号（0–11），门户按它从 12 色板取底色。按用户 ID 算，改昵称、改用户名都不变色。
///
/// **上线后不可改**：算法一变，所有人的默认头像都会变色。用户 ID 是雪花式长整数，低位随序列号、
/// 高位随时间变化，直接取模会偏，所以先用 SplitMix64 的最终混合打散再取模。
public final class AvatarPalette {

  /// 色板有几种颜色。
  public static final int SIZE = 12;

  private AvatarPalette() {}

  /// 用户的默认头像色号。
  ///
  /// @param userId 用户 ID
  /// @return 0–11
  public static int of(long userId) {
    long z = userId + 0x9E3779B97F4A7C15L;
    z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
    z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
    z = z ^ (z >>> 31);
    return (int) Long.remainderUnsigned(z, SIZE);
  }
}
