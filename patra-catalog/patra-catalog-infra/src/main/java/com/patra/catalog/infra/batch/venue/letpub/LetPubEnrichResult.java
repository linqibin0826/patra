package com.patra.catalog.infra.batch.venue.letpub;

import com.patra.catalog.infra.persistence.entity.CasRatingEntity;
import com.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import java.util.List;

/// Processor → Writer 的中间传输记录。
///
/// 携带拆解后的评级数据，JCR 和 CAS 分开存储以便 Writer 写入不同的表。
/// 两者都是列表：JCR 每年一行，CAS 每个版本一行（新锐版/升级版/旧版）。
///
/// @param venueId 目标 Venue 的数据库 ID
/// @param jcrRatings JCR 评级实体列表（每年一行，不可变）
/// @param casRatings CAS 评级实体列表（每个版本一行，不可变）
/// @author linqibin
/// @since 0.1.0
public record LetPubEnrichResult(
    Long venueId, List<JcrRatingEntity> jcrRatings, List<CasRatingEntity> casRatings) {

  /// 紧凑构造器：对集合字段进行防御性拷贝。
  public LetPubEnrichResult {
    jcrRatings = jcrRatings != null ? List.copyOf(jcrRatings) : List.of();
    casRatings = casRatings != null ? List.copyOf(casRatings) : List.of();
  }

  /// 创建 LetPubEnrichResult 实例。
  public static LetPubEnrichResult of(
      Long venueId, List<JcrRatingEntity> jcrRatings, List<CasRatingEntity> casRatings) {
    return new LetPubEnrichResult(venueId, jcrRatings, casRatings);
  }
}
