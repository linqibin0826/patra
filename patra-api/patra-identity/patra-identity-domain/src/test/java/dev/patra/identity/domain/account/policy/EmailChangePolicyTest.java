package dev.patra.identity.domain.account.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// EmailChangePolicy 单元测试。
@DisplayName("EmailChangePolicy 单元测试")
class EmailChangePolicyTest {

  @Test
  @DisplayName("每账号每天的上限与天窗口")
  void should_expose_daily_limit_and_window() {
    assertThat(EmailChangePolicy.of(3).perAccountDaily()).isEqualTo(3);
    assertThat(EmailChangePolicy.DAY_WINDOW).isEqualTo(Duration.ofHours(24));
  }

  @Test
  @DisplayName("上限至少为 1")
  void should_reject_non_positive_limit() {
    assertThatThrownBy(() -> EmailChangePolicy.of(0)).isInstanceOf(IllegalArgumentException.class);
  }
}
