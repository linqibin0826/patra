package dev.patra.analytics.simulator.model;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.simulator.event.SyntheticIds;
import java.util.SplittableRandom;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class PopulationTest {

  private static final TrafficModel MODEL = TrafficModel.load(null, JsonMapper.builder().build());
  private static final Population POPULATION = Population.build(MODEL, new SyntheticIds(1), 42);

  @Test
  void builds_devices_and_registered_users() {
    assertThat(POPULATION.size()).isEqualTo(300_000);
    assertThat(
            IntStream.range(0, POPULATION.size())
                .filter(i -> POPULATION.person(i).userId() != null)
                .count())
        .isEqualTo(30_000);
    assertThat(POPULATION.person(5).deviceId()).startsWith("sim001");
    assertThat(POPULATION.person(5).ua().crawler()).isFalse();
  }

  @Test
  void about_a_quarter_of_sampled_sessions_are_logged_in() {
    SplittableRandom random = new SplittableRandom(9);
    int loggedIn = 0;
    for (int i = 0; i < 100_000; i++) {
      if (POPULATION.sample(random).userId() != null) {
        loggedIn++;
      }
    }

    assertThat(loggedIn / 100_000.0).isBetween(0.20, 0.30);
  }

  @Test
  void no_single_person_dominates() {
    SplittableRandom random = new SplittableRandom(13);
    int[] hits = new int[POPULATION.size()];
    for (int i = 0; i < 1_000_000; i++) {
      hits[POPULATION.sample(random).index()]++;
    }

    assertThat(IntStream.of(hits).max().orElseThrow()).isLessThan(1_000);
  }

  @Test
  void same_seed_same_population() {
    Population again = Population.build(MODEL, new SyntheticIds(1), 42);

    assertThat(again.person(123)).isEqualTo(POPULATION.person(123));
  }

  @Test
  void fresh_device_is_anonymous_with_requested_ua_kind() {
    SplittableRandom random = new SplittableRandom(2);

    Person crawler = POPULATION.freshDevice(300_010, true, random);
    Person stealth = POPULATION.freshDevice(300_011, false, random);

    assertThat(crawler.index()).isEqualTo(300_010);
    assertThat(crawler.userId()).isNull();
    assertThat(crawler.ua().crawler()).isTrue();
    assertThat(stealth.ua().crawler()).isFalse();
    assertThat(crawler.personKey()).isEqualTo("d:" + crawler.deviceId());
  }

  @Test
  void person_key_prefers_user_id() {
    Person registered =
        IntStream.range(0, POPULATION.size())
            .mapToObj(POPULATION::person)
            .filter(p -> p.userId() != null)
            .findFirst()
            .orElseThrow();

    assertThat(registered.personKey()).isEqualTo("u:" + registered.userId());
  }
}
