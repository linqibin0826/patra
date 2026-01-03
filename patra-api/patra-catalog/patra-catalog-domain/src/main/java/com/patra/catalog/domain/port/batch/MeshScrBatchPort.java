package com.patra.catalog.domain.port.batch;

import com.patra.catalog.domain.model.vo.mesh.MeshImportParams;

/// MeSH SCR 批量导入端口接口（领域层定义，基础设施层实现）。
///
/// **设计原则**：
///
/// - 六边形架构：领域层定义接口，隐藏批处理框架细节
/// - Application 层通过此端口调用批量导入，无需知道 Spring Batch 等技术实现
/// - 返回执行标识符供上层追踪任务状态
///
/// **使用场景**：
///
/// - 大数据量 SCR 导入（约 350,000 条）
/// - 支持断点续传
/// - 纯 INSERT 策略，不支持增量或覆盖模式
///
/// @author linqibin
/// @since 0.1.0
public interface MeshScrBatchPort {

  /// 启动 SCR 批量导入任务。
  ///
  /// @param params 导入参数（包含下载 URL、版本号）
  /// @return 批处理执行标识符（Spring Batch Job Execution ID）
  /// @see MeshImportParams
  Long launchImport(MeshImportParams params);
}
