package com.patra.catalog.domain.model.dto.serfile;

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
/// @author linqibin
/// @since 0.1.0
public record SerialTitleRelated(String titleType, String relatedTitle, String relatedIssn) {

  /// 创建期刊关联关系。
  ///
  /// @param titleType 关系类型
  /// @param relatedTitle 关联期刊标题
  /// @return 期刊关联关系解析结果
  public static SerialTitleRelated of(String titleType, String relatedTitle) {
    return new SerialTitleRelated(titleType, relatedTitle, null);
  }

  /// 创建带 ISSN 的期刊关联关系。
  ///
  /// @param titleType 关系类型
  /// @param relatedTitle 关联期刊标题
  /// @param relatedIssn 关联期刊 ISSN
  /// @return 期刊关联关系解析结果
  public static SerialTitleRelated of(String titleType, String relatedTitle, String relatedIssn) {
    return new SerialTitleRelated(titleType, relatedTitle, relatedIssn);
  }

  /// 判断是否有关联 ISSN。
  ///
  /// @return true 如果有 ISSN
  public boolean hasIssn() {
    return relatedIssn != null && !relatedIssn.isBlank();
  }
}
