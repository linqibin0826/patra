package com.patra.catalog.domain.port;

import com.patra.catalog.domain.model.enums.MeshBatchStatus;
import com.patra.catalog.domain.model.valueobject.FailedBatch;
import com.patra.catalog.domain.model.valueobject.MeshImportId;
import java.util.List;

/// MeSH 批次详情仓储接口（Repository）。
///
/// 定义批次详情的查询能力，用于进度监控和失败批次追踪。
///
/// **设计原则**：
///
/// - 依赖倒置：Domain 层定义接口，Infrastructure 层实现
///   - 只读仓储：仅提供查询方法，批次详情由导入流程创建
///   - 支持进度监控：提供按状态统计和失败批次查询
///
/// **使用场景**：
///
/// - 进度查询：统计各状态批次数量，计算完成度
///   - 失败追踪：查询失败批次详情，用于重试和错误分析
///   - 性能分析：通过批次时间戳分析导入速度
///
/// @author linqibin
/// @since 0.1.0
public interface MeshBatchDetailRepository {

  /// 查询指定任务的失败批次。
  ///
  /// 用于重试失败批次和错误分析。
  ///
  /// @param importId 任务ID
  /// @return 失败批次列表（状态为 FAILED）
  List<FailedBatch> findFailedBatches(MeshImportId importId);

  /// 统计指定任务的某状态批次数量。
  ///
  /// 用于进度监控和状态统计。
  ///
  /// @param importId 任务ID
  /// @param status 批次状态
  /// @return 批次数量
  Long countByStatus(MeshImportId importId, MeshBatchStatus status);
}
