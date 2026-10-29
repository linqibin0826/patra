package dev.patra.identity.domain.account.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// AccountDeletionPolicy 单元测试：冷静期与提醒提前量的校验。
@DisplayName("AccountDeletionPolicy 单元测试")
class AccountDeletionPolicyTest {

  @Test
  @DisplayName("冷静期、提前量必须是正数")
  void should_reject_non_positive_durations() {
    assertThatThrownBy(() -> AccountDeletionPolicy.of(Duration.ZERO, Duration.ofDays(3)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> AccountDeletionPolicy.of(Duration.ofDays(15), Duration.ofDays(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("提前量必须短于冷静期")
  void should_reject_lead_not_shorter_than_cooling_period() {
    assertThatThrownBy(() -> AccountDeletionPolicy.of(Duration.ofDays(15), Duration.ofDays(15)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("默认值 15 天、3 天原样保留")
  void should_accept_defaults() {
    AccountDeletionPolicy policy =
        AccountDeletionPolicy.of(Duration.ofDays(15), Duration.ofDays(3));

    assertThat(policy.coolingPeriod()).isEqualTo(Duration.ofDays(15));
    assertThat(policy.reminderLead()).isEqualTo(Duration.ofDays(3));
  }
}
