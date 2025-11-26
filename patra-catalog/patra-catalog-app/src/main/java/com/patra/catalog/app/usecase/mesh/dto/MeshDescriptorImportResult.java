package com.patra.catalog.app.usecase.mesh.dto;

import com.patra.catalog.domain.model.enums.MeshDescriptorImportMode;

/// MeSH 主题词导入结果（Application → Adapter）。
///
/// 主题词（Descriptor）导入任务执行后返回给调度器的结果摘要。
///
/// @param executionId 批处理执行标识符（Spring Batch Job Execution ID）
/// @param sourceUrl 原始数据源 URL
/// @param filePath 下载后的本地临时文件路径（仅供内部追踪，不暴露于 message）
/// @param meshVersion MeSH 版本号
/// @param mode 使用的导入模式
/// @param message 人类可读的状态摘要（不含敏感的服务器路径）
/// @author linqibin
/// @since 0.1.0
public record MeshDescriptorImportResult(
    Long executionId,
    String sourceUrl,
    String filePath,
    String meshVersion,
    MeshDescriptorImportMode mode,
    String message) {

  /// 创建成功结果。
  ///
  /// @param executionId 批处理执行标识符
  /// @param sourceUrl 原始数据源 URL
  /// @param filePath 下载后的本地文件路径
  /// @param meshVersion 版本号
  /// @param mode 导入模式
  /// @return 成功结果对象
  public static MeshDescriptorImportResult success(
      Long executionId,
      String sourceUrl,
      String filePath,
      String meshVersion,
      MeshDescriptorImportMode mode) {
    return new MeshDescriptorImportResult(
        executionId,
        sourceUrl,
        filePath,
        meshVersion,
        mode,
        String.format(
            "MeSH 导入任务已启动，executionId=%d，sourceUrl=%s，version=%s，mode=%s",
            executionId, sourceUrl, meshVersion, mode));
  }
}
