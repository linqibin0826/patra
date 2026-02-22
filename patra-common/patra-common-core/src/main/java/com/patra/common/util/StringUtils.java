package com.patra.common.util;

/// 字符串工具类，提供通用的字符串处理方法。
///
/// @author linqibin
/// @since 0.1.0
public final class StringUtils {

  private StringUtils() {}

  /// 将空白字符串归一化为 null。
  ///
  /// 去除首尾空白后，若结果为空则返回 null，否则返回 trim 后的字符串。
  ///
  /// @param value 原始字符串
  /// @return 去除首尾空白后的字符串，空白时返回 null
  public static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
