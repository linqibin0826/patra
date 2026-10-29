package dev.patra.identity.domain.session.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// NewDeviceLoginPolicy 单元测试：回看窗口与提醒冷却（PAP-99 设计第 12.2 节）。
@DisplayName("NewDeviceLoginPolicy 单元测试")
class NewDeviceLoginPolicyTest {

  @Test
  @DisplayName("窗口起点是 now 减回看时长")
  void should_compute_window_start() {
    NewDeviceLoginPolicy policy =
        NewDeviceLoginPolicy.of(Duration.ofDays(180), Duration.ofHours(24));

    assertThat(policy.since(Instant.parse("2026-10-22T08:00:00Z")))
        .isEqualTo(Instant.parse("2026-04-25T08:00:00Z"));
    assertThat(policy.cooldown()).isEqualTo(Duration.ofHours(24));
  }

  @Test
  @DisplayName("两个时长都必须是正数")
  void should_require_positive_durations() {
    assertThatThrownBy(() -> NewDeviceLoginPolicy.of(Duration.ZERO, Duration.ofHours(24)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> NewDeviceLoginPolicy.of(Duration.ofDays(180), Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
