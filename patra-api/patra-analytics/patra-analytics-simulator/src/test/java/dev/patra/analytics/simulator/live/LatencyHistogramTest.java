package dev.patra.analytics.simulator.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.withinPercentage;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class LatencyHistogramTest {

  @Test
  void histogram_percentiles_within_five_percent() {
    LatencyHistogram histogram = new LatencyHistogram();
    for (int ms = 1; ms <= 1_000; ms++) {
      histogram.record(ms * 1_000_000L);
    }

    assertThat(histogram.percentile(0.50).toNanos() / 1e6).isCloseTo(500, withinPercentage(5));
    assertThat(histogram.percentile(0.99).toNanos() / 1e6).isCloseTo(990, withinPercentage(5));
    assertThat(histogram.count()).isEqualTo(1_000);
  }

  @Test
  void empty_histogram_reports_zero() {
    assertThat(new LatencyHistogram().percentile(0.5).isZero()).isTrue();
  }

  @Test
  void handles_extremes() {
    LatencyHistogram histogram = new LatencyHistogram();
    SplittableRandom random = new SplittableRandom(1);
    histogram.record(1);
    histogram.record(600_000_000_000L);
    for (int i = 0; i < 100; i++) {
      histogram.record(random.nextLong(1, 10_000_000));
    }

    assertThat(histogram.percentile(1.0).toSeconds()).isGreaterThanOrEqualTo(60);
  }
}
