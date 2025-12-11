package com.patra.catalog.domain.model.vo.venue.pubmed;

import java.util.List;

/// PubMed 期刊关联关系。
///
/// 表示从 NLM Serfile 解析出的期刊标题关联数据。
///
/// **XML 结构示例**：
///
/// ```xml
/// <TitleRelated TitleType="Preceding">
///   <Title>Previous Journal Name</Title>
///   <RecordID Source="NLM">12345678</RecordID>
///   <RecordID Source="OCLC">98765432</RecordID>
///   <ISSN IssnType="Print">1234-5678</ISSN>
/// </TitleRelated>
/// ```
///
/// **关系类型映射**：
///
/// | DTD TitleType | 领域 VenueRelationType |
/// |---------------|------------------------|
/// | Preceding | PRECEDING |
/// | Succeeding | SUCCEEDING |
/// | Absorbed | ABSORBED |
/// | AbsorbedBy | ABSORBED_BY |
/// | MergedTo | MERGED |
/// | SplitFrom | SPLIT_FROM |
/// | ContinuedBy | CONTINUED_BY |
/// | Continues | CONTINUES |
///
/// @param titleType 关联类型（如 "Continues", "Continued by"）
/// @param relatedTitle 关联期刊标题
/// @param relatedIssn 关联期刊 ISSN（可选）
/// @param recordIds 关联记录 ID 列表（可选）
/// @author linqibin
/// @since 0.1.0
public record PubmedTitleRelation(
    String titleType, String relatedTitle, String relatedIssn, List<PubmedRecordId> recordIds) {

  /// 规范化构造函数，确保 recordIds 不为 null。
  public PubmedTitleRelation {
    recordIds = recordIds != null ? recordIds : List.of();
  }

  /// 创建关联关系（基本版）。
  ///
  /// @param titleType 关系类型
  /// @param relatedTitle 关联期刊标题
  /// @return 期刊关联关系
  public static PubmedTitleRelation of(String titleType, String relatedTitle) {
    return new PubmedTitleRelation(titleType, relatedTitle, null, List.of());
  }

  /// 创建带 ISSN 的关联关系。
  ///
  /// @param titleType 关系类型
  /// @param relatedTitle 关联期刊标题
  /// @param relatedIssn 关联期刊 ISSN
  /// @return 期刊关联关系
  public static PubmedTitleRelation of(String titleType, String relatedTitle, String relatedIssn) {
    return new PubmedTitleRelation(titleType, relatedTitle, relatedIssn, List.of());
  }

  /// 创建完整的关联关系。
  ///
  /// @param titleType 关系类型
  /// @param relatedTitle 关联期刊标题
  /// @param relatedIssn 关联期刊 ISSN
  /// @param recordIds 关联记录 ID 列表
  /// @return 期刊关联关系
  public static PubmedTitleRelation ofFull(
      String titleType, String relatedTitle, String relatedIssn, List<PubmedRecordId> recordIds) {
    return new PubmedTitleRelation(titleType, relatedTitle, relatedIssn, recordIds);
  }

  /// 是否有有效的关联标题。
  public boolean hasValidTitle() {
    return relatedTitle != null && !relatedTitle.isBlank();
  }

  /// 判断是否有关联 ISSN。
  ///
  /// @return true 如果有 ISSN
  public boolean hasIssn() {
    return relatedIssn != null && !relatedIssn.isBlank();
  }

  /// 判断是否有关联记录 ID。
  ///
  /// @return true 如果有 RecordID
  public boolean hasRecordIds() {
    return !recordIds.isEmpty();
  }

  /// 获取 NLM 来源的记录 ID。
  ///
  /// @return NLM 记录 ID，不存在则返回 null
  public String getNlmRecordId() {
    return recordIds.stream()
        .filter(PubmedRecordId::isNlm)
        .map(PubmedRecordId::id)
        .findFirst()
        .orElse(null);
  }
}
