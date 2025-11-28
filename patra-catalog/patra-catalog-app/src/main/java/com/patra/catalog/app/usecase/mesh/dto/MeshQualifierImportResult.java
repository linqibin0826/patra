package com.patra.catalog.app.usecase.mesh.dto;

/// MeSH 限定词导入结果（Application → Adapter）。
///
/// 限定词导入任务执行后返回给调度器的结果摘要。
///
/// **设计说明**：
///
/// - 限定词导入不使用 Spring Batch（数据量小，约 80 条）
/// - 仅支持 TRUNCATE_REIMPORT 模式，因此不需要 mode 字段
/// - 同步执行，直接返回导入数量
///
/// @param sourceUrl 原始数据源 URL
/// @param meshVersion MeSH 版本号
/// @param importedCount 成功导入的限定词数量
/// @param message 人类可读的状态摘要
/// @author linqibin
/// @since 0.1.0
public record MeshQualifierImportResult(
    String sourceUrl, String meshVersion, int importedCount, String message) {

  /// 创建成功结果。
  ///
  /// @param sourceUrl 原始数据源 URL
  /// @param meshVersion 版本号
  /// @param importedCount 导入数量
  /// @return 成功结果对象
  public static MeshQualifierImportResult success(
      String sourceUrl, String meshVersion, int importedCount) {
    return new MeshQualifierImportResult(
        sourceUrl,
        meshVersion,
        importedCount,
        String.format(
            "MeSH 限定词导入完成，sourceUrl=%s，version=%s，导入数量=%d", sourceUrl, meshVersion, importedCount));
  }
}
