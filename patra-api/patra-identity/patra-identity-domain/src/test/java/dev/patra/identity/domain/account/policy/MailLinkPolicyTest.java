package dev.patra.identity.domain.account.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.patra.identity.domain.account.model.vo.EmailTokenSecret;
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
        MailLinkPolicy.builder()
            .baseUrl("https://patra.example/")
            .verifyEmailPath("/verify-email")
            .securitySettingsPath("/settings/security")
            .resetPasswordPath("/reset-password")
            .confirmEmailChangePath("/confirm-email-change")
            .revertEmailChangePath("/revert-email-change")
            .build();

    assertThat(policy.verifyEmailUrl(SECRET))
        .isEqualTo("https://patra.example/verify-email#token=" + SECRET.value());
    assertThat(policy.securitySettingsUrl()).isEqualTo("https://patra.example/settings/security");
    assertThat(policy.baseUrl()).isEqualTo("https://patra.example");
  }

  @Test
  @DisplayName("带端口的 http 地址也可以")
  void should_accept_http_base_url_with_port() {
    MailLinkPolicy policy =
        MailLinkPolicy.builder()
            .baseUrl("http://localhost:4000")
            .verifyEmailPath("/verify-email")
            .securitySettingsPath("/settings/security")
            .resetPasswordPath("/reset-password")
            .confirmEmailChangePath("/confirm-email-change")
            .revertEmailChangePath("/revert-email-change")
            .build();

    assertThat(policy.verifyEmailUrl(SECRET))
        .startsWith("http://localhost:4000/verify-email#token=");
  }

  @ParameterizedTest
  @ValueSource(strings = {"", "patra.example", "/portal", "ftp://patra.example", "http://"})
  @DisplayName("门户地址必须是 http / https 的绝对地址")
  void should_reject_relative_or_non_http_base_url(String baseUrl) {
    assertThatThrownBy(
            () ->
                MailLinkPolicy.builder()
                    .baseUrl(baseUrl)
                    .verifyEmailPath("/verify-email")
                    .securitySettingsPath("/settings/security")
                    .resetPasswordPath("/reset-password")
                    .confirmEmailChangePath("/confirm-email-change")
                    .revertEmailChangePath("/revert-email-change")
                    .build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("路径必须以 / 开头")
  void should_reject_paths_not_starting_with_slash() {
    assertThatThrownBy(
            () ->
                MailLinkPolicy.builder()
                    .baseUrl("http://localhost:4000")
                    .verifyEmailPath("verify-email")
                    .securitySettingsPath("/settings/security")
                    .resetPasswordPath("/reset-password")
                    .confirmEmailChangePath("/confirm-email-change")
                    .revertEmailChangePath("/revert-email-change")
                    .build())
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                MailLinkPolicy.builder()
                    .baseUrl("http://localhost:4000")
                    .verifyEmailPath("/verify-email")
                    .securitySettingsPath("settings/security")
                    .resetPasswordPath("/reset-password")
                    .confirmEmailChangePath("/confirm-email-change")
                    .revertEmailChangePath("/revert-email-change")
                    .build())
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("重设密码、确认改邮箱、撤销改邮箱三个链接同样把令牌放在片段里")
  void should_build_three_new_links_with_token_in_fragment() {
    MailLinkPolicy links =
        MailLinkPolicy.builder()
            .baseUrl("https://patra.example/")
            .verifyEmailPath("/verify-email")
            .securitySettingsPath("/settings/security")
            .resetPasswordPath("/reset-password")
            .confirmEmailChangePath("/confirm-email-change")
            .revertEmailChangePath("/revert-email-change")
            .build();

    assertThat(links.resetPasswordUrl(SECRET))
        .isEqualTo("https://patra.example/reset-password#token=" + SECRET.value());
    assertThat(links.confirmEmailChangeUrl(SECRET))
        .isEqualTo("https://patra.example/confirm-email-change#token=" + SECRET.value());
    assertThat(links.revertEmailChangeUrl(SECRET))
        .isEqualTo("https://patra.example/revert-email-change#token=" + SECRET.value());
  }

  @Test
  @DisplayName("三个新路径也必须以 / 开头")
  void should_reject_new_paths_not_starting_with_slash() {
    assertThatThrownBy(
            () ->
                MailLinkPolicy.builder()
                    .baseUrl("http://localhost:4000")
                    .verifyEmailPath("/verify-email")
                    .securitySettingsPath("/settings/security")
                    .resetPasswordPath("reset-password")
                    .confirmEmailChangePath("/confirm-email-change")
                    .revertEmailChangePath("/revert-email-change")
                    .build())
        .isInstanceOf(IllegalArgumentException.class);
  }
}
