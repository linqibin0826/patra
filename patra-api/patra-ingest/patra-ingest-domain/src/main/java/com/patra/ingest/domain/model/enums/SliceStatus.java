package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/**
 * 计划切片状态 (字典: ing_slice_status)。
 *
 * <p>字段映射: {@code ing_plan_slice.status_code → PENDING/ASSIGNED/FINISHED}
 *
 * <p>状态机语义(强制 1:1 Slice-Task 关系):
 *
 * <ul>
 *   <li>PENDING → 等待 Task 生成
 *   <li>ASSIGNED → 对应的 Task 已创建(1:1 映射)
 *   <li>FINISHED → 关联的 Task 达到终态(SUCCEEDED 或 FAILED)
 * </ul>
 *
 * <p><b>注意:</b> Slice 不区分成功/失败。查询关联的 Task 以获取执行结果。
 */
@Getter
public enum SliceStatus {
  /** 待处理;等待 Task 生成。 */
  PENDING("PENDING", "Pending"),
  /** 已分配;对应的 Task 已创建。 */
  ASSIGNED("ASSIGNED", "Assigned"),
  /** 已完成;关联的 Task 达到终态。 */
  FINISHED("FINISHED", "Finished");

  private final String code;
  private final String description;

  SliceStatus(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static SliceStatus fromCode(String value) {
    if (value == null) {
      throw new IllegalArgumentException("切片状态代码不能为 null");
    }
    String n = value.trim().toUpperCase();
    for (SliceStatus e : values()) {
      if (e.code.equals(n)) return e;
    }
    throw new IllegalArgumentException("未知的切片状态代码: " + value);
  }
}
