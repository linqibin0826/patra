package dev.patra.identity.domain.account.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// EmailResendPolicy 单元测试：冷却、窗口常量、参数校验。
@DisplayName("EmailResendPolicy 单元测试")
class EmailResendPolicyTest {

  @Test
  @DisplayName("下次可重发时间 = 现在 + 冷却；小时窗口 1 小时、天窗口 24 小时")
  void should_compute_next_resend_at_from_cooldown() {
    EmailResendPolicy policy = EmailResendPolicy.of(Duration.ofSeconds(60), 5, 10, 30);

    assertThat(policy.nextResendAt(Instant.parse("2026-10-21T08:00:00Z")))
        .isEqualTo(Instant.parse("2026-10-21T08:01:00Z"));
    assertThat(EmailResendPolicy.HOUR_WINDOW).isEqualTo(Duration.ofHours(1));
    assertThat(EmailResendPolicy.DAY_WINDOW).isEqualTo(Duration.ofHours(24));
  }

  @Test
  @DisplayName("冷却必须为正，三个上限至少为 1")
  void should_reject_non_positive_values() {
    assertThatThrownBy(() -> EmailResendPolicy.of(Duration.ZERO, 5, 10, 30))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> EmailResendPolicy.of(Duration.ofSeconds(-1), 5, 10, 30))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> EmailResendPolicy.of(Duration.ofSeconds(60), 0, 10, 30))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> EmailResendPolicy.of(Duration.ofSeconds(60), 5, 0, 30))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> EmailResendPolicy.of(Duration.ofSeconds(60), 5, 10, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> EmailResendPolicy.of(null, 5, 10, 30))
        .isInstanceOf(NullPointerException.class);
  }
}
