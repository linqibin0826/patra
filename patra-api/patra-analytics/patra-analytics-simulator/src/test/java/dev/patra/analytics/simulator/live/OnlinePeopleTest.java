package dev.patra.analytics.simulator.live;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.simulator.event.SyntheticIds;
import dev.patra.analytics.simulator.model.Person;
import dev.patra.analytics.simulator.model.Population;
import dev.patra.analytics.simulator.model.TrafficModel;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class OnlinePeopleTest {

  private static final TrafficModel MODEL =
      TrafficModel.load(null, JsonMapper.builder().build()).toBuilder()
          .devices(2)
          .registeredUsers(1)
          .build();

  @Test
  void a_person_is_never_in_two_sessions_at_once() {
    OnlinePeople online = new OnlinePeople(Population.build(MODEL, new SyntheticIds(1), 1));
    SplittableRandom random = new SplittableRandom(1);

    Person first = online.acquire(random);
    Person second = online.acquire(random);
    Person third = online.acquire(random);

    assertThat(first.index()).isNotEqualTo(second.index());
    assertThat(third.index()).isGreaterThanOrEqualTo(2);
    assertThat(third.userId()).isNull();
    assertThat(online.size()).isEqualTo(3);
  }

  @Test
  void released_person_can_come_back() {
    OnlinePeople online = new OnlinePeople(Population.build(MODEL, new SyntheticIds(1), 1));
    SplittableRandom random = new SplittableRandom(1);
    Person first = online.acquire(random);
    Person second = online.acquire(random);

    online.release(first);
    online.release(second);

    assertThat(online.acquire(random).index()).isLessThan(2);
    assertThat(online.size()).isEqualTo(1);
  }
}
