package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jwt.JWTClaimsSet;
import dev.patra.identity.config.FakeProviderFixture;
import java.time.Instant;
import java.util.Date;
import java.util.Set;
import org.junit.jupiter.api.Test;

/// 协议攻击（release spec D7）：模拟平台给不对的东西，都不建账号、不发会话。
///
/// 门户对 `state` Cookie 的核对在门户测试里；这里守 identity 这一侧：Redis 里的记录证明「这个 state 是我们发的」。
class OAuth2ProtocolAttackIT extends ProviderFlowITSupport {

  private static final String SUBJECT = "attack-sub";

  private long sessionKeys() {
    Set<String> keys = redis.keys("idn:session:*");
    return keys == null ? 0 : keys.size();
  }

  private void assertRejected(RawResponse response, String trait) {
    assertThat(response.status()).as(response.body()).isEqualTo(422);
    assertThat(json(response).get("code").asString()).isEqualTo("IDN-0422");
    assertThat(json(response).get("traits").toString()).contains(trait);
    assertThat(json(response).has("sessionToken")).isFalse();
    assertThat(json(response).has("ticket")).isFalse();
  }

  private RawResponse callbackWithIdToken(AuthorizationStart start, String idToken) {
    FakeProviderFixture.stubGoogleIdToken(idToken);
    return callback("google", start.state(), noHeaders());
  }

  private JWTClaimsSet.Builder goodClaims(AuthorizationStart start) {
    return FakeProviderFixture.claims(
            FakeProviderFixture.GOOGLE_ISSUER,
            FakeProviderFixture.GOOGLE_CLIENT_ID,
            SUBJECT,
            start.nonce())
        .claim("email", "attack@example.com");
  }

  @Test
  void should_accept_well_formed_id_token_as_baseline() {
    AuthorizationStart start = startLogin("google");

    RawResponse response =
        callbackWithIdToken(start, FakeProviderFixture.signedByProvider(goodClaims(start).build()));

    assertThat(response.status()).as(response.body()).isEqualTo(200);
  }

  @Test
  void should_reject_unknown_state() {
    long before = sessionKeys();

    RawResponse response = callback("google", "state-we-never-issued", noHeaders());

    assertRejected(response, "OAUTH_REJECTED");
    assertThat(sessionKeys()).isEqualTo(before);
  }

  @Test
  void should_reject_replayed_callback() {
    AuthorizationStart start = startLogin("github");
    FakeProviderFixture.stubGithub(10001L, "replay", null);
    assertThat(callback("github", start.state(), noHeaders()).status()).isEqualTo(200);

    RawResponse replay = callback("github", start.state(), noHeaders());

    assertRejected(replay, "OAUTH_REJECTED");
  }

  @Test
  void should_reject_callback_without_code_or_error() {
    assertThat(get("/login/oauth2/code/google", noHeaders()).status()).isEqualTo(422);
  }

  @Test
  void should_report_cancelled_when_user_denies() {
    AuthorizationStart start = startLogin("google");

    RawResponse response =
        get("/login/oauth2/code/google?error=access_denied&state=" + start.state(), noHeaders());

    assertRejected(response, "OAUTH_CANCELLED");
  }

  @Test
  void should_reject_id_token_signed_by_foreign_key() {
    AuthorizationStart start = startLogin("google");

    assertRejected(
        callbackWithIdToken(
            start, FakeProviderFixture.signedByForeignKey(goodClaims(start).build())),
        "OAUTH_REJECTED");
  }

  @Test
  void should_reject_id_token_with_wrong_nonce() {
    AuthorizationStart start = startLogin("google");

    assertRejected(
        callbackWithIdToken(
            start,
            FakeProviderFixture.signedByProvider(
                goodClaims(start).claim("nonce", "someone-elses-nonce").build())),
        "OAUTH_REJECTED");
  }

  @Test
  void should_reject_id_token_with_wrong_audience() {
    AuthorizationStart start = startLogin("google");

    assertRejected(
        callbackWithIdToken(
            start,
            FakeProviderFixture.signedByProvider(
                goodClaims(start).audience("another-client").build())),
        "OAUTH_REJECTED");
  }

