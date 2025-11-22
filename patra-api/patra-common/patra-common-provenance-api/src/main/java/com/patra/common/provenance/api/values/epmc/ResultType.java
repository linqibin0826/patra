package com.patra.common.provenance.api.values.epmc;

import com.fasterxml.jackson.annotation.JsonValue;

/// EPMC ResultType 参数值枚举
///
/// 控制返回结果的详细程度
///
/// ### 类型说明
///
/// - **LITE** - 轻量级结果，仅包含基本信息
///   - **CORE** - 核心结果，包含常用字段
///   - **IDLIST** - 仅返回 ID 列表
///
/// @author linqibin
/// @since 0.1.0
public enum ResultType {
  /// 轻量级结果
  LITE("lite"),

  /// 核心结果（推荐）
  CORE("core"),

  /// 仅 ID 列表
  IDLIST("idlist");

  private final String value;

  ResultType(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }

  public static ResultType fromString(String value) {
    if (value == null) {
      throw new IllegalArgumentException("ResultType 不能为 null");
    }
    for (ResultType type : values()) {
      if (type.value.equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的 ResultType: " + value);
  }

  public static ResultType fromStringOrDefault(String value, ResultType defaultValue) {
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
