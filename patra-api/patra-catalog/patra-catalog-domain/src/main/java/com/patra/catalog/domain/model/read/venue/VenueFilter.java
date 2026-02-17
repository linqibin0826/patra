package com.patra.catalog.domain.model.read.venue;

import lombok.Builder;

/// Venue 列表查询筛选条件。
///
/// 封装 Venue 分页查询的所有筛选参数，所有字段均可为 null（表示不筛选）。
///
/// @param keyword title/titleZh 前缀模糊搜索（可空）
/// @param provenanceCode 数据来源编码精确匹配（可空）
/// @param countryCode 国家编码精确匹配（可空）
/// @param issnL ISSN-L 精确匹配（可空）
/// @param nlmId NLM ID 精确匹配（可空）
@Builder
public record VenueFilter(
    String keyword, String provenanceCode, String countryCode, String issnL, String nlmId) {}
