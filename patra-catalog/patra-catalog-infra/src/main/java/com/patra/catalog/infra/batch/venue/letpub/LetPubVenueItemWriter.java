package com.patra.catalog.infra.batch.venue.letpub;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/// LetPub 期刊富化 Writer。
///
/// 使用 JdbcTemplate 直接更新 `cat_venue.letpub_data` JSON 列，
/// 避免重建完整聚合根的开销。同时更新 `updated_at` 和 `version` 字段。
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@RequiredArgsConstructor
public class LetPubVenueItemWriter implements ItemWriter<LetPubEnrichResult> {

  private static final String UPDATE_SQL =
      """
      UPDATE cat_venue
      SET letpub_data = ?, updated_at = CURRENT_TIMESTAMP(6), version = version + 1
      WHERE id = ?
      """;

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  /// 将 LetPub 富化数据写入数据库。
  ///
  /// 逐条更新 `cat_venue.letpub_data` JSON 列（chunk size = 1，无需批量优化）。
  @Override
  public void write(Chunk<? extends LetPubEnrichResult> results) throws Exception {
    for (LetPubEnrichResult result : results) {
      String json = objectMapper.writeValueAsString(result.data());
      jdbcTemplate.update(UPDATE_SQL, json, result.venueId());
      log.debug("Venue [id={}] letpub_data 已更新", result.venueId());
    }
  }
}
