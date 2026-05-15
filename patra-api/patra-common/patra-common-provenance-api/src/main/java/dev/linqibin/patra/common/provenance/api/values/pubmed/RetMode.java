package dev.linqibin.patra.common.provenance.api.values.pubmed;

import com.fasterxml.jackson.annotation.JsonValue;

/// PubMed RetMode 参数值枚举
///
/// 控制 API 返回格式类型
///
/// ### 使用示例
///
/// ```java
/// // 构建请求
/// params.put("retmode", RetMode.JSON.value());
///
/// // 类型安全比较
/// if (request.retmode() == RetMode.XML) {
///     // 处理 XML 响应
///
/// // 从字符串解析
/// RetMode mode = RetMode.fromString("json");
/// ```
///
/// @author linqibin
/// @since 0.1.0
public enum RetMode {
  /// JSON 格式（默认，推荐）
  JSON("json"),

  /// XML 格式（传统格式）
  XML("xml"),

  /// 纯文本格式（仅部分 API 支持）
  TEXT("text");

  private final String value;

  RetMode(String value) {
    this.value = value;
  }

  /// 获取 API 参数值
  ///
  /// @return API 参数字符串值
  @JsonValue // Jackson 序列化时使用此方法
  public String value() {
    return value;
  }

  /// 从字符串解析（忽略大小写）
  ///
  /// @param value 字符串值
  /// @return 对应的枚举，如果无效则抛出异常
  /// @throws IllegalArgumentException 如果值无效
  public static RetMode fromString(String value) {
    if (value == null) {
      throw new IllegalArgumentException("RetMode 不能为 null");
    }
    for (RetMode mode : values()) {
      if (mode.value.equalsIgnoreCase(value)) {
        return mode;
      }
    }
    throw new IllegalArgumentException("未知的 RetMode: " + value);
  }

  /// 安全解析（返回默认值而非抛异常）
  ///
  /// @param value 字符串值
  /// @param defaultValue 默认值
  /// @return 对应的枚举，如果无效则返回默认值
  public static RetMode fromStringOrDefault(String value, RetMode defaultValue) {
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
