package com.patra.catalog.app.usecase.venue.query.dto;

import lombok.Builder;

/// Venue 列表查询参数。
///
/// @param page 页码（可空，由应用层归一化）
/// @param pageSize 每页大小（可空，由应用层归一化）
/// @param q title/titleZh 模糊搜索关键词（可空）
/// @param countryCode 国家编码（可空）
/// @param issnL ISSN-L（可空）
/// @param nlmId NLM ID（可空）
@Builder
public record VenueListQuery(
    Integer page, Integer pageSize, String q, String countryCode, String issnL, String nlmId) {}
