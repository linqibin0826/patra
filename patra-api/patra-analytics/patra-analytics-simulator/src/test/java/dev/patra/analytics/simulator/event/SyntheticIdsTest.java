package dev.patra.analytics.simulator.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class SyntheticIdsTest {

  @Test
  void device_id_has_run_prefix_and_43_base64url_chars() {
    String id = new SyntheticIds(7).deviceId(new SplittableRandom(1));

    assertThat(id).hasSize(43).startsWith("sim007").matches("^[A-Za-z0-9_-]{43}$");
  }

  @Test
  void user_ids_live_in_run_range() {
    SyntheticIds ids = new SyntheticIds(7);

    assertThat(ids.userId(0)).isEqualTo(9_000_000_000_070_000_000L);
    assertThat(ids.userIdLow()).isEqualTo(9_000_000_000_070_000_000L);
    assertThat(ids.userIdHigh()).isEqualTo(9_000_000_000_079_999_999L);
    assertThat(ids.devicePrefix()).isEqualTo("sim007");
  }

  @Test
  void rejects_run_outside_1_to_899() {
    assertThatThrownBy(() -> new SyntheticIds(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new SyntheticIds(900)).isInstanceOf(IllegalArgumentException.class);
  }
}
