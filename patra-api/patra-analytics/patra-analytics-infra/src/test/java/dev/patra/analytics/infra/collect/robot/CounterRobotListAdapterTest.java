package dev.patra.analytics.infra.collect.robot;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class CounterRobotListAdapterTest {

  private final CounterRobotListAdapter adapter =
      new CounterRobotListAdapter(JsonMapper.builder().build());

  @Test
  void googlebot_is_listed() {
    assertThat(
            adapter.listed(
                "Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)"))
        .isTrue();
  }

  @Test
  void plain_chrome_is_not_listed() {
    assertThat(
            adapter.listed(
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like"
                    + " Gecko) Chrome/141.0.0.0 Safari/537.36"))
        .isFalse();
  }

  @Test
  void matching_is_case_insensitive() {
    assertThat(adapter.listed("GOOGLEBOT/2.1")).isTrue();
  }

  @Test
  void loads_all_patterns() {
    assertThat(adapter.size()).isEqualTo(327);
  }
}
