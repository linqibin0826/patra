package com.patra.catalog.infra.batch.venue.scopus;

import com.patra.catalog.infra.persistence.dao.ScopusRatingDao;
import com.patra.catalog.infra.persistence.entity.ScopusRatingEntity;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

/// Scopus 期刊富化 Writer。
///
/// 通过 {@link ScopusRatingDao} 保存评级数据到 `cat_venue_scopus_rating`，
/// **过滤已存在的年份**，仅插入新年份数据。
///
/// 断点续传通过 Reader JPQL 的 `NOT EXISTS` 子查询（基于目标年份）实现。
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
        List<ScopusRatingEntity> newRatings =
            filterNewScopusRatings(result.venueId(), result.scopusRatings());
        if (!newRatings.isEmpty()) {
          scopusRatingDao.saveAll(newRatings);
          log.debug(
              "Venue [id={}] 已保存 {} 条 Scopus 评级（跳过 {} 条已存在年份）",
              result.venueId(),
              newRatings.size(),
              result.scopusRatings().size() - newRatings.size());
        }
      }
    }
  }

  /// 过滤掉数据库中已存在的年份，只返回新年份的评级数据。
  private List<ScopusRatingEntity> filterNewScopusRatings(
      Long venueId, List<ScopusRatingEntity> scopusRatings) {
    Set<Short> existingYears = scopusRatingDao.findYearsByVenueId(venueId);

    if (existingYears.isEmpty()) {
      return scopusRatings;
    }

    return scopusRatings.stream().filter(r -> !existingYears.contains(r.getYear())).toList();
  }
}
