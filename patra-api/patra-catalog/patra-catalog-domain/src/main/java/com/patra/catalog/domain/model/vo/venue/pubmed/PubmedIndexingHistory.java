package com.patra.catalog.domain.model.vo.venue.pubmed;

/// PubMed 期刊索引历史。
///
/// 表示从 NLM Serfile 解析出的索引历史数据。
///
/// @param indexingTreatment 索引处理方式（如 "Full", "Selective"）
/// @param citationSubset 引用子集（如 "IM"）
/// @param isCurrentlyIndexed 是否当前正在索引
/// @author linqibin
/// @since 0.1.0
public record PubmedIndexingHistory(
    String indexingTreatment, String citationSubset, boolean isCurrentlyIndexed) {

  /// 创建当前索引记录。
  public static PubmedIndexingHistory currentIndexing(
      String indexingTreatment, String citationSubset) {
    return new PubmedIndexingHistory(indexingTreatment, citationSubset, true);
  }

  /// 创建历史索引记录。
  public static PubmedIndexingHistory historicalIndexing(
      String indexingTreatment, String citationSubset) {
    return new PubmedIndexingHistory(indexingTreatment, citationSubset, false);
  }
}
