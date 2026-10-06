package dev.linqibin.patra.identity.infra.adapter.throttle;

import static org.assertj.core.api.Assertions.assertThat;

import dev.linqibin.patra.common.security.AccountType;
import dev.linqibin.patra.identity.domain.model.vo.EmailAddress;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// LoginThrottleAdapter 单元测试：只测键名。
@DisplayName("LoginThrottleAdapter 键名")
class LoginThrottleAdapterTest {

  @Test
  @DisplayName("三个键带账号类型和邮箱的 SHA-256，不含明文邮箱")
  void should_build_keys_with_account_type_and_email_digest() {
    List<String> keys =
        LoginThrottleAdapter.keys(AccountType.USER, EmailAddress.of("chen.yu@example.com"));

    assertThat(keys).hasSize(3);
    assertThat(keys.get(0)).matches("idn:login-failures:user:[0-9a-f]{64}");
    assertThat(keys.get(1)).matches("idn:login-inflight:user:[0-9a-f]{64}");
    assertThat(keys.get(2)).matches("idn:login-lock:user:[0-9a-f]{64}");
    assertThat(String.join(",", keys)).doesNotContain("chen.yu");
  }

  @Test
  @DisplayName("同一个邮箱的三个键后缀相同，不同邮箱不同")
  void should_share_suffix_for_same_email_only() {
    List<String> a = LoginThrottleAdapter.keys(AccountType.USER, EmailAddress.of("a@example.com"));
    List<String> b = LoginThrottleAdapter.keys(AccountType.USER, EmailAddress.of("b@example.com"));

    assertThat(suffix(a.get(0))).isEqualTo(suffix(a.get(2)));
    assertThat(suffix(a.get(0))).isNotEqualTo(suffix(b.get(0)));
  }

  /// 取键名最后一段。
  ///
  /// @param key 键名
  /// @return 最后一个冒号之后的部分
  private static String suffix(String key) {
    return key.substring(key.lastIndexOf(':') + 1);
  }
}
