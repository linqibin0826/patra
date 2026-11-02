package dev.patra.analytics.simulator.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class BeijingDaysTest {

  @Test
  void half_past_midnight_beijing_is_that_day() {
    assertThat(BeijingDays.of(Instant.parse("2026-10-31T16:30:00Z")))
        .isEqualTo(LocalDate.of(2026, 11, 1));
  }
}
