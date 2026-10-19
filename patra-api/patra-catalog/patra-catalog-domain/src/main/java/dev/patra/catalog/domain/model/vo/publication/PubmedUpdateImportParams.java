package dev.patra.catalog.domain.model.vo.publication;

import dev.patra.catalog.domain.model.enums.PubmedUpdateScope;

/// 启动一个 updatefiles 文件导入作业的参数。
///
/// @param file 要处理的文件
/// @param updateUrl updatefiles 目录地址
/// @param scope 导入范围
/// @param generation 重放代次，重放已完成的文件时传新值以生成新的作业实例；平时为 `null`
/// @author linqibin
/// @since 0.11.0
public record PubmedUpdateImportParams(
    PubmedUpdateFileRef file, String updateUrl, PubmedUpdateScope scope, String generation) {

  /// 创建参数。
  ///
  /// @param file 文件
  /// @param updateUrl 目录地址
  /// @param scope 导入范围
  /// @param generation 重放代次，可空
  /// @return 参数
  public static PubmedUpdateImportParams of(
      PubmedUpdateFileRef file, String updateUrl, PubmedUpdateScope scope, String generation) {
    return new PubmedUpdateImportParams(file, updateUrl, scope, generation);
  }
}
