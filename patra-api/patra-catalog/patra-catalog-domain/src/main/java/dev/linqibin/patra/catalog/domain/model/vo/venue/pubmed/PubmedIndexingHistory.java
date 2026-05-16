package dev.linqibin.patra.catalog.domain.model.vo.venue.pubmed;

import java.time.LocalDate;

/// PubMed 期刊索引历史。
///
/// 表示从 NLM Serfile 解析出的索引历史数据。
///
/// **XML 结构示例**：
///
/// ```xml
/// <IndexingHistory CitationSubset="IM" IndexingTreatment="Full"
///     IndexingStatus="Currently-indexed">
///   <DateOfAction>
///     <Year>2020</Year>
///     <Month>01</Month>
///     <Day>15</Day>
///   </DateOfAction>
///   <Coverage>v1n1, 2010-</Coverage>
///   <CoverageNote>Indexed selectively from 2010.</CoverageNote>
/// </IndexingHistory>
/// ```
///
/// **属性说明**：
///
/// - `citationSubset`: 引用子集（如 IM = Index Medicus, AIM = Abridged Index Medicus）
/// - `indexingTreatment`: 索引处理方式（Full, Selective, ReferencedIn 等）
/// - `indexingStatus`: 索引状态（Currently-indexed, Ceased-publication, Deselected 等）
///
/// @param citationSubset 引用子集代码
/// @param indexingTreatment 索引处理方式（如 "Full", "Selective"）
/// @param indexingStatus 索引状态
/// @param dateOfAction 操作日期
/// @param coverage 覆盖范围描述
/// @param coverageNote 覆盖范围备注
/// @author linqibin
/// @since 0.1.0
public record PubmedIndexingHistory(
    String citationSubset,
    String indexingTreatment,
    String indexingStatus,
    LocalDate dateOfAction,
    String coverage,
    String coverageNote) {

  /// 创建索引历史记录（基本版）。
  ///
  /// @param citationSubset 引用子集代码
  /// @param indexingTreatment 索引处理方式
  /// @param indexingStatus 索引状态
  /// @param dateOfAction 操作日期
  /// @return 索引历史
  public static PubmedIndexingHistory of(
      String citationSubset,
      String indexingTreatment,
      String indexingStatus,
      LocalDate dateOfAction) {
    return new PubmedIndexingHistory(
        citationSubset, indexingTreatment, indexingStatus, dateOfAction, null, null);
  }

  /// 创建完整的索引历史记录。
  ///
  /// @param citationSubset 引用子集代码
  /// @param indexingTreatment 索引处理方式
  /// @param indexingStatus 索引状态
  /// @param dateOfAction 操作日期
  /// @param coverage 覆盖范围描述
  /// @param coverageNote 覆盖范围备注
  /// @return 索引历史
  public static PubmedIndexingHistory ofFull(
      String citationSubset,
      String indexingTreatment,
      String indexingStatus,
      LocalDate dateOfAction,
      String coverage,
      String coverageNote) {
    return new PubmedIndexingHistory(
        citationSubset, indexingTreatment, indexingStatus, dateOfAction, coverage, coverageNote);
  }

  /// 创建当前索引记录（兼容旧 API）。
  ///
  /// @param indexingTreatment 索引处理方式
  /// @param citationSubset 引用子集代码
  /// @return 当前索引历史
  public static PubmedIndexingHistory currentIndexing(
      String indexingTreatment, String citationSubset) {
    return new PubmedIndexingHistory(
        citationSubset, indexingTreatment, "Currently-indexed", null, null, null);
  }

  /// 创建历史索引记录（兼容旧 API）。
  ///
  /// @param indexingTreatment 索引处理方式
  /// @param citationSubset 引用子集代码
  /// @return 历史索引记录
  public static PubmedIndexingHistory historicalIndexing(
      String indexingTreatment, String citationSubset) {
    return new PubmedIndexingHistory(citationSubset, indexingTreatment, null, null, null, null);
  }

  /// 判断是否当前正在索引。
  ///
  /// @return true 如果当前正在索引
  public boolean isCurrentlyIndexed() {
    return "Currently-indexed".equalsIgnoreCase(indexingStatus);
  }

  /// 判断是否为 MEDLINE 索引（Index Medicus）。
  ///
  /// @return true 如果为 MEDLINE 索引
  public boolean isMedlineIndexing() {
    return "IM".equalsIgnoreCase(citationSubset);
  }

  /// 判断是否有覆盖范围信息。
  ///
  /// @return true 如果有覆盖范围
  public boolean hasCoverage() {
    return coverage != null && !coverage.isBlank();
  }

  /// 判断是否有操作日期。
  ///
  /// @return true 如果有操作日期
  public boolean hasDateOfAction() {
    return dateOfAction != null;
  }
}
