package com.patra.catalog.adapter.rest.venue.request;

/// Venue 实例文献列表查询请求。
///
/// @param page 页码（可空，由应用层归一化）
/// @param pageSize 每页大小（可空，由应用层归一化）
/// @param sortBy 排序字段（可空，支持 "citedByCount"）
public record InstancePublicationListRequest(Integer page, Integer pageSize, String sortBy) {}
