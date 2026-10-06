package dev.linqibin.patra.identity.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// LoginThrottlePolicy 单元测试。
@DisplayName("LoginThrottlePolicy 单元测试")
class LoginThrottlePolicyTest {

  private static final Duration FIFTEEN_MINUTES = Duration.ofMinutes(15);

  @Test
  @DisplayName("保存四个参数")
  void should_keep_parameters() {
    LoginThrottlePolicy policy =
        LoginThrottlePolicy.of(5, FIFTEEN_MINUTES, FIFTEEN_MINUTES, Duration.ofSeconds(30));

    assertThat(policy.maxFailures()).isEqualTo(5);
    assertThat(policy.inFlightTtl()).isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  @DisplayName("上限至少为 1，时长必须为正")
  void should_reject_invalid_parameters() {
    assertThatThrownBy(
            () -> LoginThrottlePolicy.of(0, FIFTEEN_MINUTES, FIFTEEN_MINUTES, FIFTEEN_MINUTES))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> LoginThrottlePolicy.of(5, Duration.ZERO, FIFTEEN_MINUTES, FIFTEEN_MINUTES))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> LoginThrottlePolicy.of(5, FIFTEEN_MINUTES, null, FIFTEEN_MINUTES))
        .isInstanceOf(NullPointerException.class);
  }
}
