package com.patra.catalog.adapter.rest.venue.response;

/// Venue 实例列表项响应。
///
/// @param id 实例主键 ID
/// @param volume 卷号
/// @param issue 期号
/// @param publicationYear 出版年份
/// @param publicationMonth 出版月份
/// @param publicationDay 出版日期
/// @param publicationCount 关联文献数量
public record VenueInstanceItemResponse(
    Long id,
    String volume,
    String issue,
    Integer publicationYear,
    Integer publicationMonth,
    Integer publicationDay,
    long publicationCount) {}
