package com.patra.expr;

/// 表示文本比较是否区分大小写。
/// 
/// 用于 TERM 和 IN 操作中控制大小写敏感度行为。
public enum CaseSensitivity {
  /// 不区分大小写比较。
/// 
/// 例如 "Text" 与 "text" 匹配。
  INSENSITIVE,

  /// 区分大小写比较。
/// 
/// 例如 "Text" 与 "text" 不匹配。
  SENSITIVE;

  /// 将布尔值转换为大小写敏感度。
/// 
/// @param caseSensitive 为 true 返回 SENSITIVE，false 返回 INSENSITIVE
/// @return 对应的大小写敏感度值
  public static CaseSensitivity of(boolean caseSensitive) {
    return caseSensitive ? SENSITIVE : INSENSITIVE;
  }

  /// 检查此实例是否代表区分大小写的匹配。
/// 
/// @return 若为 SENSITIVE 返回 true，若为 INSENSITIVE 返回 false
  public boolean isSensitive() {
    return this == SENSITIVE;
  }
}
