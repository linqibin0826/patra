package com.patra.catalog.domain.model.vo.venue.pubmed;

/// PubMed 期刊关联关系。
///
/// 表示从 NLM Serfile 解析出的期刊标题关联数据。
///
/// @param relatedTitle 关联期刊标题
/// @param titleType 关联类型（如 "Continues", "Continued by"）
/// @author linqibin
/// @since 0.1.0
public record PubmedTitleRelation(String relatedTitle, String titleType) {

  /// 创建关联关系。
  public static PubmedTitleRelation of(String relatedTitle, String titleType) {
    return new PubmedTitleRelation(relatedTitle, titleType);
  }

  /// 是否有有效的关联标题。
  public boolean hasValidTitle() {
    return relatedTitle != null && !relatedTitle.isBlank();
  }
}
