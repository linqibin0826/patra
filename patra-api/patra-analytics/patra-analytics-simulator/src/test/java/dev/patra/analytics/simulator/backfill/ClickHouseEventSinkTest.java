package dev.patra.analytics.simulator.backfill;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.infra.ingest.support.ClickHouseInserts;
import org.junit.jupiter.api.Test;

class ClickHouseEventSinkTest {

  @Test
  void insert_settings_match_ingest() {
    assertThat(ClickHouseEventSink.settings("t").getAllSettings())
        .isEqualTo(ClickHouseInserts.settings("t").getAllSettings());
  }
}
