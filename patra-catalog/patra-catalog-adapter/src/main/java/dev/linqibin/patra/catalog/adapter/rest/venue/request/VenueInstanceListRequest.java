package dev.linqibin.patra.catalog.adapter.rest.venue.request;

/// Venue 实例列表查询请求。
///
/// @param page 页码（可空，由应用层归一化）
/// @param pageSize 每页大小（可空，由应用层归一化）
/// @param year 出版年份过滤（可空）
public record VenueInstanceListRequest(Integer page, Integer pageSize, Integer year) {}
