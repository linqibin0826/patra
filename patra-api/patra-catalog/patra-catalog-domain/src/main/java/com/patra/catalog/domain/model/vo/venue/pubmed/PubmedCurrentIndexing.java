package com.patra.catalog.domain.model.vo.venue.pubmed;

/// PubMed 当前索引子集。
///
/// 表示期刊当前正在索引的子集信息。
///
/// **XML 结构示例**：
///
/// ```xml
/// <CurrentlyIndexedForSubset CurrentSubset="IM" CurrentIndexingTreatment="Full">
///   Current indexing info
/// </CurrentlyIndexedForSubset>
/// ```
///
/// **子集代码说明**：
///
/// | 代码 | 含义 |
/// |------|------|
/// | IM | Index Medicus |
/// | AIM | Abridged Index Medicus |
/// | D | Dental Literature |
/// | N | Nursing Literature |
///
/// **索引处理方式**：
///
/// | 方式 | 含义 |
/// |------|------|
/// | Full | 全文索引 |
/// | Selective | 选择性索引 |
/// | Unknown | 未知 |
///
/// @param subset 子集代码
/// @param indexingTreatment 索引处理方式
/// @param content 元素文本内容（可选）
/// @author linqibin
/// @since 0.1.0
public record PubmedCurrentIndexing(String subset, String indexingTreatment, String content) {

  /// 创建当前索引子集（无内容）。
  ///
  /// @param subset 子集代码
  /// @param indexingTreatment 索引处理方式
  /// @return 当前索引子集值对象
  public static PubmedCurrentIndexing of(String subset, String indexingTreatment) {
    return new PubmedCurrentIndexing(subset, indexingTreatment, null);
  }

  /// 创建当前索引子集（带内容）。
  ///
  /// @param subset 子集代码
  /// @param indexingTreatment 索引处理方式
  /// @param content 元素文本内容
  /// @return 当前索引子集值对象
  public static PubmedCurrentIndexing of(String subset, String indexingTreatment, String content) {
    return new PubmedCurrentIndexing(subset, indexingTreatment, content);
  }

  /// 判断是否为 Index Medicus 子集。
  public boolean isIndexMedicus() {
    return "IM".equalsIgnoreCase(subset);
  }

  /// 判断是否为全文索引。
  public boolean isFullIndexing() {
    return "Full".equalsIgnoreCase(indexingTreatment);
  }

  /// 判断是否为选择性索引。
  public boolean isSelectiveIndexing() {
    return "Selective".equalsIgnoreCase(indexingTreatment);
  }

  /// 判断是否有内容。
  public boolean hasContent() {
    return content != null && !content.isBlank();
  }
}
