package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.api.account.event.LoginMethodLinkedMailEvent;
import dev.patra.identity.api.account.event.LoginMethodUnlinkedMailEvent;
import dev.patra.identity.api.account.event.PasswordResetMailEvent;
import dev.patra.identity.config.FakeProviderFixture;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import tools.jackson.databind.JsonNode;

/// 绑定、解绑与再认证规则（PAP-184）。
class ProviderLinkFlowIT extends ProviderFlowITSupport {

  /// 已验证邮箱的密码用户。
  ///
  /// @param email 邮箱
  /// @param username 用户名
  /// @return 带用户和会话的断言头
  private Signed verifiedUser(String email, String username) {
    RawResponse registered = register(email, username);
    long userId = userIdOf(registered);
    jdbcTemplate.update("UPDATE idn_user SET email_verified_at = now() WHERE id = ?", userId);
    long sessionId = latestSessionIdOf(userId);
    return new Signed(userId, sessionId, assertionFor(userId, sessionId));
  }

  private record Signed(long userId, long sessionId, HttpHeaders auth) {}

  private AuthorizationStart startLink(String provider, HttpHeaders auth) {
    RawResponse response =
        exchange(HttpMethod.POST, "/me/login-methods/" + provider + "/link", null, auth);
    assertThat(response.status()).as(response.body()).isEqualTo(200);
    return AuthorizationStart.of(json(response).get("authorizationUrl").asString());
  }

  private RawResponse linkGithub(long id, String login, HttpHeaders auth) {
    AuthorizationStart start = startLink("github", auth);
    FakeProviderFixture.stubGithub(id, login, null);
    return callback("github", start.state(), auth);
  }

  private Long identityCount(long userId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM idn_user_external_identity WHERE user_id = ?", Long.class, userId);
  }

  private static String traits(RawResponse response) {
    return json(response).get("traits").toString();
  }

  @Test
  void should_link_list_login_and_unlink() {
    String email = "flow.link.ok@example.com";
    Signed user = verifiedUser(email, "link-ok");

    RawResponse linked = linkGithub(8001L, "link-octo", user.auth());

    assertThat(linked.status()).as(linked.body()).isEqualTo(200);
    JsonNode body = json(linked);
    assertThat(body.get("result").asString()).isEqualTo("LINKED");
    assertThat(body.get("provider").asString()).isEqualTo("github");
    assertThat(body.get("accountLabel").asString()).isEqualTo("link-octo");
    LoginMethodLinkedMailEvent linkedMail = mailEvent(LoginMethodLinkedMailEvent.class, email);
    assertThat(linkedMail.providerName()).isEqualTo("GitHub");
    assertThat(linkedMail.accountLabel()).isEqualTo("link-octo");

    JsonNode methods = json(get("/me/login-methods", user.auth()));
    assertThat(methods.get("hasPassword").asBoolean()).isTrue();
    assertThat(methods.get("linked")).hasSize(1);
    assertThat(methods.get("linked").get(0).get("provider").asString()).isEqualTo("github");
    assertThat(methods.get("linked").get(0).get("accountLabel").asString()).isEqualTo("link-octo");
    assertThat(methods.get("linked").get(0).has("linkedAt")).isTrue();

    RawResponse viaGithub = githubLogin(8001L, "link-octo");
    assertThat(json(viaGithub).get("result").asString()).isEqualTo("AUTHENTICATED");
    assertThat(userIdOf(viaGithub)).isEqualTo(user.userId());

    RawResponse unlinked =
        exchange(HttpMethod.DELETE, "/me/login-methods/github", null, user.auth());

    assertThat(unlinked.status()).as(unlinked.body()).isEqualTo(204);
    assertThat(mailEvent(LoginMethodUnlinkedMailEvent.class, email).providerName())
        .isEqualTo("GitHub");
    assertThat(json(githubLogin(8001L, "link-octo")).get("result").asString())
        .isEqualTo("REGISTRATION_REQUIRED");
  }

  @Test
  void should_answer_404_for_unknown_provider() {
    Signed user = verifiedUser("flow.link.unknown@example.com", "link-unknown");

    assertThat(
            exchange(HttpMethod.POST, "/me/login-methods/wechat/link", null, user.auth()).status())
        .isEqualTo(404);
    assertThat(exchange(HttpMethod.DELETE, "/me/login-methods/wechat", null, user.auth()).status())
        .isEqualTo(404);
  }

  @Test
  void should_require_login_for_login_methods() {
    assertThat(get("/me/login-methods", noHeaders()).status()).isEqualTo(401);
  }

