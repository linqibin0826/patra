package dev.patra.catalog.infra.portal.persistence;

/// [PublicationDetailReadAdapter] 的 SQL 查询行，组件名与列别名一一对应。
///
/// @param publicationMonth 出版月。
/// @param publicationDay 出版日。
/// @param structuredSectionsJson 结构化摘要段落 JSON 字符串（原始 jsonb 转换为文本）。
/// @param provenanceCode 数据来源代码（PUBMED / EPMC / CROSSREF 等），来自 cat_publication.provenance_code。
/// @param fullTextUrl 全文链接，来自 cat_publication_metadata.full_text_url（LEFT JOIN，无记录时为 null）。
/// @param publicationTypesAgg 出版类型列表，以 U+001F（\x1f）分隔，按 type_order ASC、id ASC 排序。
/// @param fullTextStatus 全文记录状态（枚举名），没有全文记录时为空。
/// @param hasPdfObject 是否有 PDF 对象。
/// @param pdfSize PDF 字节数。
/// @param wordCount 正文字数。
/// @param retracted 是否撤稿文献：PubMed 发表类型含 `D016441`，或抓取时 PMC 标为撤稿（全文不可用原因 `RETRACTED`）。
/// @author linqibin
/// @since 0.1.0
record PublicationDetailRow(
    Long id,
    String title,
    String originalTitle,
    Long venueId,
    String venueName,
    Integer publicationYear,
    Integer publicationMonth,
    Integer publicationDay,
    String abstractType,
    String structuredSectionsJson,
    String abstractPlainText,
    String doi,
    String pmid,
    String pmcid,
    String pii,
    Integer citationCount,
    Integer numberOfReferences,
    String conflictOfInterest,
    Boolean isOa,
    String oaStatus,
    String provenanceCode,
    String fullTextUrl,
    String publicationTypesAgg,
    String fullTextStatus,
    Boolean hasPdfObject,
    Long pdfSize,
    Integer wordCount,
    Boolean retracted) {}
