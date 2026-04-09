package com.patra.catalog.infra.batch.venue.letpub;

import com.patra.catalog.infra.persistence.entity.CasRatingEntity;
import com.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import java.util.List;

/// Processor → Writer 的中间传输记录。
///
/// 携带拆解后的评级数据，JCR 和 CAS 分开存储以便 Writer 写入不同的表。
///
/// @param venueId 目标 Venue 的数据库 ID
/// @param jcrRatings JCR 评级实体列表（每年一行）
/// @param casRating CAS 评级实体（单行，可为 null）
/// @author linqibin
/// @since 0.1.0
public record LetPubEnrichResult(
    Long venueId, List<JcrRatingEntity> jcrRatings, CasRatingEntity casRating) {

  /// 创建 LetPubEnrichResult 实例。
  public static LetPubEnrichResult of(
      Long venueId, List<JcrRatingEntity> jcrRatings, CasRatingEntity casRating) {
    return new LetPubEnrichResult(venueId, jcrRatings, casRating);
  }
}
