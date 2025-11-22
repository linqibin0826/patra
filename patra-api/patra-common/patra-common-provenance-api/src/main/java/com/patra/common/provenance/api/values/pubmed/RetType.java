package com.patra.common.provenance.api.values.pubmed;

import com.fasterxml.jackson.annotation.JsonValue;

/// PubMed RetType 参数值枚举
///
/// 控制 API 返回内容类型
///
/// ### 各值说明
///
/// - **COUNT** - 仅返回匹配数量，用于快速统计
///   - **UILIST** - 返回 PMID 列表，用于分页获取 ID
///   - **ABSTRACT** - 返回摘要信息（EFetch 专用）
///
/// @author linqibin
/// @since 0.1.0
public enum RetType {
  /// 仅返回数量（适用于 ESearch）
  COUNT("count"),

  /// 返回 ID 列表（适用于 ESearch）
  UILIST("uilist"),

  /// 返回摘要（适用于 EFetch）
  ABSTRACT("abstract");

  private final String value;

  RetType(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }

  public static RetType fromString(String value) {
    if (value == null) {
      throw new IllegalArgumentException("RetType 不能为 null");
    }
    for (RetType type : values()) {
      if (type.value.equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的 RetType: " + value);
  }

  public static RetType fromStringOrDefault(String value, RetType defaultValue) {
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
