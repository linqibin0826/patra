package com.patra.catalog.infra.batch.venue.letpub;

import com.patra.catalog.domain.model.vo.venue.LetPubVenueData;

/// Processor → Writer 的中间传输记录。
///
/// 携带目标 Venue ID 和爬取到的 LetPub 数据，
/// 由 Writer 用于定向更新 `cat_venue.letpub_data` JSON 列。
///
/// @param venueId 目标 Venue 的数据库 ID
/// @param data    LetPub 期刊评价数据
/// @author linqibin
/// @since 0.1.0
public record LetPubEnrichResult(Long venueId, LetPubVenueData data) {

  /// 创建 LetPubEnrichResult 实例。
  public static LetPubEnrichResult of(Long venueId, LetPubVenueData data) {
    return new LetPubEnrichResult(venueId, data);
  }
}
