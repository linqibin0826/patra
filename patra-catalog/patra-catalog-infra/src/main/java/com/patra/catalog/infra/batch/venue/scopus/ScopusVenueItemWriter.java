package com.patra.catalog.infra.batch.venue.scopus;

import com.patra.catalog.infra.persistence.dao.ScopusRatingDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

/// Scopus 期刊富化 Writer。
///
/// 通过 {@link ScopusRatingDao} 保存评级数据到 `cat_venue_scopus_rating`。
/// 无需更新 VenueEntity — 断点续传通过 Reader JPQL 的 NOT EXISTS 子查询实现。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@RequiredArgsConstructor
public class ScopusVenueItemWriter implements ItemWriter<ScopusEnrichResult> {

  private final ScopusRatingDao scopusRatingDao;

  /// 将 Scopus 富化数据写入数据库。
  @Override
  public void write(Chunk<? extends ScopusEnrichResult> results) throws Exception {
    for (ScopusEnrichResult result : results) {
      if (!result.scopusRatings().isEmpty()) {
        scopusRatingDao.saveAll(result.scopusRatings());
        log.debug(
            "Venue [id={}] 已保存 {} 条 Scopus 评级", result.venueId(), result.scopusRatings().size());
      }
    }
  }
}
