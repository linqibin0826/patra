package com.patra.catalog.domain.model.vo.venue.pubmed;

/// PubMed 期刊 MeSH 主题词。
///
/// 表示从 NLM Serfile 解析出的 MeSH 主题词数据。
///
/// @param descriptorName 主题词描述符名称
/// @param qualifierName 限定词名称（可选）
/// @param isMajorTopic 是否为主要主题
/// @author linqibin
/// @since 0.1.0
public record PubmedMeshHeading(String descriptorName, String qualifierName, boolean isMajorTopic) {

  /// 创建不带限定词的主题词。
  public static PubmedMeshHeading of(String descriptorName, boolean isMajorTopic) {
    return new PubmedMeshHeading(descriptorName, null, isMajorTopic);
  }

  /// 创建带限定词的主题词。
  public static PubmedMeshHeading withQualifier(
      String descriptorName, String qualifierName, boolean isMajorTopic) {
    return new PubmedMeshHeading(descriptorName, qualifierName, isMajorTopic);
  }

  /// 是否有限定词。
  public boolean hasQualifier() {
    return qualifierName != null && !qualifierName.isBlank();
  }
}
