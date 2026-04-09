package com.patra.catalog.infra.batch.venue.letpub;

import com.patra.catalog.infra.persistence.dao.CasRatingDao;
import com.patra.catalog.infra.persistence.dao.JcrRatingDao;
import com.patra.catalog.infra.persistence.entity.JcrRatingEntity;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;

/// LetPub 期刊富化 Writer。
///
/// 两步写入：
///
/// 1. 通过 {@link JcrRatingDao} 保存 JCR 评级行（→ `cat_venue_jcr_rating`），
///    **过滤已存在的年份**，仅插入新年份数据
/// 2. 通过 {@link CasRatingDao} 保存 CAS 评级行（→ `cat_venue_cas_rating`）
///
/// **断点续传**：不再依赖 `letpub_fetched_at` 标记字段，
/// 改由 Reader 的 `NOT EXISTS` 子查询（基于目标年份）实现。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@RequiredArgsConstructor
public class LetPubVenueItemWriter implements ItemWriter<LetPubEnrichResult> {

  private final JcrRatingDao jcrRatingDao;
  private final CasRatingDao casRatingDao;

  /// 将 LetPub 富化数据写入数据库。
  @Override
  public void write(Chunk<? extends LetPubEnrichResult> results) throws Exception {
    for (LetPubEnrichResult result : results) {
      // Step 1: 保存 JCR 评级（过滤已存在的年份）
      if (result.jcrRatings() != null && !result.jcrRatings().isEmpty()) {
        List<JcrRatingEntity> newRatings =
            filterNewJcrRatings(result.venueId(), result.jcrRatings());
        if (!newRatings.isEmpty()) {
          jcrRatingDao.saveAll(newRatings);
          log.debug(
              "Venue [id={}] 已保存 {} 条 JCR 评级（跳过 {} 条已存在年份）",
              result.venueId(),
              newRatings.size(),
              result.jcrRatings().size() - newRatings.size());
        }
      }

      // Step 2: 保存 CAS 评级
      if (result.casRating() != null) {
        casRatingDao.save(result.casRating());
        log.debug("Venue [id={}] 已保存 CAS 评级", result.venueId());
      }
    }
  }

  /// 过滤掉数据库中已存在的年份，只返回新年份的评级数据。
  private List<JcrRatingEntity> filterNewJcrRatings(
      Long venueId, List<JcrRatingEntity> jcrRatings) {
    Set<Short> existingYears = jcrRatingDao.findYearsByVenueId(venueId);

    if (existingYears.isEmpty()) {
      return jcrRatings;
    }

    return jcrRatings.stream().filter(r -> !existingYears.contains(r.getYear())).toList();
  }
}
