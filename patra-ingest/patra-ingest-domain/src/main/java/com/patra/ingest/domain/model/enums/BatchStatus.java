package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/// 批次执行状态 (字典: ing_batch_status)。
/// 
/// 字段映射: `ing_task_run_batch.status_code → RUNNING/SUCCEEDED/FAILED/SKIPPED`
/// 
/// 状态机语义:
/// 
/// - RUNNING → 批次正在执行
///   - SUCCEEDED → 批次成功完成
///   - FAILED → 批次执行失败
///   - SKIPPED → 批次被跳过(例如,由于依赖条件不满足)
/// 
@Getter
public enum BatchStatus {
  /// 运行中;批次正在执行。
  RUNNING("RUNNING", "Running"),
  /// 成功;批次执行成功。
  SUCCEEDED("SUCCEEDED", "Succeeded"),
  /// 失败;批次执行失败。
  FAILED("FAILED", "Failed"),
  /// 已跳过;批次被跳过执行。
  SKIPPED("SKIPPED", "Skipped");

  private final String code;
  private final String description;

  BatchStatus(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static BatchStatus fromCode(String value) {
    if (value == null) {
      throw new IllegalArgumentException("批次状态代码不能为 null");
    }
    String n = value.trim().toUpperCase();
    for (BatchStatus e : values()) {
      if (e.code.equals(n)) return e;
    }
    throw new IllegalArgumentException("未知的批次状态代码: " + value);
  }
}
