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

  /// 构造 ResultType 枚举常量。
  ///
  /// @param value API 参数值
  ResultType(String value) {
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

  /// 安全解析（返回默认值而非抛异常）。
  ///
  /// @param value 字符串值
  /// @param defaultValue 默认值
  /// @return 对应的枚举，如果无效则返回默认值
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

  /// 返回结果类型的字符串表示形式。
  ///
  /// @return API 参数值
  @Override
  public String toString() {
    return value;
  }
}
