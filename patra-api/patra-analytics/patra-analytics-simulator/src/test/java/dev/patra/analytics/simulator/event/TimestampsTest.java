package dev.patra.analytics.simulator.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class TimestampsTest {

  @Test
  void formats_exactly_three_millis_digits() {
    assertThat(Timestamps.format(Instant.parse("2026-11-01T08:30:10Z")))
        .isEqualTo("2026-11-01T08:30:10.000Z");
    assertThat(Timestamps.format(Instant.parse("2026-11-01T08:30:10.123456Z")))
        .isEqualTo("2026-11-01T08:30:10.123Z");
  }
}
