package dev.patra.analytics.simulator.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class TrafficModelTest {

  @Test
  void loads_builtin_model_defaults() {
    TrafficModel model = TrafficModel.load(null, JsonMapper.builder().build());

    assertThat(model.devices()).isEqualTo(300_000);
    assertThat(model.registeredUsers()).isEqualTo(30_000);
    assertThat(model.zipfExponent()).isEqualTo(1.0);
    assertThat(model.maxArticles()).isEqualTo(200_000);
    assertThat(model.topicAffinity()).isEqualTo(0.7);
    assertThat(model.zeroResultRate()).isEqualTo(0.08);
    assertThat(model.weekday()).containsExactly(1.0, 1.0, 1.0, 1.0, 1.0, 0.65, 0.6);
    assertThat(model.crawlers().declared().share()).isEqualTo(0.03);
    assertThat(model.crawlers().stealth().share()).isEqualTo(0.01);
    assertThat(model.crawlers().noscript().share()).isEqualTo(0.02);
  }

  @Test
  void hourly_is_normalized_to_mean_one() {
    TrafficModel model = TrafficModel.load(null, JsonMapper.builder().build());

    assertThat(model.hourly()).hasSize(24);
    assertThat(model.hourly().stream().mapToDouble(Double::doubleValue).average().orElseThrow())
        .isCloseTo(1.0, within(1e-9));
    assertThat(model.hourly().get(3) / model.hourly().get(10)).isCloseTo(0.15 / 1.8, within(1e-9));
  }

  @Test
  void transition_rows_sum_to_one() {
    TrafficModel model = TrafficModel.load(null, JsonMapper.builder().build());

    assertThat(model.transitions())
        .containsKeys("home", "search", "article", "fulltext", "library");
    for (Map<String, Double> row : model.transitions().values()) {
      assertThat(row.values().stream().mapToDouble(Double::doubleValue).sum())
          .isCloseTo(1.0, within(1e-9));
    }
    assertThat(model.entry().values().stream().mapToDouble(Double::doubleValue).sum())
        .isCloseTo(1.0, within(1e-9));
  }
}
