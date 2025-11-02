package com.patra.ingest.domain.model.enums;

import lombok.Getter;

/**
 * 任务状态 (字典: ing_task_status)。
 *
 * <p>字段映射: {@code ing_task.status_code → PENDING/QUEUED/RUNNING/SUCCEEDED/FAILED}
 *
 * <p>状态机语义:
 *
 * <ul>
 *   <li>PENDING → 初始状态,等待调度器拾取
 *   <li>QUEUED → 已加入执行队列
 *   <li>RUNNING → 任务运行进行中
 *   <li>RUNNING ⇄ QUEUED → 失败时重试(创建新的 TaskRun)
 *   <li>SUCCEEDED → 最终成功状态(至少一个 TaskRun 成功)
 *   <li>FAILED → 最终失败状态(所有 TaskRun 失败,达到最大重试次数)
 * </ul>
 *
 * <p><b>注意:</b> PARTIAL 状态已移至 TaskRun 层以进行可恢复执行追踪。
 */
@Getter
public enum TaskStatus {
  /** 待处理;初始状态,等待调度器拾取。 */
  PENDING("PENDING", "Pending"),
  /** 已排队;已加入执行队列,等待创建 TaskRun。 */
  QUEUED("QUEUED", "Queued"),
  /** 运行中;TaskRun 进行中。 */
  RUNNING("RUNNING", "Running"),
  /** 成功;至少一个 TaskRun 成功完成。 */
  SUCCEEDED("SUCCEEDED", "Succeeded"),
  /** 失败;所有 TaskRun 失败,达到最大重试次数。 */
  FAILED("FAILED", "Failed");

  private final String code;
  private final String description;

  TaskStatus(String code, String description) {
    this.code = code;
    this.description = description;
  }

  public static TaskStatus fromCode(String value) {
    if (value == null) {
      throw new IllegalArgumentException("任务状态代码不能为 null");
    }
    String n = value.trim().toUpperCase();
    for (TaskStatus e : values()) {
      if (e.code.equals(n)) return e;
    }
    throw new IllegalArgumentException("未知的任务状态代码: " + value);
  }
}
