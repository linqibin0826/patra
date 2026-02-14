package com.patra.catalog.app.usecase.venue.query.dto;

/// Venue 列表查询参数。
///
/// @param page 页码（可空，由应用层归一化）
/// @param pageSize 每页大小（可空，由应用层归一化）
/// @param q 搜索关键词（可空）
public record VenueListQuery(Integer page, Integer pageSize, String q) {}
