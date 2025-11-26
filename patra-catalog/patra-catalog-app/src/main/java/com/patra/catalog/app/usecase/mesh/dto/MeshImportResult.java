package com.patra.catalog.app.usecase.mesh.dto;

import com.patra.catalog.domain.model.enums.MeshDescriptorImportMode;

/// MeSH 导入结果（Application → Adapter）。
///
/// 导入任务执行后返回给调度器的结果摘要。
///
/// @param executionId 批处理执行标识符（Spring Batch Job Execution ID）
/// @param filePath 导入的 XML 文件路径
/// @param meshVersion MeSH 版本号
/// @param mode 使用的导入模式
/// @param message 人类可读的状态摘要
/// @author linqibin
/// @since 0.1.0
public record MeshImportResult(
    Long executionId,
    String filePath,
    String meshVersion,
    MeshDescriptorImportMode mode,
    String message) {

  /// 创建成功结果。
  ///
  /// @param executionId 批处理执行标识符
  /// @param filePath 文件路径
  /// @param meshVersion 版本号
  /// @param mode 导入模式
  /// @return 成功结果对象
  public static MeshImportResult success(
      Long executionId, String filePath, String meshVersion, MeshDescriptorImportMode mode) {
    return new MeshImportResult(
        executionId,
        filePath,
        meshVersion,
        mode,
        String.format(
            "MeSH 导入任务已启动，executionId=%d，filePath=%s，version=%s，mode=%s",
            executionId, filePath, meshVersion, mode));
  }
}
