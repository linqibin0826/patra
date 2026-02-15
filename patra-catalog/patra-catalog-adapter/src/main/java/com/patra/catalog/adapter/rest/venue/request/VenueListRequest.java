package com.patra.catalog.adapter.rest.venue.request;

/// Venue 列表查询请求。
///
/// @param page 页码（可空，由应用层归一化）
/// @param pageSize 每页大小（可空，由应用层归一化）
/// @param q displayName 模糊搜索关键词（可空）
/// @param provenanceCode 数据来源编码（可空）
/// @param countryCode 国家编码（可空）
/// @param issnL ISSN-L（可空）
/// @param nlmId NLM ID（可空）
public record VenueListRequest(
    Integer page,
    Integer pageSize,
    String q,
    String provenanceCode,
    String countryCode,
    String issnL,
    String nlmId) {}
