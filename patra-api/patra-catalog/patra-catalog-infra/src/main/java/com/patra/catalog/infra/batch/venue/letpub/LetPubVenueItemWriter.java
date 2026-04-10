package com.patra.catalog.infra.batch.venue.letpub;

import com.patra.catalog.infra.persistence.dao.CasRatingDao;
import com.patra.catalog.infra.persistence.dao.JcrRatingDao;
import com.patra.catalog.infra.persistence.entity.CasRatingEntity;
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
/// 2. 通过 {@link CasRatingDao} 保存 CAS 评级行（→ `cat_venue_cas_rating`），
///    **过滤已存在的 `(年份, 版本)` 组合**，仅插入新版本数据
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
      if (!result.jcrRatings().isEmpty()) {
        List<JcrRatingEntity> newJcrRatings =
            filterNewJcrRatings(result.venueId(), result.jcrRatings());
        if (!newJcrRatings.isEmpty()) {
          jcrRatingDao.saveAll(newJcrRatings);
          log.debug(
              "Venue [id={}] 已保存 {} 条 JCR 评级（跳过 {} 条已存在年份）",
              result.venueId(),
              newJcrRatings.size(),
              result.jcrRatings().size() - newJcrRatings.size());
        }
      }

      if (!result.casRatings().isEmpty()) {
        List<CasRatingEntity> newCasRatings =
            filterNewCasRatings(result.venueId(), result.casRatings());
        if (!newCasRatings.isEmpty()) {
          casRatingDao.saveAll(newCasRatings);
          log.debug(
              "Venue [id={}] 已保存 {} 条 CAS 评级（跳过 {} 条已存在版本）",
              result.venueId(),
              newCasRatings.size(),
              result.casRatings().size() - newCasRatings.size());
        }
      }
    }
  }

  /// 过滤掉数据库中已存在的年份，只返回新年份的 JCR 评级。
  private List<JcrRatingEntity> filterNewJcrRatings(
      Long venueId, List<JcrRatingEntity> jcrRatings) {
    Set<Short> existingYears = jcrRatingDao.findYearsByVenueId(venueId);

    if (existingYears.isEmpty()) {
      return jcrRatings;
    }

    return jcrRatings.stream().filter(r -> !existingYears.contains(r.getYear())).toList();
  }

  /// 过滤掉数据库中已存在的 `(年份, 版本)` 组合，只返回新版本的 CAS 评级。
  ///
  /// 通过投影查询仅载入 `(year:edition)` 键集合，避免拉取完整 CAS 实体字段。
  private List<CasRatingEntity> filterNewCasRatings(
      Long venueId, List<CasRatingEntity> casRatings) {
    Set<String> existingKeys = casRatingDao.findKeysByVenueId(venueId);
    if (existingKeys.isEmpty()) {
      return casRatings;
    }

    return casRatings.stream()
        .filter(r -> !existingKeys.contains(r.getYear() + ":" + r.getEdition()))
        .toList();
  }
}
