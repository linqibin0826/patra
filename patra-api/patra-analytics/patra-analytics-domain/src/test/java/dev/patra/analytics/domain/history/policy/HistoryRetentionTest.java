package dev.patra.analytics.domain.history.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class HistoryRetentionTest {

  @Test
  void history_cutoff_is_twelve_calendar_months() {
    assertThat(HistoryRetention.historyCutoff(Instant.parse("2026-03-31T00:00:00Z")))
        .isEqualTo(Instant.parse("2025-03-31T00:00:00Z"));
    assertThat(HistoryRetention.historyCutoff(Instant.parse("2028-02-29T12:00:00Z")))
        .isEqualTo(Instant.parse("2027-02-28T12:00:00Z"));
  }

  @Test
  void deletion_cutoff_is_two_days() {
    assertThat(HistoryRetention.deletionCutoff(Instant.parse("2026-11-02T06:40:00Z")))
        .isEqualTo(Instant.parse("2026-10-31T06:40:00Z"));
  }
}
