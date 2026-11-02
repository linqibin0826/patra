package dev.patra.analytics.simulator.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.simulator.event.UuidV7;
import java.time.Instant;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DuplicatesTest {

  @Test
  void duplicate_share_is_one_percent() {
    SplittableRandom random = new SplittableRandom(8);
    Instant at = Instant.parse("2026-11-01T00:00:00Z");
    int chosen = 0;
    for (int i = 0; i < 1_000_000; i++) {
      if (Duplicates.chosen(UuidV7.of(at.plusMillis(i), random))) {
        chosen++;
      }
    }

    assertThat(chosen / 1_000_000.0).isBetween(0.009, 0.011);
  }

  @Test
  void duplicate_choice_is_stable_for_same_event_id() {
    UUID id = UuidV7.of(Instant.parse("2026-11-01T00:00:00Z"), new SplittableRandom(1));

    assertThat(Duplicates.chosen(id)).isEqualTo(Duplicates.chosen(UUID.fromString(id.toString())));
  }
}
