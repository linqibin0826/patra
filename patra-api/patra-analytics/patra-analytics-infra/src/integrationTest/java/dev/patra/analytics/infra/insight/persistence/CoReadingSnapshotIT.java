package dev.patra.analytics.infra.insight.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.domain.insight.model.vo.CoReadingEntry;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class CoReadingSnapshotIT extends InsightPersistenceITSupport {

  private static final Instant AT = Instant.parse("2026-11-02T20:30:00Z");

  @Autowired private CoReadingSnapshotRepositoryAdapter snapshots;
  @Autowired private CoReadingReadAdapter reads;

  @Test
  void replace_and_read_in_position_order() {
    List<CoReadingEntry> entries = new ArrayList<>();
    for (long x = 1; x <= 600; x++) {
      for (int position = 20; position >= 1; position--) {
        entries.add(entry(x, position, 10_000 + x * 100 + position));
      }
    }
    replace(List.of(entry(9_999L, 1, 1L)));
    replace(entries);

    assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM insight.co_reading", Long.class))
        .isEqualTo(12_000L);
    assertThat(reads.relatedOf(9_999L, 6)).isEmpty();
    assertThat(reads.relatedOf(7L, 3)).containsExactly(10_701L, 10_702L, 10_703L);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT row_count FROM insight.snapshot_run WHERE kind = 'CO_READING'",
                Integer.class))
        .isEqualTo(12_000);
  }

  @Test
  void related_of_limits() {
    List<CoReadingEntry> entries = new ArrayList<>();
    for (int position = 1; position <= 20; position++) {
      entries.add(entry(1L, position, 100L + position));
    }
    replace(entries);

    assertThat(reads.relatedOf(1L, 6)).hasSize(6).first().isEqualTo(101L);
    assertThat(reads.relatedOf(2L, 6)).isEmpty();
  }

  private void replace(List<CoReadingEntry> entries) {
    transactions.executeWithoutResult(status -> snapshots.replace(entries, AT));
  }

  private static CoReadingEntry entry(long x, int position, long y) {
    return CoReadingEntry.builder()
        .articleId(x)
        .position(position)
        .relatedArticleId(y)
        .similarity(1.0 / position)
        .coReaders(3)
        .build();
  }
}
