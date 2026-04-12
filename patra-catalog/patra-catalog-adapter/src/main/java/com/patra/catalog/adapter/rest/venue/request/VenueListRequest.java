package com.patra.catalog.adapter.rest.venue.request;

/// Venue 列表查询请求。
///
/// @param page 页码（可空，由应用层归一化）
/// @param pageSize 每页大小（可空，由应用层归一化）
/// @param q title 模糊搜索关键词（可空）
/// @param countryCode 国家编码（可空）
/// @param issnL ISSN-L（可空）
/// @param nlmId NLM ID（可空）
/// @param jifQuartile JCR JIF 分区过滤，如 Q1/Q2/Q3/Q4（可空）
/// @param casMajorQuartile CAS 大类分区过滤，如 1区/2区/3区/4区（可空）
/// @param casTopJournal 是否仅查 CAS Top 期刊（可空）
/// @param oaType OA 类型过滤，如 gold/hybrid/bronze（可空）
/// @param collection JCR 收录集过滤，如 SCIE/SSCI/AHCI（可空）
/// @param researchDirection 研究方向关键词匹配（可空）
/// @param warningOnly 是否仅查预警期刊（可空）
/// @param sortBy 排序字段：impactFactor/citeScore/hIndex/citedByCount（可空，默认 citedByCount）
public record VenueListRequest(
    Integer page,
    Integer pageSize,
    String q,
    String countryCode,
    String issnL,
    String nlmId,
    String jifQuartile,
    String casMajorQuartile,
    Boolean casTopJournal,
    String oaType,
    String collection,
    String researchDirection,
    Boolean warningOnly,
    String sortBy) {}
