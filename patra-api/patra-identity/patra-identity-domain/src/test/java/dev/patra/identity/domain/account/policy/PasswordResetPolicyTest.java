package dev.patra.identity.domain.account.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// PasswordResetPolicy 单元测试：下次可申请时间、窗口常量、参数校验。
@DisplayName("PasswordResetPolicy 单元测试")
class PasswordResetPolicyTest {

  @Test
  @DisplayName("下次可申请时间 = 现在 + 冷却；三个窗口常量")
  void should_compute_next_request_at_and_expose_windows() {
    PasswordResetPolicy policy = PasswordResetPolicy.of(Duration.ofSeconds(60), 5, 20, 10);

    assertThat(policy.nextRequestAt(Instant.parse("2026-10-22T08:00:00Z")))
        .isEqualTo(Instant.parse("2026-10-22T08:01:00Z"));
    assertThat(PasswordResetPolicy.HOUR_WINDOW).isEqualTo(Duration.ofHours(1));
    assertThat(PasswordResetPolicy.DAY_WINDOW).isEqualTo(Duration.ofHours(24));
    assertThat(PasswordResetPolicy.MINUTE_WINDOW).isEqualTo(Duration.ofMinutes(1));
  }

  @Test
  @DisplayName("冷却必须为正，三个上限至少为 1")
  void should_reject_non_positive_values() {
    assertThatThrownBy(() -> PasswordResetPolicy.of(Duration.ZERO, 5, 20, 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PasswordResetPolicy.of(Duration.ofSeconds(60), 0, 20, 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PasswordResetPolicy.of(Duration.ofSeconds(60), 5, 0, 10))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> PasswordResetPolicy.of(Duration.ofSeconds(60), 5, 20, 0))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