  @Test
  void should_require_reauth_to_start_link() {
    Signed user = verifiedUser("flow.link.reauth@example.com", "link-reauth");
    clock.advance(Duration.ofMinutes(11));

    RawResponse response =
        exchange(
            HttpMethod.POST,
            "/me/login-methods/github/link",
            null,
            assertionFor(user.userId(), user.sessionId()));

    assertThat(response.status()).isEqualTo(403);
    assertThat(traits(response)).contains("REAUTH_REQUIRED");
  }

  @Test
  void should_require_verified_email_to_start_link() {
    RawResponse registered = register("flow.link.unverified@example.com", "link-unverified");
    long userId = userIdOf(registered);

    RawResponse response =
        exchange(
            HttpMethod.POST,
            "/me/login-methods/github/link",
            null,
            assertionFor(userId, latestSessionIdOf(userId)));

    assertThat(response.status()).isEqualTo(409);
    assertThat(traits(response)).contains("EMAIL_NOT_VERIFIED");
  }

  /// D8：用别人的回调绑定。老王发起绑定后把回调链接给小陈，小陈的会话带着它回来，因发起人对不上被拒。
  @Test
  void should_reject_callback_from_another_user() {
    Signed attacker = verifiedUser("flow.link.attacker@example.com", "link-attacker");
    Signed victim = verifiedUser("flow.link.victim@example.com", "link-victim");
    AuthorizationStart start = startLink("github", attacker.auth());
    FakeProviderFixture.stubGithub(8002L, "attacker-github", null);

    RawResponse response = callback("github", start.state(), victim.auth());

    assertThat(response.status()).as(response.body()).isEqualTo(403);
    assertThat(traits(response)).contains("LINK_SESSION_MISMATCH");
    assertThat(identityCount(victim.userId())).isZero();
    assertThat(identityCount(attacker.userId())).isZero();
  }

  @Test
  void should_reject_callback_from_another_session_of_same_user() {
    Signed user = verifiedUser("flow.link.session@example.com", "link-session");
    AuthorizationStart start = startLink("github", user.auth());
    FakeProviderFixture.stubGithub(8003L, "other-session", null);

    RawResponse response =
        callback("github", start.state(), assertionFor(user.userId(), user.sessionId() + 1));

    assertThat(response.status()).isEqualTo(403);
    assertThat(traits(response)).contains("LINK_SESSION_MISMATCH");
    assertThat(identityCount(user.userId())).isZero();
  }

  @Test
  void should_reject_link_callback_without_session() {
    Signed user = verifiedUser("flow.link.nosession@example.com", "link-nosession");
    AuthorizationStart start = startLink("github", user.auth());
    FakeProviderFixture.stubGithub(8004L, "no-session", null);

    RawResponse response = callback("github", start.state(), noHeaders());

    assertThat(response.status()).isEqualTo(401);
    assertThat(identityCount(user.userId())).isZero();
  }

  @Test
  void should_reject_provider_account_linked_to_someone_else() {
    registerViaGithub(8005L, "taken-github", "link-owner", "flow.link.owner@example.com");
    Signed user = verifiedUser("flow.link.second@example.com", "link-second");

    RawResponse response = linkGithub(8005L, "taken-github", user.auth());

    assertThat(response.status()).as(response.body()).isEqualTo(409);
    assertThat(traits(response)).contains("PROVIDER_ACCOUNT_TAKEN");
    assertThat(identityCount(user.userId())).isZero();
  }

  @Test
  void should_reject_second_account_of_same_provider() {
    Signed user = verifiedUser("flow.link.twice@example.com", "link-twice");
    linkGithub(8006L, "first-github", user.auth());

    RawResponse atStart =
        exchange(HttpMethod.POST, "/me/login-methods/github/link", null, user.auth());

    assertThat(atStart.status()).isEqualTo(409);
    assertThat(traits(atStart)).contains("PROVIDER_ALREADY_LINKED");
  }

  @Test
  void should_be_idempotent_when_relinking_same_account() {
    String email = "flow.link.again@example.com";
    Signed user = verifiedUser(email, "link-again");
    AuthorizationStart first = startLink("github", user.auth());
    AuthorizationStart second = startLink("github", user.auth());
    FakeProviderFixture.stubGithub(8007L, "again-github", null);
    assertThat(callback("github", first.state(), user.auth()).status()).isEqualTo(200);
    mailEvent(LoginMethodLinkedMailEvent.class, email);
    securityMails.purge();

    RawResponse again = callback("github", second.state(), user.auth());

    assertThat(again.status()).as(again.body()).isEqualTo(200);
    assertThat(json(again).get("result").asString()).isEqualTo("LINKED");
    assertThat(identityCount(user.userId())).isEqualTo(1);
    assertThat(securityMails.all(LoginMethodLinkedMailEvent.class, email)).isEmpty();
  }

