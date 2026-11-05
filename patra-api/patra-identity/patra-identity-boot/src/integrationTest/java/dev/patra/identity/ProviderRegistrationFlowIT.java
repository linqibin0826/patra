package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.api.account.event.PasswordResetMailEvent;
import dev.patra.identity.api.account.event.RegistrationCodeMailEvent;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;

/// 第三方首次注册（PAP-183）：验证码通过才建账号，预劫持路径走不通。
@ExtendWith(OutputCaptureExtension.class)
class ProviderRegistrationFlowIT extends ProviderFlowITSupport {

  private Long usersWithEmail(String email) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM idn_user WHERE email = ?", Long.class, email);
  }

  private Long identitiesOf(String subject) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM idn_user_external_identity WHERE subject = ?", Long.class, subject);
  }

  private static String reason(RawResponse response, String field) {
    for (JsonNode error : json(response).get("errors")) {
      if (error.get("field").asString().equals(field)) {
        return error.get("code").asString();
      }
    }
    throw new AssertionError("没有字段 " + field + " 的错误: " + response.body());
  }

  @Test
  void should_create_account_only_after_code_is_verified() {
    String email = "flow.reg.ok@example.com";
    String ticket = githubTicket(7001L, "reg-ok");

    String code = requestCode(ticket, "reg-ok", email);

    assertThat(usersWithEmail(email)).isZero();
    RawResponse done = completeRegistration(ticket, code);

    assertThat(done.status()).as(done.body()).isEqualTo(201);
    JsonNode user = json(done).get("user");
    assertThat(user.get("username").asString()).isEqualTo("reg-ok");
    assertThat(user.get("emailVerified").asBoolean()).isTrue();
    assertThat(user.get("hasPassword").asBoolean()).isFalse();
    long userId = userIdOf(done);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT email_verified_at IS NOT NULL FROM idn_user WHERE id = ?",
                Boolean.class,
                userId))
        .isTrue();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM idn_user_password_credential WHERE user_id = ?",
                Long.class,
                userId))
        .isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM idn_user_profile WHERE user_id = ?", Long.class, userId))
        .isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT provider || '/' || subject || '/' || account_label "
                    + "FROM idn_user_external_identity WHERE user_id = ?",
                String.class,
                userId))
        .isEqualTo("GITHUB/7001/reg-ok");
    assertThat(redis.opsForHash().hasKey(sessionKey(sessionTokenOf(done)), "auth_at")).isFalse();
  }

  @Test
  void should_return_to_same_account_on_next_login() {
    long userId =
        userIdOf(registerViaGithub(7002L, "reg-again", "reg-again", "flow.reg.again@example.com"));

    RawResponse again = githubLogin(7002L, "reg-renamed");

    assertThat(json(again).get("result").asString()).isEqualTo("AUTHENTICATED");
    assertThat(userIdOf(again)).isEqualTo(userId);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT account_label FROM idn_user_external_identity WHERE user_id = ?",
                String.class,
                userId))
        .isEqualTo("reg-renamed");
  }

  @Test
  void should_show_prefill_and_restore_pending_code() {
    String ticket = githubTicket(7003L, "reg-Prefill");

    JsonNode before = json(get("/auth/oauth/registration", ticketHeader(ticket)));

    assertThat(before.get("provider").asString()).isEqualTo("github");
    assertThat(before.get("accountLabel").asString()).isEqualTo("reg-Prefill");
    assertThat(before.get("suggestedUsername").asString()).isEqualTo("reg-prefill");
    assertThat(before.get("pendingCode").isNull()).isTrue();

    requestCode(ticket, "reg-prefill", "flow.reg.prefill@example.com");

    JsonNode after = json(get("/auth/oauth/registration", ticketHeader(ticket))).get("pendingCode");
    assertThat(after.get("username").asString()).isEqualTo("reg-prefill");
    assertThat(after.get("email").asString()).isEqualTo("flow.reg.prefill@example.com");
    assertThat(after.has("codeExpiresAt")).isTrue();
    assertThat(after.has("nextResendAt")).isTrue();
  }

  @Test
  void should_reject_missing_or_unknown_ticket() {
    RawResponse missing = get("/auth/oauth/registration", noHeaders());
    RawResponse unknown =
        post(
            "/auth/oauth/registration/code",
            Map.of("username", "reg-x", "email", "flow.reg.x@example.com"),
            ticketHeader("A".repeat(43)));

    assertThat(missing.status()).isEqualTo(422);
    assertThat(reason(missing, "ticket")).isEqualTo("REQUIRED");
    assertThat(unknown.status()).isEqualTo(422);
    assertThat(reason(unknown, "ticket")).isEqualTo("INVALID");
  }

  @Test
  void should_not_send_code_when_email_is_registered() {
    String email = "flow.reg.taken@example.com";
    RawResponse existing = register(email, "reg-owner");
    long ownerId = userIdOf(existing);
    Map<String, Object> before =
        jdbcTemplate.queryForMap("SELECT * FROM idn_user WHERE id = ?", ownerId);
    String ticket = githubTicket(7004L, "reg-taken");
    securityMails.purge();

    RawResponse response =
        post(
            "/auth/oauth/registration/code",
            Map.of("username", "reg-taken", "email", email),
            ticketHeader(ticket));

    assertThat(response.status()).as(response.body()).isEqualTo(409);
    assertThat(json(response).get("code").asString()).isEqualTo("IDN-0409");
    assertThat(reason(response, "email")).isEqualTo("ALREADY_REGISTERED");
    assertThat(securityMails.all(RegistrationCodeMailEvent.class, email)).isEmpty();
    assertThat(usersWithEmail(email)).isEqualTo(1);
    assertThat(identitiesOf("7004")).isZero();
    assertThat(jdbcTemplate.queryForMap("SELECT * FROM idn_user WHERE id = ?", ownerId))
        .isEqualTo(before);
  }

  @Test
  void should_report_field_errors_and_taken_username() {
    register("flow.reg.name@example.com", "reg-name");
    String ticket = githubTicket(7005L, "reg-fields");

    RawResponse invalid =
        post(
            "/auth/oauth/registration/code",
            Map.of("username", "a", "email", "not-an-email"),
            ticketHeader(ticket));
    RawResponse taken =
        post(
            "/auth/oauth/registration/code",
            Map.of("username", "reg-name", "email", "flow.reg.name2@example.com"),
            ticketHeader(ticket));

    assertThat(invalid.status()).isEqualTo(422);
    assertThat(reason(invalid, "username")).isEqualTo("TOO_SHORT");
    assertThat(reason(invalid, "email")).isEqualTo("INVALID_FORMAT");
    assertThat(taken.status()).isEqualTo(409);
    assertThat(reason(taken, "username")).isEqualTo("ALREADY_TAKEN");
  }

  @Test
  void should_limit_resend_for_same_email() {
    String email = "flow.reg.cooldown@example.com";
    String ticket = githubTicket(7006L, "reg-cooldown");
    requestCode(ticket, "reg-cooldown", email);

    RawResponse again =
        post(
            "/auth/oauth/registration/code",
            Map.of("username", "reg-cooldown", "email", email),
            ticketHeader(ticket));

    assertThat(again.status()).isEqualTo(429);
    assertThat(again.header(HttpHeaders.RETRY_AFTER)).isNotNull();
    assertThat(json(again).get("code").asString()).isEqualTo("IDN-0429");
  }

  @Test
  void should_invalidate_code_after_five_wrong_attempts() {
    String email = "flow.reg.wrong@example.com";
    String ticket = githubTicket(7007L, "reg-wrong");
    String code = requestCode(ticket, "reg-wrong", email);
    String wrong = "000000".equals(code) ? "111111" : "000000";

    for (int i = 0; i < 4; i++) {
      RawResponse attempt = completeRegistration(ticket, wrong);
      assertThat(attempt.status()).isEqualTo(422);
      assertThat(reason(attempt, "code")).isEqualTo("INVALID");
    }
    RawResponse fifth = completeRegistration(ticket, wrong);
    RawResponse right = completeRegistration(ticket, code);

    assertThat(reason(fifth, "code")).isEqualTo("EXPIRED");
    assertThat(right.status()).isEqualTo(422);
    assertThat(reason(right, "code")).isEqualTo("EXPIRED");
    assertThat(usersWithEmail(email)).isZero();
    assertThat(identitiesOf("7007")).isZero();
  }

  @Test
  void should_reject_malformed_code_without_spending_attempt() {
    String ticket = githubTicket(7008L, "reg-format");

    RawResponse response = completeRegistration(ticket, "12ab");

    assertThat(response.status()).isEqualTo(422);
    assertThat(reason(response, "code")).isEqualTo("INVALID_FORMAT");
  }

  /// 预劫持：老王用自己的 GitHub 填小陈的邮箱。账号在验证通过前不存在，小陈照常注册；小陈走找回密码进入账号后，
  /// 老王的 GitHub 没有绑在上面。
  @Test
  void should_not_let_attacker_pre_hijack_victim_email() {
    String victim = "flow.reg.victim@example.com";
    String attackerTicket = githubTicket(7009L, "attacker");
    requestCode(attackerTicket, "reg-attacker", victim);

    assertThat(usersWithEmail(victim)).isZero();

    long victimId = userIdOf(register(victim, "reg-victim"));
    clock.advance(Duration.ofMinutes(2));
    post("/auth/password/forgot", Map.of("email", victim), noHeaders());
    String resetToken = tokenIn(mailEvent(PasswordResetMailEvent.class, victim).resetUrl());
    RawResponse reset =
        post(
            "/auth/password/reset",
            Map.of("token", resetToken, "newPassword", "Victim-Pass-2026"),
            noHeaders());

    assertThat(reset.status()).as(reset.body()).isEqualTo(200);
    assertThat(identitiesOf("7009")).isZero();
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT count(*) FROM idn_user_external_identity WHERE user_id = ?",
                Long.class,
                victimId))
        .isZero();
    assertThat(json(githubLogin(7009L, "attacker")).get("result").asString())
        .isEqualTo("REGISTRATION_REQUIRED");
  }

  /// 验证码发出去之后邮箱被别人注册：回到表单，账号不建，外部身份不写。
  @Test
  void should_reject_when_email_taken_between_code_and_complete() {
    String email = "flow.reg.race@example.com";
    String ticket = githubTicket(7010L, "reg-race");
    String code = requestCode(ticket, "reg-race", email);
    register(email, "reg-race-winner");

    RawResponse done = completeRegistration(ticket, code);

    assertThat(done.status()).as(done.body()).isEqualTo(409);
    assertThat(reason(done, "email")).isEqualTo("ALREADY_REGISTERED");
    assertThat(identitiesOf("7010")).isZero();
    assertThat(
            json(get("/auth/oauth/registration", ticketHeader(ticket))).get("provider").asString())
        .isEqualTo("github");
  }

  /// 同一个第三方账号在两个标签页都走到了最后一步：第二个 409，不会多一个账号。
  @Test
  void should_reject_second_registration_of_same_provider_account() {
    String firstTicket = githubTicket(7011L, "reg-twice");
    String secondTicket = githubTicket(7011L, "reg-twice");
    String secondEmail = "flow.reg.twice2@example.com";
    String firstCode = requestCode(firstTicket, "reg-twice", "flow.reg.twice@example.com");
    String secondCode = requestCode(secondTicket, "reg-twice-2", secondEmail);
    assertThat(completeRegistration(firstTicket, firstCode).status()).isEqualTo(201);

    RawResponse second = completeRegistration(secondTicket, secondCode);

    assertThat(second.status()).as(second.body()).isEqualTo(409);
    assertThat(json(second).get("traits").toString()).contains("PROVIDER_ACCOUNT_TAKEN");
    assertThat(usersWithEmail(secondEmail)).isZero();
    assertThat(identitiesOf("7011")).isEqualTo(1);
  }

  @Test
  void should_not_log_code_or_ticket(CapturedOutput output) {
    String ticket = githubTicket(7012L, "reg-log");
    String code = requestCode(ticket, "reg-log", "flow.reg.log@example.com");
    completeRegistration(ticket, code);

    assertThat(output.getAll()).doesNotContain(ticket);
    assertThat(output.getAll()).doesNotContain("code=" + code);
    assertThat(redis.keys("idn:oauth:*")).allSatisfy(key -> assertThat(key).doesNotContain(ticket));
  }
}
