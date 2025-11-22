package com.patra.ingest.app.usecase.plan.dto;

import java.util.List;

/// 计划触发后返回给调度器的结果摘要。
///
/// @param scheduleInstanceId 调度实例标识符
/// @param planId 已持久化的计划标识符
/// @param sliceIds 已创建切片的标识符
/// @param taskCount 本次执行生成的任务数量
/// @param finalStatus 人类可读的状态摘要
public record PlanIngestionResult(
    Long scheduleInstanceId, Long planId, List<Long> sliceIds, int taskCount, String finalStatus) {}
