package dev.patra.analytics.domain.collect.policy;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.analytics.domain.collect.model.enums.BotReason;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BotClassificationTest {

  @Test
  void robots_list_wins_over_device_class() {
    assertThat(BotClassification.fromUserAgent(true, "Desktop")).contains(BotReason.UA_LIST);
    assertThat(BotClassification.fromUserAgent(true, "Robot")).contains(BotReason.UA_LIST);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Robot", "Robot Mobile", "Robot Imitator", "Spy", "Hacker"})
  void robot_device_classes_are_bots(String deviceClass) {
    assertThat(BotClassification.fromUserAgent(false, deviceClass)).contains(BotReason.UA_CLASS);
  }

  @ParameterizedTest
  @ValueSource(strings = {"Desktop", "Phone", "Tablet", "Unknown"})
  void desktop_is_not_bot(String deviceClass) {
    assertThat(BotClassification.fromUserAgent(false, deviceClass)).isEmpty();
  }

  @Test
  void rate_limit_constants_match_tracking_plan() {
    assertThat(BotClassification.RATE_LIMIT).isEqualTo(30);
    assertThat(BotClassification.RATE_WINDOW).hasMinutes(5);
    assertThat(BotReason.RATE.wire()).isEqualTo("rate");
  }
}
