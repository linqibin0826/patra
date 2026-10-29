package dev.patra.catalog.domain.fulltext.model.vo;

/// PMC 桶里一个版本目录下的文件。
///
/// @param fileName 文件名
/// @param md5 PMC 元数据给出的 md5，下载后据此校验
/// @author linqibin
/// @since 0.11.0
public record PmcArticleFile(String fileName, String md5) {

  /// 创建文件引用。
  ///
  /// @param fileName 文件名
  /// @param md5 md5
  /// @return 文件引用
  public static PmcArticleFile of(String fileName, String md5) {
    return new PmcArticleFile(fileName, md5);
  }
}