  @Test
  void should_reject_id_token_with_wrong_issuer() {
    AuthorizationStart start = startLogin("google");

    assertRejected(
        callbackWithIdToken(
            start,
            FakeProviderFixture.signedByProvider(
                goodClaims(start).issuer("https://evil.test").build())),
        "OAUTH_REJECTED");
  }

  @Test
  void should_reject_expired_id_token() {
    AuthorizationStart start = startLogin("google");

    assertRejected(
        callbackWithIdToken(
            start,
            FakeProviderFixture.signedByProvider(
                goodClaims(start)
                    .expirationTime(Date.from(Instant.now().minusSeconds(3600)))
                    .issueTime(Date.from(Instant.now().minusSeconds(7200)))
                    .build())),
        "OAUTH_REJECTED");
  }

  @Test
  void should_reject_authorization_code_refused_by_provider() {
    AuthorizationStart start = startLogin("google");
    FakeProviderFixture.stubGoogleTokenFailure(
        400, "{\"error\":\"invalid_grant\",\"error_description\":\"Bad code\"}");

    assertRejected(callback("google", start.state(), noHeaders()), "OAUTH_REJECTED");
  }

  /// GitHub 拒绝授权码时回 200，错误写在 JSON 里，和 Google、ORCID 的 400 一样算授权没成功，不能说成平台连不上。
  @Test
  void should_reject_github_refusal_returned_with_200() {
    AuthorizationStart start = startLogin("github");
    FakeProviderFixture.stubGithubTokenResponse(
        200,
        "application/json",
        "{\"error\":\"bad_verification_code\",\"error_description\":\"internal-detail-xyz\"}");

    RawResponse response = callback("github", start.state(), noHeaders());

    assertRejected(response, "OAUTH_REJECTED");
    assertThat(response.body()).doesNotContain("internal-detail-xyz");
  }

  @Test
  void should_answer_503_when_token_response_is_not_json() {
    AuthorizationStart start = startLogin("github");
    FakeProviderFixture.stubGithubTokenResponse(200, "text/html", "<html>proxy error</html>");

    RawResponse response = callback("github", start.state(), noHeaders());

    assertThat(response.status()).as(response.body()).isEqualTo(503);
  }

  @Test
  void should_answer_503_when_github_token_endpoint_returns_500() {
    AuthorizationStart start = startLogin("github");
    FakeProviderFixture.stubGithubTokenResponse(500, "text/plain", "oops");

    RawResponse response = callback("github", start.state(), noHeaders());

    assertThat(response.status()).as(response.body()).isEqualTo(503);
  }

  @Test
  void should_answer_503_when_connection_to_github_fails() {
    AuthorizationStart start = startLogin("github");
    FakeProviderFixture.stubGithubTokenConnectionFailure();

    RawResponse response = callback("github", start.state(), noHeaders());

    assertThat(response.status()).as(response.body()).isEqualTo(503);
  }

  @Test
  void should_answer_503_when_provider_is_down() {
    AuthorizationStart start = startLogin("google");
    FakeProviderFixture.stubGoogleTokenFailure(502, "bad gateway");

    RawResponse response = callback("google", start.state(), noHeaders());

    assertThat(response.status()).as(response.body()).isEqualTo(503);
    assertThat(json(response).get("code").asString()).isEqualTo("IDN-0503");
  }

  @Test
  void should_reject_malformed_orcid_subject() {
    AuthorizationStart start = startLogin("orcid");
    FakeProviderFixture.stubOrcid("not-an-orcid-id", start.nonce());

    assertRejected(callback("orcid", start.state(), noHeaders()), "OAUTH_REJECTED");
  }

  @Test
  void should_not_leak_provider_error_description() {
    AuthorizationStart start = startLogin("google");
    FakeProviderFixture.stubGoogleTokenFailure(
        400, "{\"error\":\"invalid_grant\",\"error_description\":\"internal-detail-xyz\"}");

    RawResponse response = callback("google", start.state(), noHeaders());

    assertThat(response.body()).doesNotContain("internal-detail-xyz");
  }
}
