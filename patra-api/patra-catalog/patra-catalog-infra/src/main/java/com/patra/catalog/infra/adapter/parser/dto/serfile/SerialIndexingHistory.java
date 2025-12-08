package com.patra.catalog.infra.adapter.parser.dto.serfile;

import java.time.LocalDate;

/// Serfile 期刊索引历史解析结果。
///
/// 从 Serfile XML 的 `IndexingHistory` 元素解析出的数据传输对象。
/// 用于在解析层和领域层之间传递数据，不是领域实体。
///
/// **XML 结构示例**：
///
/// ```xml
/// <IndexingHistory CitationSubset="IM" IndexingTreatment="Full"
// IndexingStatus="Currently-indexed">
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
/// @param indexingTreatment 索引处理方式
/// @param indexingStatus 索引状态
/// @param dateOfAction 操作日期
/// @param coverage 覆盖范围描述
/// @param coverageNote 覆盖范围备注
/// @author linqibin
/// @since 0.1.0
public record SerialIndexingHistory(
    String citationSubset,
    String indexingTreatment,
    String indexingStatus,
    LocalDate dateOfAction,
    String coverage,
    String coverageNote) {

  /// 创建索引历史记录。
  ///
  /// @param citationSubset 引用子集代码
  /// @param indexingTreatment 索引处理方式
  /// @param indexingStatus 索引状态
  /// @param dateOfAction 操作日期
  /// @return 索引历史解析结果
  public static SerialIndexingHistory of(
      String citationSubset,
      String indexingTreatment,
      String indexingStatus,
      LocalDate dateOfAction) {
    return new SerialIndexingHistory(
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
  /// @return 索引历史解析结果
  public static SerialIndexingHistory of(
      String citationSubset,
      String indexingTreatment,
      String indexingStatus,
      LocalDate dateOfAction,
      String coverage,
      String coverageNote) {
    return new SerialIndexingHistory(
        citationSubset, indexingTreatment, indexingStatus, dateOfAction, coverage, coverageNote);
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
}
