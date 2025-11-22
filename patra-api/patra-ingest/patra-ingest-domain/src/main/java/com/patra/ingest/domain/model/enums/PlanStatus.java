package com.patra.ingest.domain.model.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import lombok.Getter;

/// 计划状态 (字典: ing_plan_status)。
/// 
/// 字段映射: `ing_plan.status_code → DRAFT/SLICING/READY/ARCHIVED`
/// 
/// 状态机语义:
/// 
/// - DRAFT → 新创建,尚未开始切片
///   - SLICING → 正在生成切片/任务
///   - READY → 切片和任务创建成功
///   - ARCHIVED → 生命周期已关闭,所有任务已完成(以前称为 COMPLETED)
/// 
/// **注意:** 计划状态仅反映其自身的生命周期。执行结果(部分完成/失败)应通过聚合任务状态来查询。
@Getter
public enum PlanStatus {
  /// 草稿;尚未开始切片。
  DRAFT("DRAFT", "Draft"),
  /// 切片进行中(不可重复转换)。
  SLICING("SLICING", "Slicing"),
  /// 切片已生成,任务已就绪可调度。
  READY("READY", "Ready"),
  /// 已归档;生命周期已关闭,所有任务已完成。
  ARCHIVED("ARCHIVED", "Archived");

  private final String code;
  private final String description;

  PlanStatus(String code, String description) {
    this.code = code;
    this.description = description;
  }

  @JsonCreator
  public static PlanStatus fromCode(String value) {
    if (value == null) {
      throw new IllegalArgumentException("计划状态代码不能为 null");
    }
    String n = value.trim().toUpperCase();
    for (PlanStatus e : values()) {
      if (e.code.equals(n)) return e;
    }
    throw new IllegalArgumentException("未知的计划状态代码: " + value);
  }
}
