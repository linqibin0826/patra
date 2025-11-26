package com.patra.catalog.domain.port;

/// MeSH 主题词批量导入端口接口（领域层定义，基础设施层实现）。
///
/// **设计原则**：
///
/// - 六边形架构：领域层定义接口，隐藏批处理框架细节
/// - Application 层通过此端口调用批量导入，无需知道 Spring Batch 等技术实现
/// - 返回执行标识符供上层追踪任务状态
///
/// **使用场景**：
///
/// - 大数据量主题词导入（约 35,000 条）
/// - 支持断点续传
/// - 支持重复执行和全量重导入
///
/// @author linqibin
/// @since 0.1.0
public interface MeshDescriptorBatchPort {

  /// 启动主题词批量导入任务。
  ///
  /// @param filePath XML 文件路径
  /// @param meshVersion MeSH 版本（如 "2025"）
  /// @param forceNewInstance 是否强制创建新实例
  ///   - `false`：幂等执行，相同参数复用 Job 实例，支持断点续传
  ///   - `true`：强制创建新实例，每次执行都创建新的任务实例
  /// @return 批处理执行标识符
  Long launchImport(String filePath, String meshVersion, boolean forceNewInstance);
}
