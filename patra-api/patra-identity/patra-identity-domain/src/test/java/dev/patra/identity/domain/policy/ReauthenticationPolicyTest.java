package dev.patra.identity.domain.policy;

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
    ReauthenticationPolicy policy = ReauthenticationPolicy.of(Duration.ofMinutes(10));

    assertThat(policy.isRecent(NOW, NOW.plus(Duration.ofMinutes(10)).minusMillis(1))).isTrue();
    assertThat(policy.isRecent(NOW, NOW.plus(Duration.ofMinutes(10)))).isFalse();
    assertThat(policy.isRecent(Instant.EPOCH, NOW)).isFalse();
  }

  @Test
  @DisplayName("窗口必须为正")
  void should_reject_non_positive_window() {
    assertThatThrownBy(() -> ReauthenticationPolicy.of(Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ReauthenticationPolicy.of(null))
        .isInstanceOf(NullPointerException.class);
  }
}
