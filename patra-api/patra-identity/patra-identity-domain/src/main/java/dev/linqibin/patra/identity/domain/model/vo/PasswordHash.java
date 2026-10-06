package dev.linqibin.patra.identity.domain.model.vo;

import java.util.Objects;

/// 密码哈希的编码串（Argon2id 的 PHC 格式，自带参数和盐）。`toString()` 不输出内容。
///
/// @param value 编码串
public record PasswordHash(String value) {

  /// 校验非空。
  ///
  /// @param value 编码串
  public PasswordHash {
    Objects.requireNonNull(value, "value 不能为 null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("密码哈希不能为空白");
    }
  }

  /// 创建密码哈希。
  ///
  /// @param value 编码串
  /// @return 密码哈希
  public static PasswordHash of(String value) {
    return new PasswordHash(value);
  }

  /// 不输出哈希。
  ///
  /// @return 固定文本 `***`
  @Override
  public String toString() {
    return "***";
  }
}
