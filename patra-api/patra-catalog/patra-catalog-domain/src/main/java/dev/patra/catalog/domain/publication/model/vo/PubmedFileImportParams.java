package dev.patra.catalog.domain.publication.model.vo;

import dev.patra.catalog.domain.publication.model.enums.PubmedImportScope;

/// 处理一个 PubMed 文件的参数。
///
/// @param file 文件（种类、前缀、序号）
/// @param baseUrl 该种类文件的下载目录（baseline 或 updatefiles）
/// @param scope 导入范围
/// @param generation 作业 generation（重处理时换新值，生成新的作业实例），可空
/// @author linqibin
/// @since 0.1.0
public record PubmedFileImportParams(
    PubmedFileRef file, String baseUrl, PubmedImportScope scope, String generation) {

  /// 创建参数。
  ///
  /// @param file 文件
  /// @param baseUrl 下载目录
  /// @param scope 导入范围
  /// @param generation 作业 generation，可空
  /// @return 参数
  public static PubmedFileImportParams of(
      PubmedFileRef file, String baseUrl, PubmedImportScope scope, String generation) {
    return new PubmedFileImportParams(file, baseUrl, scope, generation);
  }
}
