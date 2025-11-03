package com.patra.ingest.domain.service;

import com.patra.ingest.domain.model.enums.SliceStatus;
import com.patra.ingest.domain.model.enums.TaskStatus;

/**
 * 切片状态计算器领域服务。基于关联任务状态计算切片状态（强制 1:1 关系）。
 *
 * <p>这是一个无状态纯函数,直接将任务状态映射到切片状态。
 *
 * <p>重构后,切片:任务是 1:1 关系,因此无需聚合。映射规则为：
 *
 * <ul>
 *   <li>TaskStatus.PENDING 或 QUEUED → SliceStatus.PENDING（等待任务生成/执行）
 *   <li>TaskStatus.RUNNING → SliceStatus.ASSIGNED（任务执行中）
 *   <li>TaskStatus.SUCCEEDED 或 FAILED → SliceStatus.FINISHED（任务达到终态）
 * </ul>
 *
 * <p><b>注意：</b>切片不再区分成功/失败。直接查询任务以获取执行结果。
 */
public final class SliceStatusCalculator {

  private SliceStatusCalculator() {
    // Utility class, prevent instantiation
  }

  /**
   * 基于关联任务状态计算切片状态（1:1 映射）。
   *
   * @param taskStatus 唯一关联任务的状态（不能为 null）
   * @return 对应的切片状态
   * @throws IllegalArgumentException 如果 taskStatus 为 null
   */
  public static SliceStatus calculate(TaskStatus taskStatus) {
    if (taskStatus == null) {
      throw new IllegalArgumentException("Task status cannot be null");
    }

    return switch (taskStatus) {
      case PENDING, QUEUED -> SliceStatus.PENDING;
      case RUNNING -> SliceStatus.ASSIGNED;
      case SUCCEEDED, FAILED -> SliceStatus.FINISHED;
    };
  }

  /**
   * Checks if a Task status is a terminal state (no further transitions expected).
   *
   * @param status task status
   * @return true if terminal, false otherwise
   */
  public static boolean isTerminal(TaskStatus status) {
    return status == TaskStatus.SUCCEEDED || status == TaskStatus.FAILED;
  }
}
