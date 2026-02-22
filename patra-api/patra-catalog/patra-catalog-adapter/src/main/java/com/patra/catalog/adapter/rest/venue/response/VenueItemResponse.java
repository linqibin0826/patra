package com.patra.catalog.adapter.rest.venue.response;

import java.time.Instant;

/// Venue 列表项响应。
///
/// @param id Venue ID
/// @param title 标题
/// @param titleZh 中文标题（可空）
/// @param issnL ISSN-L
/// @param nlmId NLM ID
/// @param countryCode 国家编码
/// @param lastSyncedAt 最后同步时间
public record VenueItemResponse(
    Long id,
    String title,
    String titleZh,
    String issnL,
    String nlmId,
    String countryCode,
    Instant lastSyncedAt) {}
