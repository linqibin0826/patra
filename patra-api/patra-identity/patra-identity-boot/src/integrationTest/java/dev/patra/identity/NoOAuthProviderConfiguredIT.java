package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;

/// 本机还没放 `oauth.env`：identity 照常启动，第三方入口 404，账号密码不受影响（MacBook 的现状）。
@TestPropertySource(
    properties = {
      "PATRA_OAUTH_GOOGLE_CLIENT_ID=",
      "PATRA_OAUTH_GOOGLE_CLIENT_SECRET=",
      "PATRA_OAUTH_GITHUB_CLIENT_ID=",
      "PATRA_OAUTH_GITHUB_CLIENT_SECRET=",
      "PATRA_OAUTH_ORCID_CLIENT_ID=",
      "PATRA_OAUTH_ORCID_CLIENT_SECRET="
    })
class NoOAuthProviderConfiguredIT extends IdentityFlowITSupport {

  @Test
  void should_answer_404_on_entry_but_keep_password_login() {
    assertThat(get("/oauth2/authorization/google", noHeaders()).status()).isEqualTo(404);

    RawResponse registered = register("flow.no-oauth@example.com", "no-oauth");
    long userId = userIdOf(registered);
    jdbcTemplate.update("UPDATE idn_user SET email_verified_at = now() WHERE id = ?", userId);
    HttpHeaders auth = assertionFor(userId, latestSessionIdOf(userId));

    assertThat(get("/auth/me", auth).status()).isEqualTo(200);
    assertThat(exchange(HttpMethod.POST, "/me/login-methods/github/link", null, auth).status())
        .isEqualTo(404);
    assertThat(get("/me/login-methods", auth).status()).isEqualTo(200);
  }
}
