package dev.patra.analytics.simulator.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.withinPercentage;

import dev.patra.analytics.simulator.event.BeijingDays;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ArrivalCurveTest {

  private final TrafficModel model = TrafficModel.load(null, JsonMapper.builder().build());
  private final ArrivalCurve curve = new ArrivalCurve(model);

  @Test
  void night_to_peak_ratio_matches_model() {
    assertThat(curve.hourWeight(3) / curve.hourWeight(10)).isCloseTo(0.15 / 1.8, within(1e-9));
    assertThat(curve.dayWeight(DayOfWeek.SUNDAY)).isEqualTo(0.6);
  }

  @Test
  void sampled_starts_follow_hour_weights() {
    LocalDate day = LocalDate.of(2026, 11, 4);
    SplittableRandom random = new SplittableRandom(11);
    int samples = 200_000;
    int tenOClock = 0;
    for (int i = 0; i < samples; i++) {
      Instant start = curve.sampleStart(day, random);
      assertThat(BeijingDays.of(start)).isEqualTo(day);
      if (start.atZone(BeijingDays.ZONE).getHour() == 10) {
        tenOClock++;
      }
    }

    assertThat(tenOClock / (double) samples)
        .isCloseTo(curve.hourWeight(10) / 24, withinPercentage(5));
  }

  @Test
  void allocation_sums_exactly_and_sunday_is_lighter() {
    LocalDate first = LocalDate.of(2026, 8, 6);
    List<LocalDate> days = Stream.iterate(first, d -> d.plusDays(1)).limit(88).toList();

    Map<LocalDate, Long> allocation = curve.allocate(102_000_000L, days);

    assertThat(allocation.values().stream().mapToLong(Long::longValue).sum())
        .isEqualTo(102_000_000L);
    LocalDate sunday = LocalDate.of(2026, 8, 9);
    LocalDate wednesday = LocalDate.of(2026, 8, 12);
    assertThat(allocation.get(sunday) / (double) allocation.get(wednesday))
        .isCloseTo(0.6, within(0.001));
  }
}
