package dev.patra.identity.domain.account.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.account.model.enums.HumanVerificationProvider;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// 人机验证参数：两个门槛满足任一个就要验证。
@DisplayName("HumanVerificationPolicy")
class HumanVerificationPolicyTest {

  private static final Duration WINDOW = Duration.ofMinutes(15);

  private static final HumanVerificationPolicy POLICY =
      HumanVerificationPolicy.of(HumanVerificationProvider.TURNSTILE, 3, 5, WINDOW);

  @Test
  @DisplayName("邮箱失败次数到门槛就要验证")
  void requires_verification_when_email_failures_reach_threshold() {
    assertThat(POLICY.requiresVerification(3, 0)).isTrue();
    assertThat(POLICY.requiresVerification(2, 0)).isFalse();
  }

  @Test
  @DisplayName("IP 失败次数到门槛就要验证")
  void requires_verification_when_ip_failures_reach_threshold() {
    assertThat(POLICY.requiresVerification(0, 5)).isTrue();
    assertThat(POLICY.requiresVerification(0, 4)).isFalse();
  }

  @Test
  @DisplayName("门槛小于 1 时拒绝")
  void rejects_threshold_below_one() {
    assertThatThrownBy(
            () -> HumanVerificationPolicy.of(HumanVerificationProvider.TURNSTILE, 0, 5, WINDOW))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> HumanVerificationPolicy.of(HumanVerificationProvider.TURNSTILE, 3, 0, WINDOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("窗口不是正数时拒绝")
  void rejects_non_positive_window() {
    assertThatThrownBy(
            () ->
                HumanVerificationPolicy.of(
                    HumanVerificationProvider.TURNSTILE, 3, 5, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
