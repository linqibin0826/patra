package com.patra.catalog.infra.batch.venue.scopus;

import com.patra.catalog.infra.persistence.entity.ScopusRatingEntity;
import java.util.List;

/// Processor → Writer 的中间传输记录。
///
/// 携带从 Scopus API 获取并映射后的评级数据。
///
/// @param venueId 目标 Venue 的数据库 ID
/// @param scopusRatings Scopus 评级实体列表（每年一行）
/// @author linqibin
/// @since 0.1.0
public record ScopusEnrichResult(Long venueId, List<ScopusRatingEntity> scopusRatings) {

  /// 紧凑构造器：对 `scopusRatings` 进行防御性拷贝。
  public ScopusEnrichResult {
    scopusRatings = scopusRatings != null ? List.copyOf(scopusRatings) : List.of();
  }

  /// 创建 ScopusEnrichResult 实例。
  public static ScopusEnrichResult of(Long venueId, List<ScopusRatingEntity> scopusRatings) {
    return new ScopusEnrichResult(venueId, scopusRatings);
  }
}
