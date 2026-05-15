package dev.linqibin.patra.catalog.domain.port.batch;

import dev.linqibin.patra.catalog.domain.model.vo.mesh.MeshImportParams;

/// MeSH 批量导入端口接口（领域层定义，基础设施层实现）。
///
/// **设计原则**：
///
/// - 六边形架构：领域层定义接口，隐藏批处理框架细节
/// - Application 层通过此端口调用批量导入，无需知道 Spring Batch 等技术实现
/// - 返回执行标识符供上层追踪任务状态
///
/// **统一接口，语义方法**：
///
/// - 提供语义明确的方法区分导入类型（Descriptor vs SCR）
/// - 每个方法使用相同的参数类型，保持接口一致性
/// - 适配器层通过方法名映射到对应的 Batch Job
///
/// **使用场景**：
///
/// - `launchDescriptorImport()`：导入主题词（约 35,000 条）
/// - `launchScrImport()`：导入补充概念记录（约 350,000 条）
/// - 支持断点续传和全量重导入
///
/// @author linqibin
/// @since 0.1.0
public interface MeshBatchPort {

  /// 启动 MeSH 主题词批量导入任务。
  ///
  /// @param params 导入参数（包含下载 URL、版本号）
  /// @return 批处理执行标识符（Spring Batch Job Execution ID）
  /// @see MeshImportParams
  Long launchDescriptorImport(MeshImportParams params);

  /// 启动 MeSH SCR 批量导入任务。
  ///
  /// @param params 导入参数（包含下载 URL、版本号）
  /// @return 批处理执行标识符（Spring Batch Job Execution ID）
  /// @see MeshImportParams
  Long launchScrImport(MeshImportParams params);
}
