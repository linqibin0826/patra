package dev.patra.catalog.infra.adapter.read;

/// 文献详情主行投影接口，对应 `PublicationDetailDao.findDetailById` 查询结果。
///
/// @author linqibin
/// @since 0.1.0
public interface PublicationDetailRow {

  Long getId();

  String getTitle();

  String getOriginalTitle();

  Long getVenueId();

  String getVenueName();

  Integer getPublicationYear();

  String getAbstractType();

  /// 结构化摘要段落 JSON 字符串（原始 jsonb 转换为文本）。
  String getStructuredSectionsJson();

  String getAbstractPlainText();

  String getDoi();

  String getPmid();

  String getPmcid();

  String getPii();

  Integer getCitationCount();

  Integer getNumberOfReferences();

  String getConflictOfInterest();

  Boolean getIsOa();

  String getOaStatus();

  /// 数据来源代码（PUBMED / EPMC / CROSSREF 等），来自 cat_publication.provenance_code。
  String getProvenanceCode();

  /// 全文链接，来自 cat_publication_metadata.full_text_url（LEFT JOIN，无记录时为 null）。
  String getFullTextUrl();

  /// 出版类型列表，以 U+001F（\x1f）分隔，按 type_order ASC、id ASC 排序。
  String getPublicationTypesAgg();
}
