package dev.patra.analytics.infra.collect.useragent;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.domain.collect.model.vo.UserAgentInfo;
import org.junit.jupiter.api.Test;

class YauaaUserAgentAdapterTest {

  private final YauaaUserAgentAdapter adapter = new YauaaUserAgentAdapter();

  @Test
  void parse_chrome_on_mac() {
    UserAgentInfo info =
        adapter.parse(
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko)"
                + " Chrome/141.0.0.0 Safari/537.36");

    assertThat(info.browserFamily()).isEqualTo("Chrome");
    assertThat(info.osFamily()).isEqualTo("Mac OS");
    assertThat(info.deviceClass()).isEqualTo("Desktop");
  }

  @Test
  void parse_googlebot_is_robot_class() {
    UserAgentInfo info =
        adapter.parse("Mozilla/5.0 (compatible; Googlebot/2.1; +http://www.google.com/bot.html)");

    assertThat(info.deviceClass()).startsWith("Robot");
  }

  @Test
  void parse_empty_user_agent_yields_unknown_class() {
    UserAgentInfo info = adapter.parse("");

    assertThat(info.deviceClass()).isNotBlank();
    assertThat(info.browserFamily()).isNull();
  }

  @Test
  void long_names_are_truncated_to_schema_limit() {
    assertThat(YauaaUserAgentAdapter.known("a".repeat(100))).hasSize(64);
    assertThat(YauaaUserAgentAdapter.known("Unknown")).isNull();
  }
}
