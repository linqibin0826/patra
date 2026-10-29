package dev.patra.identity.infra.account.throttle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.domain.account.model.vo.EmailAddress;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// PasswordResetThrottleAdapter 单元测试：键名。
@DisplayName("PasswordResetThrottleAdapter 单元测试")
class PasswordResetThrottleAdapterTest {

  @Test
  @DisplayName("申请的键：邮箱冷却、邮箱天窗口、IP 小时窗口都只放哈希；没有 IP 时 IP 段是 unknown 共用桶；提交只有 IP 分钟窗口")
  void should_hash_email_and_ip_into_keys_and_share_unknown_bucket_when_ip_absent() {
    EmailAddress email = EmailAddress.of("chen.yu@example.com");

    List<String> keys = PasswordResetThrottleAdapter.requestKeys(email, "203.0.113.9");

    assertThat(keys).hasSize(3);
    assertThat(keys.get(0))
        .matches("^idn:pw-reset:cooldown:email:[0-9a-f]{32}$")
        .doesNotContain("chen");
    assertThat(keys.get(1)).matches("^idn:pw-reset:day:email:[0-9a-f]{32}$");
    assertThat(keys.get(2))
        .matches("^idn:pw-reset:hour:ip:[0-9a-f]{32}$")
        .doesNotContain("203.0.113.9");
    assertThat(PasswordResetThrottleAdapter.requestKeys(email, null).get(2))
        .isEqualTo("idn:pw-reset:hour:ip:unknown");
    assertThat(PasswordResetThrottleAdapter.submitKey("203.0.113.9"))
        .matches("^idn:pw-reset-submit:minute:ip:[0-9a-f]{32}$");
    assertThat(PasswordResetThrottleAdapter.submitKey(null))
        .isEqualTo("idn:pw-reset-submit:minute:ip:unknown");
  }
}
