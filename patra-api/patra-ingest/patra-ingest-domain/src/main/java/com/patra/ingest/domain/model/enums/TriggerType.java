package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/// 触发类型 (字典: ing_trigger_type)。
/// 
/// 字段映射: `ing_schedule_instance.trigger_type_code → SCHEDULE/MANUAL/API`
/// 
/// 触发类型语义:
/// 
/// - SCHEDULE → 定时调度触发(由调度器自动触发)
///   - MANUAL → 手动触发(由用户手动触发)
///   - API → API 调用触发(通过 API 接口触发)
/// 
@Getter
public enum TriggerType {
  /// 定时调度;由调度器自动触发。
  SCHEDULE("SCHEDULE", "Scheduled trigger"),
  /// 手动触发;由用户手动触发。
  MANUAL("MANUAL", "Manual trigger"),
  /// API 触发;通过 API 接口调用触发。
  API("API", "API invocation");

  private final String code;
  private final String description;

  TriggerType(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static TriggerType fromCode(String value) {
    if (value == null) {
      throw new IllegalArgumentException("触发类型代码不能为 null");
    }
    String normalized = value.trim().toUpperCase();
    for (TriggerType type : values()) {
      if (type.code.equals(normalized)) {
        return type;
      }
    }
    throw new IllegalArgumentException("未知的触发类型: " + value);
  }
}