  @Test
  void should_answer_204_without_mail_when_unlinking_nothing() {
    String email = "flow.link.nothing@example.com";
    Signed user = verifiedUser(email, "link-nothing");

    RawResponse response =
        exchange(HttpMethod.DELETE, "/me/login-methods/orcid", null, user.auth());

    assertThat(response.status()).isEqualTo(204);
    assertThat(securityMails.all(LoginMethodUnlinkedMailEvent.class, email)).isEmpty();
  }

  /// 没有密码的人永远过不了再认证：规则排在再认证之前，他们才看得到真实原因。
  @Test
  void should_not_unlink_last_login_method_without_password() {
    RawResponse registered =
        registerViaGithub(8008L, "only-github", "link-only", "flow.link.only@example.com");
    long userId = userIdOf(registered);
    HttpHeaders auth = assertionFor(userId, latestSessionIdOf(userId));

    RawResponse response = exchange(HttpMethod.DELETE, "/me/login-methods/github", null, auth);

    assertThat(response.status()).as(response.body()).isEqualTo(409);
    assertThat(traits(response)).contains("LAST_LOGIN_METHOD");
    assertThat(identityCount(userId)).isEqualTo(1);
  }

  @Test
  void should_require_reauth_to_unlink_when_another_method_remains() {
    RawResponse registered =
        registerViaGithub(8009L, "two-github", "link-two", "flow.link.two@example.com");
    long userId = userIdOf(registered);
    jdbcTemplate.update(
        "INSERT INTO idn_user_external_identity "
            + "(id, user_id, provider, subject, account_label, linked_at) "
            + "VALUES (?, ?, 'ORCID', '0000-0001-5109-3700', 'x', now())",
        userId + 1,
        userId);
    HttpHeaders auth = assertionFor(userId, latestSessionIdOf(userId));

    RawResponse response = exchange(HttpMethod.DELETE, "/me/login-methods/github", null, auth);

    assertThat(response.status()).as(response.body()).isEqualTo(403);
    assertThat(traits(response)).contains("REAUTH_REQUIRED");
    assertThat(identityCount(userId)).isEqualTo(2);
  }

  /// D5：只用第三方登录的用户做敏感操作被要求再认证，`hasPassword` 为假；经找回密码设好后输入密码能完成操作。
  @Test
  void should_guide_third_party_user_to_set_password_for_reauth() {
    String email = "flow.link.sudo@example.com";
    RawResponse registered = registerViaGithub(8010L, "sudo-github", "link-sudo", email);
    long userId = userIdOf(registered);
    assertThat(json(registered).get("user").get("hasPassword").asBoolean()).isFalse();
    HttpHeaders auth = assertionFor(userId, latestSessionIdOf(userId));

    RawResponse denied = post("/me/deletion", Map.of("username", "link-sudo"), auth);

    assertThat(denied.status()).as(denied.body()).isEqualTo(403);
    assertThat(traits(denied)).contains("REAUTH_REQUIRED");
    assertThat(json(get("/auth/me", auth)).get("hasPassword").asBoolean()).isFalse();

    post("/auth/password/forgot", Map.of("email", email), noHeaders());
    String resetToken = tokenIn(mailEvent(PasswordResetMailEvent.class, email).resetUrl());
    assertThat(
            post(
                    "/auth/password/reset",
                    Map.of("token", resetToken, "newPassword", "Set-Password-2026"),
                    noHeaders())
                .status())
        .isEqualTo(200);

    RawResponse viaGithub = githubLogin(8010L, "sudo-github");
    assertThat(json(viaGithub).get("result").asString()).isEqualTo("AUTHENTICATED");
    assertThat(json(viaGithub).get("user").get("hasPassword").asBoolean()).isTrue();
    long sessionId = latestSessionIdOf(userId);
    HttpHeaders newAuth = assertionFor(userId, sessionId);
    assertThat(post("/me/deletion", Map.of("username", "link-sudo"), newAuth).status())
        .isEqualTo(403);

    RawResponse reauth = post("/auth/reauth", Map.of("password", "Set-Password-2026"), newAuth);
    assertThat(reauth.status()).as(reauth.body()).isEqualTo(200);

    assertThat(post("/me/deletion", Map.of("username", "link-sudo"), newAuth).status())
        .isEqualTo(200);
  }
}
