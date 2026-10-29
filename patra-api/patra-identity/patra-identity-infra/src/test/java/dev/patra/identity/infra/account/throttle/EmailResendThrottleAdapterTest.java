package dev.patra.identity.infra.account.throttle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// EmailResendThrottleAdapter 单元测试：键名。
@DisplayName("EmailResendThrottleAdapter 单元测试")
class EmailResendThrottleAdapterTest {

  @Test
  @DisplayName("四个键：冷却、账号小时、账号天按用户 ID；IP 键只放哈希，没有 IP 时用占位键")
  void should_hash_ip_in_key_and_use_placeholder_when_absent() {
    List<String> keys = EmailResendThrottleAdapter.keys(42L, "203.0.113.9");

    assertThat(keys)
        .hasSize(4)
        .startsWith(
            "idn:email-resend:cooldown:user:42",
            "idn:email-resend:hour:user:42",
            "idn:email-resend:day:user:42");
    assertThat(keys.get(3))
        .matches("^idn:email-resend:hour:ip:[0-9a-f]{32}$")
        .doesNotContain("203.0.113.9");
    assertThat(EmailResendThrottleAdapter.keys(42L, "203.0.113.10").get(3))
        .isNotEqualTo(keys.get(3));
    assertThat(EmailResendThrottleAdapter.keys(42L, null).get(3))
        .isEqualTo("idn:email-resend:hour:ip:-");
  }
}
