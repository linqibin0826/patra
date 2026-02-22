package com.patra.catalog.adapter.rest.publication.request;

/// Publication 列表查询请求。
///
/// @param page 页码（可空，由应用层归一化）
/// @param pageSize 每页大小（可空，由应用层归一化）
/// @param q 标题模糊搜索关键词（可空）
/// @param yearFrom 起始年份（含，可空）
/// @param yearTo 截止年份（含，可空）
/// @param languageBase 基础语种代码（可空）
/// @param isOa 是否有 OA 版本（可空）
/// @param oaStatus OA 状态（可空）
/// @param venueId 载体 ID（可空）
/// @param pmid PubMed ID（可空）
/// @param doi DOI（可空）
/// @param provenanceCode 数据来源代码（可空）
/// @param publicationStatus 出版状态（可空）
public record PublicationListRequest(
    Integer page,
    Integer pageSize,
    String q,
    Integer yearFrom,
    Integer yearTo,
    String languageBase,
    Boolean isOa,
    String oaStatus,
    Long venueId,
    String pmid,
    String doi,
    String provenanceCode,
    String publicationStatus) {}
