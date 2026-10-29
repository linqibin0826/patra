package dev.patra.identity.domain.account.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// UsernameChangePolicy 单元测试。
@DisplayName("UsernameChangePolicy 单元测试")
class UsernameChangePolicyTest {

  private static final Instant CHANGED_AT = Instant.parse("2026-10-20T08:00:00Z");

  @Test
  @DisplayName("从没改过名没有冷却；冷却期内给剩余时长；到期当刻放行")
  void should_compute_remaining_cooldown() {
    UsernameChangePolicy policy = UsernameChangePolicy.of(Duration.ofDays(30), Duration.ofDays(90));

    assertThat(policy.remainingCooldown(null, CHANGED_AT)).isEmpty();
    assertThat(policy.remainingCooldown(CHANGED_AT, CHANGED_AT.plus(Duration.ofDays(10))))
        .contains(Duration.ofDays(20));
    assertThat(policy.remainingCooldown(CHANGED_AT, CHANGED_AT.plus(Duration.ofDays(30))))
        .isEmpty();
    assertThat(policy.nextChangeAt(CHANGED_AT)).isEqualTo(CHANGED_AT.plus(Duration.ofDays(30)));
    assertThat(policy.nextChangeAt(null)).isNull();
  }

  @Test
  @DisplayName("冷却和保留期都必须是正数")
  void should_reject_non_positive_periods() {
    assertThatThrownBy(() -> UsernameChangePolicy.of(Duration.ZERO, Duration.ofDays(90)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> UsernameChangePolicy.of(Duration.ofDays(30), Duration.ofDays(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
