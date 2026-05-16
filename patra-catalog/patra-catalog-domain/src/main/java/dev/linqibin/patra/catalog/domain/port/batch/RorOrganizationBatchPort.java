package dev.linqibin.patra.catalog.domain.port.batch;

import dev.linqibin.patra.catalog.domain.model.vo.organization.RorImportParams;

/// ROR 机构批量导入端口接口（领域层定义，基础设施层实现）。
///
/// **设计原则**：
///
/// - 六边形架构：领域层定义接口，隐藏批处理框架细节
/// - Application 层通过此端口调用批量导入，无需知道 Spring Batch 等技术实现
/// - 返回执行标识符供上层追踪任务状态
///
/// **使用场景**：
///
/// - `launchImport()`：导入 ROR 机构数据（约 110,000 条）
/// - 支持断点续传和全量重导入
///
/// **两阶段导入**：
///
/// 1. 第一阶段：导入所有机构数据，关系表的 `related_org_id` 暂为 NULL
/// 2. 第二阶段：调用 `launchRelationLinking()` 填充机构关系
///
/// @author linqibin
/// @since 0.1.0
public interface RorOrganizationBatchPort {

  /// 启动 ROR 机构批量导入任务。
  ///
  /// @param params 导入参数（包含下载 URL、版本号）
  /// @return 批处理执行标识符（Spring Batch Job Execution ID）
  /// @see RorImportParams
  Long launchImport(RorImportParams params);
}
