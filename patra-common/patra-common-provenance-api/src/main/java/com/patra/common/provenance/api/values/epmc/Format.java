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

  Format(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }

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

  @Override
  public String toString() {
    return value;
  }
}
