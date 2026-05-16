package dev.linqibin.patra.ingest.domain.model.enums;

import lombok.Getter;

/// 游标类型 (字典: ing_cursor_type)。
///
/// 字段映射: `cursor_type_code → TIME/ID/TOKEN`。
///
/// 类型语义:
///
/// - TIME → 基于时间的游标(时间戳)
///   - ID → 基于标识符的游标(数值 ID)
///   - TOKEN → 基于令牌的游标(不透明令牌,如分页 token)
///
@Getter
public enum CursorType {
  /// 时间型;基于时间戳的游标。
  TIME("TIME", "Time-based"),
  /// 标识符型;基于数值 ID 的游标。
  ID("ID", "Identifier-based"),
  /// 令牌型;基于不透明令牌的游标。
  TOKEN("TOKEN", "Token-based");

  private final String code;
  private final String description;

  CursorType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static CursorType fromCode(String value) {
    if (value == null) {
      throw new IllegalArgumentException("游标类型代码不能为 null");
    }
    String n = value.trim().toUpperCase();
    for (CursorType e : values()) {
      if (e.code.equals(n)) return e;
    }
    throw new IllegalArgumentException("未知的游标类型代码: " + value);
  }
}
