package dev.patra.identity.domain.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.model.vo.EmailTokenSecret;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// MailLinkPolicy 单元测试：链接拼法、地址与路径的校验。
@DisplayName("MailLinkPolicy 单元测试")
class MailLinkPolicyTest {

  private static final EmailTokenSecret SECRET = new EmailTokenSecret("C".repeat(43));

  @Test
  @DisplayName("验证链接 = 门户地址 + 路径 + #token=令牌；门户地址末尾的斜杠被去掉")
  void should_build_verify_url_with_fragment_and_strip_trailing_slash() {
    MailLinkPolicy policy =
        MailLinkPolicy.of("https://patra.example/", "/verify-email", "/settings/security");

    assertThat(policy.verifyEmailUrl(SECRET))
        .isEqualTo("https://patra.example/verify-email#token=" + SECRET.value());
    assertThat(policy.securitySettingsUrl()).isEqualTo("https://patra.example/settings/security");
    assertThat(policy.baseUrl()).isEqualTo("https://patra.example");
  }

  @Test
  @DisplayName("带端口的 http 地址也可以")
  void should_accept_http_base_url_with_port() {
    MailLinkPolicy policy =
        MailLinkPolicy.of("http://localhost:4000", "/verify-email", "/settings/security");

    assertThat(policy.verifyEmailUrl(SECRET))
        .startsWith("http://localhost:4000/verify-email#token=");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "patra.example", "/portal", "ftp://patra.example", "http://"})
  @DisplayName("门户地址必须是 http / https 的绝对地址")
  void should_reject_relative_or_non_http_base_url(String baseUrl) {
    assertThatThrownBy(() -> MailLinkPolicy.of(baseUrl, "/verify-email", "/settings/security"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("路径必须以 / 开头")
  void should_reject_paths_not_starting_with_slash() {
    assertThatThrownBy(
            () -> MailLinkPolicy.of("http://localhost:4000", "verify-email", "/settings/security"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> MailLinkPolicy.of("http://localhost:4000", "/verify-email", "settings/security"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
