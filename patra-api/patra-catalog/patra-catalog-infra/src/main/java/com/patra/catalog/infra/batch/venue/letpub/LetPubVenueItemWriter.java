package com.patra.catalog.infra.batch.venue.letpub;

import com.patra.catalog.infra.persistence.dao.CasRatingDao;
import com.patra.catalog.infra.persistence.dao.JcrRatingDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;

/// LetPub 期刊富化 Writer。
///
/// 三步写入：
///
/// 1. 通过 {@link JcrRatingDao} 保存 JCR 评级行（→ `cat_venue_jcr_rating`）
/// 2. 通过 {@link CasRatingDao} 保存 CAS 评级行（→ `cat_venue_cas_rating`）
/// 3. 通过 {@link JdbcTemplate} 更新 `cat_venue.letpub_fetched_at`（断点续传标记）
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@RequiredArgsConstructor
public class LetPubVenueItemWriter implements ItemWriter<LetPubEnrichResult> {

  private static final String UPDATE_FETCHED_AT_SQL =
      """
      UPDATE cat_venue
      SET letpub_fetched_at = CURRENT_TIMESTAMP(6), updated_at = CURRENT_TIMESTAMP(6), version = version + 1
      WHERE id = ?
      """;

  private final JcrRatingDao jcrRatingDao;
  private final CasRatingDao casRatingDao;
  private final JdbcTemplate jdbcTemplate;

  /// 将 LetPub 富化数据写入数据库。
  @Override
  public void write(Chunk<? extends LetPubEnrichResult> results) throws Exception {
    for (LetPubEnrichResult result : results) {
      // Step 1: 保存 JCR 评级
      if (result.jcrRatings() != null && !result.jcrRatings().isEmpty()) {
        jcrRatingDao.saveAll(result.jcrRatings());
        log.debug("Venue [id={}] 已保存 {} 条 JCR 评级", result.venueId(), result.jcrRatings().size());
      }

      // Step 2: 保存 CAS 评级
      if (result.casRating() != null) {
        casRatingDao.save(result.casRating());
        log.debug("Venue [id={}] 已保存 CAS 评级", result.venueId());
      }

      // Step 3: 标记已抓取（断点续传）
      jdbcTemplate.update(UPDATE_FETCHED_AT_SQL, result.venueId());
    }
  }
}
