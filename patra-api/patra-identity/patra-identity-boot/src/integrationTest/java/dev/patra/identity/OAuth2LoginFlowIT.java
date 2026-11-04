package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.config.FakeProviderFixture;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;

/// `oauth2Login` 接入（PAP-180）：发起、回调、两种平台、两条过滤器链共存。
class OAuth2LoginFlowIT extends ProviderFlowITSupport {

  @Test
  void should_not_call_provider_on_startup() {
    assertThat(FakeProviderFixture.requestsDuringStartup()).isZero();
  }

  @Test
  void should_redirect_to_google_with_configured_scope_nonce_and_pkce() {
    AuthorizationStart start = startLogin("google");

    assertThat(start.location()).startsWith("https://accounts.google.com/o/oauth2/v2/auth?");
    assertThat(start.query().get("client_id")).isEqualTo(FakeProviderFixture.GOOGLE_CLIENT_ID);
    assertThat(start.query().get("scope")).isEqualTo("openid email");
    assertThat(start.query().get("redirect_uri"))
        .isEqualTo("http://localhost:4000/api/auth/oauth/callback/google");
    assertThat(start.state()).isNotBlank();
    assertThat(start.nonce()).isNotBlank();
    assertThat(start.query().get("code_challenge_method")).isEqualTo("S256");
  }

  @Test
  void should_redirect_to_github_with_pkce_but_without_nonce() {
    AuthorizationStart start = startLogin("github");

    assertThat(start.location()).startsWith("https://github.com/login/oauth/authorize?");
    assertThat(start.query().get("scope")).isEqualTo("read:user");
    assertThat(start.nonce()).isNull();
    assertThat(start.query()).containsKey("code_challenge");
  }

  @Test
  void should_redirect_to_orcid_without_pkce_by_default() {
    AuthorizationStart start = startLogin("orcid");

    assertThat(start.query().get("scope")).isEqualTo("openid");
    assertThat(start.nonce()).isNotBlank();
    assertThat(start.query()).doesNotContainKey("code_challenge");
  }

  @Test
  void should_answer_404_for_unknown_provider() {
    assertThat(get("/oauth2/authorization/wechat", noHeaders()).status()).isEqualTo(404);
  }

  @Test
  void should_not_create_http_session() {
    RawResponse response = get("/oauth2/authorization/google", noHeaders());

    assertThat(response.headers().get(HttpHeaders.SET_COOKIE)).isNull();
  }

  @Test
  void should_return_registration_required_for_new_oidc_account() {
    AuthorizationStart start = startLogin("google");
    FakeProviderFixture.stubGoogle("google-sub-1", "new.user@example.com", start.nonce());

    RawResponse response = callback("google", start.state(), noHeaders());

    assertThat(response.status()).as(response.body()).isEqualTo(200);
    assertThat(response.headers().get(HttpHeaders.SET_COOKIE)).isNull();
    JsonNode json = JSON.readTree(response.body());
    assertThat(json.get("result").asString()).isEqualTo("REGISTRATION_REQUIRED");
    assertThat(json.get("ticket").asString()).hasSize(43);
    assertThat(json.get("provider").asString()).isEqualTo("google");
    assertThat(json.get("accountLabel").asString()).isEqualTo("new.user@example.com");
    assertThat(json.get("suggestedUsername").isNull()).isTrue();
    assertThat(json.get("suggestedEmail").asString()).isEqualTo("new.user@example.com");
    assertThat(json.has("expiresAt")).isTrue();
  }

  @Test
  void should_return_registration_required_for_new_github_account() {
    AuthorizationStart start = startLogin("github");
    FakeProviderFixture.stubGithub(583231L, "OctoCat", null);

    RawResponse response = callback("github", start.state(), noHeaders());

    assertThat(response.status()).as(response.body()).isEqualTo(200);
    JsonNode json = JSON.readTree(response.body());
    assertThat(json.get("result").asString()).isEqualTo("REGISTRATION_REQUIRED");
    assertThat(json.get("provider").asString()).isEqualTo("github");
    assertThat(json.get("accountLabel").asString()).isEqualTo("OctoCat");
    assertThat(json.get("suggestedUsername").asString()).isEqualTo("octocat");
    assertThat(json.get("suggestedEmail").isNull()).isTrue();
  }

  @Test
  void should_return_registration_required_for_new_orcid_account() {
    AuthorizationStart start = startLogin("orcid");
    FakeProviderFixture.stubOrcid("https://orcid.org/0000-0002-1825-0097", start.nonce());

    RawResponse response = callback("orcid", start.state(), noHeaders());

    assertThat(response.status()).as(response.body()).isEqualTo(200);
    assertThat(JSON.readTree(response.body()).get("accountLabel").asString())
        .isEqualTo("0000-0002-1825-0097");
  }

  @Test
  void should_keep_assertion_chain_for_me_paths() {
    RawResponse registered = register("chain@example.com", "chain-user");
    long userId = JSON.readTree(registered.body()).get("user").get("userId").asLong();

    assertThat(get("/auth/me", noHeaders()).status()).isEqualTo(401);
    assertThat(get("/auth/me", assertionFor(userId, 1L)).status()).isEqualTo(200);
  }
}
