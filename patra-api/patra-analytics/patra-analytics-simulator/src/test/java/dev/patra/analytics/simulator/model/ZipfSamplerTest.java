package dev.patra.analytics.simulator.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.assertj.core.api.Assertions.withinPercentage;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class ZipfSamplerTest {

  private static final int SAMPLES = 1_000_000;

  @Test
  void top_rank_share_matches_harmonic_number() {
    ZipfSampler zipf = new ZipfSampler(200_000, 1.0);
    SplittableRandom random = new SplittableRandom(42);
    int hits = 0;
    for (int i = 0; i < SAMPLES; i++) {
      if (zipf.sample(random) == 0) {
        hits++;
      }
    }

    assertThat(zipf.probability(0)).isCloseTo(1 / 12.783, within(0.001));
    assertThat(hits / (double) SAMPLES).isCloseTo(zipf.probability(0), withinPercentage(5));
  }

  @Test
  void top_one_percent_takes_about_half() {
    ZipfSampler zipf = new ZipfSampler(200_000, 1.0);
    SplittableRandom random = new SplittableRandom(7);
    int top = 0;
    for (int i = 0; i < SAMPLES; i++) {
      if (zipf.sample(random) < 2_000) {
        top++;
      }
    }

    assertThat(top / (double) SAMPLES).isBetween(0.45, 0.65);
  }

  @Test
  void samples_stay_in_range() {
    ZipfSampler zipf = new ZipfSampler(10, 1.0);
    SplittableRandom random = new SplittableRandom(1);

    for (int i = 0; i < 10_000; i++) {
      assertThat(zipf.sample(random)).isBetween(0, 9);
    }
  }
}
