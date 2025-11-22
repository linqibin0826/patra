package com.patra.common.provenance.api.values.epmc;

import com.fasterxml.jackson.annotation.JsonValue;

/// EPMC Format 参数值枚举
///
/// 控制 API 返回格式和详细程度
///
/// ### 格式说明
///
/// - **JSON** - JSON 格式（默认，推荐）
///   - **XML** - XML 格式
///   - **LITE** - 轻量级格式，仅返回核心字段
///   - **CORE** - 核心格式，返回常用字段
///
/// @author linqibin
/// @since 0.1.0
public enum Format {
  /// JSON 格式（推荐）
  JSON("json"),

  /// XML 格式
  XML("xml"),

  /// 轻量级格式
  LITE("lite"),

  /// 核心格式
  CORE("core");

  private final String value;

  /// 构造 Format 枚举常量。
  ///
  /// @param value API 参数值
  Format(String value) {
    this.value = value;
  }

  /// 获取 API 参数值。
  ///
  /// @return API 参数字符串值
  @JsonValue
  public String value() {
    return value;
  }

  /// 从字符串解析（忽略大小写）。
  ///
  /// @param value 字符串值
  /// @return 对应的枚举
  /// @throws IllegalArgumentException 如果值为 null 或无效
  public static Format fromString(String value) {
    if (value == null) {
      throw new IllegalArgumentException("Format 不能为 null");
    }
    for (Format format : values()) {
      if (format.value.equalsIgnoreCase(value)) {
        return format;
      }
    }
    throw new IllegalArgumentException("未知的 Format: " + value);
  }

  /// 安全解析（返回默认值而非抛异常）。
  ///
  /// @param value 字符串值
  /// @param defaultValue 默认值
  /// @return 对应的枚举，如果无效则返回默认值
  public static Format fromStringOrDefault(String value, Format defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    try {
      return fromString(value);
    } catch (IllegalArgumentException e) {
      return defaultValue;
    }
  }

  /// 返回格式的字符串表示形式。
  ///
  /// @return API 参数值
  @Override
  public String toString() {
    return value;
  }
}
