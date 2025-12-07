package com.patra.catalog.domain.model.dto.serfile;

import java.util.List;

/// Serfile 期刊关联关系解析结果。
///
/// 从 Serfile XML 的 `TitleRelated` 元素解析出的数据传输对象。
/// 用于在解析层和领域层之间传递数据，不是领域实体。
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
/// @param titleType 关系类型（来自 DTD TitleType 属性）
/// @param relatedTitle 关联期刊标题（必填）
/// @param relatedIssn 关联期刊 ISSN（可选）
/// @param recordIds 关联记录 ID 列表（可选）
/// @author linqibin
/// @since 0.1.0
public record SerialTitleRelated(
    String titleType, String relatedTitle, String relatedIssn, List<SerialRecordId> recordIds) {

  /// 规范化构造函数，确保 recordIds 不为 null。
  public SerialTitleRelated {
    recordIds = recordIds != null ? recordIds : List.of();
  }

  /// 创建期刊关联关系（无 ISSN 和 RecordID）。
  ///
  /// @param titleType 关系类型
  /// @param relatedTitle 关联期刊标题
  /// @return 期刊关联关系解析结果
  public static SerialTitleRelated of(String titleType, String relatedTitle) {
    return new SerialTitleRelated(titleType, relatedTitle, null, List.of());
  }

  /// 创建带 ISSN 的期刊关联关系（无 RecordID）。
  ///
  /// @param titleType 关系类型
  /// @param relatedTitle 关联期刊标题
  /// @param relatedIssn 关联期刊 ISSN
  /// @return 期刊关联关系解析结果
  public static SerialTitleRelated of(String titleType, String relatedTitle, String relatedIssn) {
    return new SerialTitleRelated(titleType, relatedTitle, relatedIssn, List.of());
  }

  /// 创建完整的期刊关联关系。
  ///
  /// @param titleType 关系类型
  /// @param relatedTitle 关联期刊标题
  /// @param relatedIssn 关联期刊 ISSN
  /// @param recordIds 关联记录 ID 列表
  /// @return 期刊关联关系解析结果
  public static SerialTitleRelated ofFull(
      String titleType, String relatedTitle, String relatedIssn, List<SerialRecordId> recordIds) {
    return new SerialTitleRelated(titleType, relatedTitle, relatedIssn, recordIds);
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
        .filter(SerialRecordId::isNlm)
        .map(SerialRecordId::id)
        .findFirst()
        .orElse(null);
  }
}
