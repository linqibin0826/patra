package dev.patra.identity.domain.account.model.vo.avatar;

import java.util.Arrays;

/// 服务端重出的两档 WebP 字节。
///
/// @param large 512×512
/// @param small 128×128
public record AvatarRenditions(byte[] large, byte[] small) {

  /// 拷贝传入的数组。
  public AvatarRenditions {
    large = large.clone();
    small = small.clone();
  }

  /// 工厂方法。
  ///
  /// @param large 512 档
  /// @param small 128 档
  /// @return 两档
  public static AvatarRenditions of(byte[] large, byte[] small) {
    return new AvatarRenditions(large, small);
  }

  /// 512 档的副本。
  ///
  /// @return 字节
  @Override
  public byte[] large() {
    return large.clone();
  }

  /// 128 档的副本。
  ///
  /// @return 字节
  @Override
  public byte[] small() {
    return small.clone();
  }

  /// 按内容比较。
  ///
  /// @param other 另一个对象
  /// @return 两档字节都相同时为 `true`
  @Override
  public boolean equals(Object other) {
    return other instanceof AvatarRenditions that
        && Arrays.equals(large, that.large)
        && Arrays.equals(small, that.small);
  }

  /// 按内容算哈希。
  ///
  /// @return 哈希
  @Override
  public int hashCode() {
    return 31 * Arrays.hashCode(large) + Arrays.hashCode(small);
  }

  /// 只输出长度。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "AvatarRenditions[large=" + large.length + "B, small=" + small.length + "B]";
  }
}
