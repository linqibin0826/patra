package com.patra.ingest.domain.model.vo.cursor;

/// 游标血缘上下文值对象,表示单次游标推进的相关实体标识符。
/// 
/// 促进跨调度层的可追溯性和审计。
/// 
/// 不可变性:通过值语义比较相等性
/// 
/// 使用场景:追踪游标更新的完整调用链路
/// 
/// @param scheduleInstanceId 调度实例ID
/// @param planId 计划ID
/// @param sliceId 切片ID
/// @param taskId 任务ID
/// @param runId 任务运行ID
/// @param batchId 任务运行批次ID
public record CursorLineage(
    Long scheduleInstanceId, Long planId, Long sliceId, Long taskId, Long runId, Long batchId) {

  /// 创建空血缘上下文(所有层级为`null`)。
/// 
/// @return 空血缘上下文
  public static CursorLineage empty() {
    return new CursorLineage(null, null, null, null, null, null);
  }
}
