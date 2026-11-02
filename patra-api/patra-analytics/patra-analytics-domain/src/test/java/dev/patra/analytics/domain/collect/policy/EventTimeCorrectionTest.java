package dev.patra.analytics.domain.collect.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class EventTimeCorrectionTest {

  private static final Instant RECEIVED = Instant.parse("2026-11-01T09:00:05Z");

  @Test
  void derive_corrects_client_clock_skew() {
    Instant created = Instant.parse("2026-11-01T08:00:00Z");
    Instant sent = Instant.parse("2026-11-01T08:00:02Z");

    assertThat(EventTimeCorrection.derive(created, sent, RECEIVED))
        .isEqualTo(Instant.parse("2026-11-01T09:00:03Z"));
  }

  @Test
  void derive_falls_back_when_more_than_24h_early() {
    Instant created = Instant.parse("2026-10-30T09:00:00Z");
    Instant sent = Instant.parse("2026-11-01T09:00:05Z");

    assertThat(EventTimeCorrection.derive(created, sent, RECEIVED)).isEqualTo(RECEIVED);
  }

  @Test
  void derive_falls_back_when_more_than_5min_late() {
    Instant created = Instant.parse("2026-11-01T09:00:00Z");
    Instant sent = created.minus(Duration.ofMinutes(6));

    assertThat(EventTimeCorrection.derive(created, sent, RECEIVED)).isEqualTo(RECEIVED);
  }

  @Test
  void derive_keeps_value_exactly_at_bounds() {
    Instant sent = Instant.parse("2026-11-01T08:00:00Z");

    assertThat(EventTimeCorrection.derive(sent.minus(Duration.ofHours(24)), sent, RECEIVED))
        .isEqualTo(RECEIVED.minus(Duration.ofHours(24)));
    assertThat(EventTimeCorrection.derive(sent.plus(Duration.ofMinutes(5)), sent, RECEIVED))
        .isEqualTo(RECEIVED.plus(Duration.ofMinutes(5)));
  }
}
