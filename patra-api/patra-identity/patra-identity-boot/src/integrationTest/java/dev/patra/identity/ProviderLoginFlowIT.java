package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.app.account.usecase.deletion.ExecuteDueAccountDeletionsCommand;
import dev.patra.tool.cqrs.CommandBus;
import java.sql.Timestamp;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;

/// 老用户第三方登录（PAP-182）：会话、账号状态分流、注销到期。
class ProviderLoginFlowIT extends ProviderFlowITSupport {

  @Autowired CommandBus commandBus;

  @Test
  void should_issue_session_token_usable_by_gateway() {
    registerViaGithub(9001L, "login-ok", "login-ok", "flow.login.ok@example.com");

    RawResponse response = githubLogin(9001L, "login-ok");

    assertThat(response.status()).as(response.body()).isEqualTo(200);
    assertThat(response.headers().get(HttpHeaders.SET_COOKIE)).isNull();
    String token = sessionTokenOf(response);
    assertThat(token).startsWith("patra_user_");
    JsonNode introspected =
        json(post("/_internal/sessions/introspect", Map.of("token", token), noHeaders()));
    assertThat(introspected.get("active").asBoolean()).isTrue();
    assertThat(introspected.get("userId").asString())
        .isEqualTo(json(response).get("user").get("userId").asString());
    assertThat(redis.opsForHash().hasKey(sessionKey(token), "auth_at")).isFalse();
  }

  @Test
  void should_output_user_id_as_string_in_callback() {
    registerViaGithub(9002L, "login-id", "login-id", "flow.login.id@example.com");

    JsonNode user = json(githubLogin(9002L, "login-id")).get("user");

    assertThat(user.get("userId").isString()).isTrue();
  }

  @Test
  void should_record_device_and_ip_in_login_record() {
    long userId =
        userIdOf(registerViaGithub(9003L, "login-dev", "login-dev", "flow.login.dev@example.com"));
    String device = "Kq3_z-0aB1cD2eF3gH4iJ5kL6mN7oP8qR9sT0uV1wXy";
    AuthorizationStart start = startLogin("github");
    dev.patra.identity.config.FakeProviderFixture.stubGithub(9003L, "login-dev", null);
    HttpHeaders headers = fromShenzhenChrome(noHeaders());
    headers.add("X-Patra-Device-Id", device);

    RawResponse response = callback("github", start.state(), headers);

    assertThat(response.status()).as(response.body()).isEqualTo(200);
    assertThat(
            jdbcTemplate.queryForMap(
                "SELECT device_id, client_ip FROM idn_user_login_record WHERE id = ?",
                latestSessionIdOf(userId)))
        .containsEntry("device_id", device)
        .containsEntry("client_ip", "113.92.157.29");
  }

  @Test
  void should_reject_banned_user_with_403() {
    long userId =
        userIdOf(registerViaGithub(9004L, "login-ban", "login-ban", "flow.login.ban@example.com"));
    assertThat(post("/admin/users/" + userId + "/ban", Map.of(), noHeaders()).status())
        .isEqualTo(204);

    RawResponse response = githubLogin(9004L, "login-ban");

    assertThat(response.status()).as(response.body()).isEqualTo(403);
    assertThat(json(response).get("code").asString()).isEqualTo("IDN-0403");
  }

  @Test
  void should_issue_session_with_deletion_due_at_for_pending_deletion_user() {
    long userId =
        userIdOf(registerViaGithub(9005L, "login-pd", "login-pd", "flow.login.pd@example.com"));
    jdbcTemplate.update(
        "UPDATE idn_user SET status = 'PENDING_DELETION', deletion_due_at = ? WHERE id = ?",
        Timestamp.from(T0.plus(Duration.ofDays(15))),
        userId);

    RawResponse response = githubLogin(9005L, "login-pd");

    assertThat(response.status()).as(response.body()).isEqualTo(200);
    JsonNode user = json(response).get("user");
    assertThat(user.get("status").asString()).isEqualTo("PENDING_DELETION");
    assertThat(user.get("deletionDueAt").asString()).isNotBlank();
  }

  /// D6：注销到期后外部身份行被删除，同一个第三方账号可以重新注册，得到的是新账号。
  @Test
  void should_delete_identity_on_deletion_and_allow_new_registration() {
    long oldId =
        userIdOf(registerViaGithub(9006L, "login-del", "login-del", "flow.login.del@example.com"));
    jdbcTemplate.update(
        "UPDATE idn_user SET status = 'PENDING_DELETION', deletion_due_at = ? WHERE id = ?",
        Timestamp.from(T0.minus(Duration.ofDays(1))),
        oldId);

    commandBus.handle(ExecuteDueAccountDeletionsCommand.of());

    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM idn_user_external_identity WHERE user_id = ?",
                Long.class,
                oldId))
        .isZero();
    assertThat(json(githubLogin(9006L, "login-del")).get("result").asString())
        .isEqualTo("REGISTRATION_REQUIRED");
    long newId =
        userIdOf(
            registerViaGithub(
                9006L, "login-del", "login-del-new", "flow.login.del.new@example.com"));
    assertThat(newId).isNotEqualTo(oldId);
  }
}
