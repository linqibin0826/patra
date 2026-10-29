package dev.patra.catalog.domain.publication.port.source;

/// 读取 PubMed updatefiles 目录的端口。
///
/// @author linqibin
/// @since 0.11.0
public interface PubmedFileListingPort {

  /// 返回 updatefiles 目录里当前最大的文件号。
  ///
  /// 读目录的 HTML 索引，取所有 `{filePrefix}NNNN.xml.gz` 的最大号。
  ///
  /// @param updateUrl updatefiles 目录地址
  /// @param filePrefix 文件名前缀，如 `pubmed26n`
  /// @return 最大文件号
  /// @throws dev.patra.catalog.domain.publication.exception.PubmedUpdateListingException
  ///     目录读取失败或没有匹配文件时抛出，不返回 0
  int findLatestSeq(String updateUrl, String filePrefix);
}
