package dev.linqibin.patra.ingest.domain.model.enums;

import lombok.Getter;

/// 游标推进方向 (字典: ing_cursor_direction)。
///
/// 字段映射: `ing_cursor_event.direction_code → FORWARD/BACKFILL`。
///
/// 方向语义:
///
/// - FORWARD → 向前推进,用于增量数据采集
///   - BACKFILL → 向后回填,用于历史数据补全
///
@Getter
public enum CursorDirection {
  /// 向前;增量推进方向。
  FORWARD("FORWARD", "Forward"),
  /// 回填;历史数据回填方向。
  BACKFILL("BACKFILL", "Backfill");

  private final String code;
  private final String description;

  CursorDirection(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static CursorDirection fromCode(String value) {
    if (value == null) {
      throw new IllegalArgumentException("游标方向代码不能为 null");
    }
    String n = value.trim().toUpperCase();
    for (CursorDirection e : values()) {
      if (e.code.equals(n)) return e;
    }
    throw new IllegalArgumentException("未知的游标方向代码: " + value);
  }
}
