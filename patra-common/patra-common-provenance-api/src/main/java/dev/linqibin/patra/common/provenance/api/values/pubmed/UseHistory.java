package dev.linqibin.patra.common.provenance.api.values.pubmed;

import com.fasterxml.jackson.annotation.JsonValue;

/// PubMed UseHistory 参数值枚举
///
/// 控制是否使用 History Server 缓存查询结果
///
/// ### 功能说明
///
/// - **YES** - 启用历史服务器，返回 WebEnv 和 QueryKey 用于后续批次请求
///   - **NO** - 不使用历史服务器，直接返回结果
///
/// ### 使用场景
///
/// 当需要分批获取大量数据时，使用 `YES` 可以：
///
/// @author linqibin
/// @since 0.1.0
public enum UseHistory {
  /// 使用历史服务器（推荐用于批量数据）
  YES("y"),

  /// 不使用历史服务器（适用于小数据集）
  NO("n");

  private final String value;

  UseHistory(String value) {
    this.value = value;
  }

  @JsonValue
  public String value() {
    return value;
  }

  public static UseHistory fromString(String value) {
    if (value == null) {
      throw new IllegalArgumentException("UseHistory 不能为 null");
    }
    for (UseHistory history : values()) {
      if (history.value.equalsIgnoreCase(value)) {
        return history;
      }
    }
    throw new IllegalArgumentException("未知的 UseHistory: " + value);
  }

  public static UseHistory fromStringOrDefault(String value, UseHistory defaultValue) {
    if (value == null) {
      return defaultValue;
    }
    try {
      return fromString(value);
    } catch (IllegalArgumentException e) {
      return defaultValue;
    }
  }

  /// 从布尔值转换
  ///
  /// @param useHistory true 表示使用历史服务器
  /// @return 对应的枚举值
  public static UseHistory fromBoolean(boolean useHistory) {
    return useHistory ? YES : NO;
  }

  /// 转换为布尔值
  ///
  /// @return true 如果是 YES
  public boolean toBoolean() {
    return this == YES;
  }

  @Override
  public String toString() {
    return value;
  }
}
