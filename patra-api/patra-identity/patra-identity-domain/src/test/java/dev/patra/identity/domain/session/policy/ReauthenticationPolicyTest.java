package dev.patra.identity.domain.session.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// ReauthenticationPolicy 单元测试：窗口边界、参数校验。
@DisplayName("ReauthenticationPolicy 单元测试")
class ReauthenticationPolicyTest {

  private static final Instant NOW = Instant.parse("2026-10-22T08:00:00Z");

  @Test
  @DisplayName("窗口内算最近；满窗口当刻不算；EPOCH 视作从未再认证")
  void should_treat_window_end_as_not_recent() {
    ReauthenticationPolicy policy =
        ReauthenticationPolicy.of(Duration.ofMinutes(10), Duration.ofSeconds(10));

    assertThat(policy.isRecent(NOW, NOW.plus(Duration.ofMinutes(10)).minusMillis(1))).isTrue();
    assertThat(policy.isRecent(NOW, NOW.plus(Duration.ofMinutes(10)))).isFalse();
    assertThat(policy.isRecent(Instant.EPOCH, NOW)).isFalse();
  }

  @Test
  @DisplayName("窗口必须为正")
  void should_reject_non_positive_window() {
    assertThatThrownBy(() -> ReauthenticationPolicy.of(Duration.ZERO, Duration.ofSeconds(10)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ReauthenticationPolicy.of(null, Duration.ofSeconds(10)))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  @DisplayName("换令牌的宽限期：可以为零（每次都换），不能为负或 null")
  void should_accept_zero_rotation_grace_but_reject_negative() {
    assertThat(ReauthenticationPolicy.of(Duration.ofMinutes(10), Duration.ZERO).rotationGrace())
        .isEqualTo(Duration.ZERO);
    assertThat(
            ReauthenticationPolicy.of(Duration.ofMinutes(10), Duration.ofSeconds(10))
                .rotationGrace())
        .isEqualTo(Duration.ofSeconds(10));
    assertThatThrownBy(
            () -> ReauthenticationPolicy.of(Duration.ofMinutes(10), Duration.ofSeconds(-1)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ReauthenticationPolicy.of(Duration.ofMinutes(10), null))
        .isInstanceOf(NullPointerException.class);
  }
}
