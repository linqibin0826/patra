package dev.patra.identity.infra.account.throttle;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// EmailChangeThrottleAdapter 单元测试：键名。
@DisplayName("EmailChangeThrottleAdapter 单元测试")
class EmailChangeThrottleAdapterTest {

  @Test
  @DisplayName("键按用户 ID")
  void should_key_by_user_id() {
    assertThat(EmailChangeThrottleAdapter.key(42L)).isEqualTo("idn:email-change:day:user:42");
  }
}
