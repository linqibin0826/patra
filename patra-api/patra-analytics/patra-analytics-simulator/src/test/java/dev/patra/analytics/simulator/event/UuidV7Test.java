package dev.patra.analytics.simulator.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.SplittableRandom;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

  @Test
  void is_lowercase_version_7_and_time_ordered() {
    SplittableRandom random = new SplittableRandom(1);
    UUID earlier = UuidV7.of(Instant.parse("2026-11-01T00:00:00Z"), random);
    UUID later = UuidV7.of(Instant.parse("2026-11-01T00:00:01Z"), random);

    assertThat(earlier.version()).isEqualTo(7);
    assertThat(earlier.variant()).isEqualTo(2);
    assertThat(earlier.toString()).isEqualTo(earlier.toString().toLowerCase());
    assertThat(earlier.toString().compareTo(later.toString())).isNegative();
  }

  @Test
  void carries_millisecond_timestamp_in_high_bits() {
    Instant at = Instant.parse("2026-11-01T08:30:10.123Z");

    UUID id = UuidV7.of(at, new SplittableRandom(3));

    assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(at.toEpochMilli());
  }
}
