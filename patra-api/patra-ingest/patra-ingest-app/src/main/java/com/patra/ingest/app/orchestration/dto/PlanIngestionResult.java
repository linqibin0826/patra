package com.patra.ingest.app.orchestration.dto;

import java.util.List;

/**
 * 计划触发结果，向调度层返回核心统计信息。
 *
 * @param scheduleInstanceId 调度实例 ID
 * @param planId 计划 ID
 * @param sliceIds 切片 ID 集合
 * @param taskCount 任务数量
 * @param finalStatus 最终状态描述
 */
public record PlanIngestionResult(Long scheduleInstanceId,
                                  Long planId,
                                  List<Long> sliceIds,
                                  int taskCount,
                                  String finalStatus) {
}
