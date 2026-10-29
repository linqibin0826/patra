package dev.patra.catalog.domain.publication.model.vo;

/// 一篇 PubMed 记录的原文及其来源位置：文件导入与本地重算的共同输入。
///
/// @param pmid PMID（文件导入时从原文里取；取不到为 null，交给解析阶段判定无效）
/// @param rawXml 这篇 `<PubmedArticle>` 的原文
/// @param position 来源位置
/// @author linqibin
/// @since 0.1.0
public record PubmedSourceRecord(String pmid, String rawXml, SourcePosition position) {

  /// 创建记录。
  ///
  /// @param pmid PMID，可空
  /// @param rawXml 原文
  /// @param position 来源位置
  /// @return 记录
  public static PubmedSourceRecord of(String pmid, String rawXml, SourcePosition position) {
    return new PubmedSourceRecord(pmid, rawXml, position);
  }
}
