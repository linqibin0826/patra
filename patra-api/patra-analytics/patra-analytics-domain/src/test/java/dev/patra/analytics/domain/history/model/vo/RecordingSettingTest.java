package dev.patra.analytics.domain.history.model.vo;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RecordingSettingTest {

  private static final Instant CLEARED = Instant.parse("2026-11-02T08:00:00Z");

  @Test
  void accepts_only_after_cleared() {
    RecordingSetting cleared = RecordingSetting.of(7L, true, CLEARED);

    assertThat(cleared.accepts(CLEARED)).isFalse();
    assertThat(cleared.accepts(CLEARED.minusMillis(1))).isFalse();
    assertThat(cleared.accepts(CLEARED.plusMillis(1))).isTrue();
    assertThat(RecordingSetting.of(7L, false, null).accepts(CLEARED)).isFalse();
  }

  @Test
  void defaults_record_everything() {
    RecordingSetting defaults = RecordingSetting.defaults(7L);

    assertThat(defaults.enabled()).isTrue();
    assertThat(defaults.clearedAt()).isNull();
    assertThat(defaults.accepts(Instant.EPOCH)).isTrue();
  }
}
