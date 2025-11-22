package com.patra.catalog.api.dto;

import java.time.Instant;
import java.util.List;
import lombok.Builder;

/// MeSH 导入进度 DTO（数据传输对象）。
///
/// 用于实时监控导入进度，符合 API 契约定义 (mesh-import-api.yaml#ImportProgressResponse)。
///
/// **设计原则**：
///
/// - API 契约优先：严格遵循 OpenAPI 规范
///   - 不可变对象：使用 Record 类型保证线程安全
///   - 跨模块共享：放在 api 模块，供其他服务调用
///   - 扁平化设计：避免过度嵌套，便于前端使用
///
/// **核心字段**：
///
/// - overallProgress - 整体进度百分比（0.0 ~ 100.0）
///   - processSpeed - 处理速度（记录/秒）
///   - estimatedRemainingSeconds - 预计剩余时间（秒）
///   - tableProgress - 各表进度列表
///   - failedBatches - 失败批次列表（用于错误追踪）
///
/// @author linqibin
/// @since 0.1.0
@Builder
public record MeshProgressDTO(
    // ========== 任务基本信息 ==========
    /// 任务 ID
    String taskId,

    /// 任务名称
    String taskName,

    /// 任务状态（PENDING/PROCESSING/SUCCESS/FAILED/CANCELLED）
    String status,

    // ========== 整体进度 ==========
    /// 总记录数
    Integer totalRecords,

    /// 已处理记录数
    Integer processedRecords,

    /// 整体进度百分比（0.0 ~ 100.0）
    Double overallProgress,

    /// 处理速度（记录/秒），如果任务未开始返回 null
    Double processSpeed,

    /// 预计剩余时间（秒），如果无法估算返回 null
    Long estimatedRemainingSeconds,

    // ========== 时间信息 ==========
    /// 开始时间
    Instant startTime,

    /// 结束时间（任务完成或失败时）
    Instant endTime,

    /// 已用时间（秒）
    Long elapsedSeconds,

    // ========== 各表进度 ==========
    /// 各表进度列表
    List<TableProgressDTO> tableProgress,

    // ========== 失败批次 ==========
    /// 失败批次列表（用于错误追踪和重试）
    List<FailedBatchDTO> failedBatches) {

  /// 表进度 DTO（嵌套对象）。
  ///
  /// 表示单张表的导入进度。
  @Builder
  public record TableProgressDTO(
      /// 表名（如 "descriptor"）
      String tableName,

      /// 显示名称（如 "主题词"）
      String displayName,

      /// 总记录数
      Integer totalCount,

      /// 已处理数
      Integer processedCount,

      /// 失败数
      Integer failedCount,

      /// 进度百分比（0.0 ~ 100.0）
      Double progressPercentage,

      /// 表状态（NOT_STARTED/IN_PROGRESS/COMPLETED/FAILED）
      String status) {}

  /// 失败批次 DTO（嵌套对象）。
  ///
  /// 表示导入失败的批次详情，用于错误分析和重试。
  @Builder
  public record FailedBatchDTO(
      /// 批次 ID
      Long batchId,

      /// 表名
      String tableName,

      /// 批次序号（从 1 开始）
      Integer batchNum,

      /// 失败原因
      String failureReason,

      /// 失败时间
      Instant failureTime,

      /// 重试次数
      Integer retryCount) {}
}
