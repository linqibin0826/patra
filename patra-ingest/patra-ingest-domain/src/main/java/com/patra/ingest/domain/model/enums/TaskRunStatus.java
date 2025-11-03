package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/**
 * 任务运行(尝试)状态 (字典: ing_task_run_status)。
 *
 * <p>字段映射: {@code ing_task_run.status_code → PENDING/RUNNING/SUCCEEDED/FAILED/PARTIAL}
 *
 * <p>状态转换及语义:
 *
 * <ul>
 *   <li><b>PENDING</b> - TaskRun 创建时的初始状态;等待执行开始
 *   <li><b>RUNNING</b> - 任务执行已开始(通过 {@code start()} 方法)
 *   <li><b>SUCCEEDED</b> - 任务成功完成,所有记录已处理(通过 {@code succeed()} 方法)
 *   <li><b>FAILED</b> - 任务失败并出现错误,未处理任何记录(通过 {@code fail()} 方法)
 *   <li><b>PARTIAL</b> - 任务部分成功;部分批次已处理,检查点已保存以供恢复(通过 {@code markPartial()} 方法) - <b>启用可恢复执行</b>
 * </ul>
 *
 * <p><b>注意:</b> 这是唯一保留 PARTIAL 状态以支持基于检查点的可恢复执行的层级。
 */
@Getter
public enum TaskRunStatus {
  /** 待处理;初始状态,等待执行开始。 */
  PENDING("PENDING", "Pending"),
  /** 运行中;任务执行进行中。 */
  RUNNING("RUNNING", "Running"),
  /** 成功;所有记录已成功处理。 */
  SUCCEEDED("SUCCEEDED", "Succeeded"),
  /** 失败;任务失败并出现错误,未处理任何记录。 */
  FAILED("FAILED", "Failed"),
  /** 部分完成;部分批次已处理,检查点已保存以供恢复。 */
  PARTIAL("PARTIAL", "Partially completed");

  private final String code;
  private final String description;

  TaskRunStatus(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static TaskRunStatus fromCode(String value) {
    if (value == null) {
      throw new IllegalArgumentException("TaskRun 状态代码不能为 null");
    }
    String n = value.trim().toUpperCase();
    for (TaskRunStatus e : values()) {
      if (e.code.equals(n)) return e;
    }
    throw new IllegalArgumentException("未知的 TaskRun 状态代码: " + value);
  }
}
