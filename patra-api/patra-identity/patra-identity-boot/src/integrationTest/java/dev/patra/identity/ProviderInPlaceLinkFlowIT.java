package dev.patra.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.patra.identity.api.account.event.LoginMethodLinkCodeMailEvent;
import dev.patra.identity.api.account.event.LoginMethodLinkedMailEvent;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.JsonNode;

/// 邮箱撞车时就地绑定（决策 D 的补充）：证明邮箱归属后绑到已有账号，不符合条件时不多泄露。
class ProviderInPlaceLinkFlowIT extends ProviderFlowITSupport {

  private long verifiedUser(String email, String username) {
    long userId = userIdOf(register(email, username));
    markEmailVerified(userId);
    return userId;
  }

  private RawResponse requestLinkCode(String ticket, String email) {
    return post("/auth/oauth/registration/link-code", Map.of("email", email), ticketHeader(ticket));
  }

  private RawResponse link(String ticket, String code) {
    return post("/auth/oauth/registration/link", Map.of("code", code), ticketHeader(ticket));
  }

  private Long identitiesOf(long userId) {
    return jdbcTemplate.queryForObject(
        "SELECT count(*) FROM idn_user_external_identity WHERE user_id = ?", Long.class, userId);
  }

  private Long identitiesWithSubject(String subject) {
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

  private static String traits(RawResponse response) {
    return json(response).get("traits").toString();
  }

  /// 验证码发到邮箱，填回后绑到已有账号并登录。
  @Test
  void should_link_to_verified_account_and_sign_in() {
    String email = "flow.inplace.ok@example.com";
    long userId = verifiedUser(email, "inplace-ok");
    String ticket = githubTicket(12001L, "inplace-octo");
    RawResponse collision =
        post(
            "/auth/oauth/registration/code",
            Map.of("username", "inplace-new", "email", email),
            ticketHeader(ticket));
    assertThat(collision.status()).as(collision.body()).isEqualTo(409);

    RawResponse sent = requestLinkCode(ticket, email);

    assertThat(sent.status()).as(sent.body()).isEqualTo(202);
    assertThat(json(sent).get("email").asString()).isEqualTo(email);
    LoginMethodLinkCodeMailEvent mail = mailEvent(LoginMethodLinkCodeMailEvent.class, email);
    assertThat(mail.providerName()).isEqualTo("GitHub");
    assertThat(mail.accountLabel()).isEqualTo("inplace-octo");
    assertThat(mail.username()).isEqualTo("inplace-ok");
    assertThat(identitiesOf(userId)).isZero();

    RawResponse done = link(ticket, mail.code());

    assertThat(done.status()).as(done.body()).isEqualTo(200);
    assertThat(userIdOf(done)).isEqualTo(userId);
    assertThat(json(done).get("user").get("hasPassword").asBoolean()).isTrue();
    assertThat(identitiesOf(userId)).isEqualTo(1);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT last_login_at IS NOT NULL FROM idn_user_external_identity WHERE user_id = ?",
                Boolean.class,
                userId))
        .isTrue();
    LoginMethodLinkedMailEvent linked = mailEvent(LoginMethodLinkedMailEvent.class, email);
    assertThat(linked.accountLabel()).isEqualTo("inplace-octo");

    RawResponse again = githubLogin(12001L, "inplace-octo");
    assertThat(json(again).get("result").asString()).isEqualTo("AUTHENTICATED");
    assertThat(userIdOf(again)).isEqualTo(userId);
  }

  /// 新会话不算再认证：邮箱验证码不是密码证明。
  @Test
  void should_not_open_reauth_window() {
    String email = "flow.inplace.sudo@example.com";
    long userId = verifiedUser(email, "inplace-sudo");
    String ticket = githubTicket(12002L, "inplace-sudo");
    requestLinkCode(ticket, email);
    String code = mailEvent(LoginMethodLinkCodeMailEvent.class, email).code();

    RawResponse done = link(ticket, code);

    assertThat(redis.opsForHash().hasKey(sessionKey(sessionTokenOf(done)), "auth_at")).isFalse();
    HttpHeaders auth = assertionFor(userId, latestSessionIdOf(userId));
    RawResponse sensitive = post("/me/login-methods/orcid/link", Map.of(), auth);
    assertThat(sensitive.status()).as(sensitive.body()).isEqualTo(403);
    assertThat(traits(sensitive)).contains("REAUTH_REQUIRED");
  }

  /// 未验证邮箱的账号可能是抢注的：抢注者的密码还留着，绝不能绑。响应、错码都和正常一样，只是没有信。
  @Test
  void should_never_link_to_unverified_account_without_leaking() {
    String email = "flow.inplace.unverified@example.com";
    long userId = userIdOf(register(email, "inplace-unverified"));
    String ticket = githubTicket(12003L, "inplace-unv");
    securityMails.purge();

    RawResponse sent = requestLinkCode(ticket, email);

    assertThat(sent.status()).as(sent.body()).isEqualTo(202);
    assertThat(json(sent).has("codeExpiresAt")).isTrue();
    assertThat(json(sent).has("nextResendAt")).isTrue();
    JsonNode pending =
        json(get("/auth/oauth/registration", ticketHeader(ticket))).get("pendingCode");
    assertThat(pending.get("purpose").asString()).isEqualTo("LINK");
    assertThat(pending.get("username").isNull()).isTrue();
    assertThat(pending.get("email").asString()).isEqualTo(email);
    for (int i = 0; i < 4; i++) {
      assertThat(reason(link(ticket, "000000"), "code")).isEqualTo("INVALID");
    }
    assertThat(reason(link(ticket, "000000"), "code")).isEqualTo("EXPIRED");
    securityMails.awaitRegistryDrained();
    assertThat(securityMails.all(LoginMethodLinkCodeMailEvent.class, email)).isEmpty();
    assertThat(identitiesOf(userId)).isZero();
  }

  @Test
  void should_not_send_for_banned_or_unknown_email() {
    String banned = "flow.inplace.banned@example.com";
    long bannedId = verifiedUser(banned, "inplace-banned");
    applyStatus(bannedId, "BANNED");
    String unknown = "flow.inplace.nobody@example.com";
    String ticket = githubTicket(12004L, "inplace-ban");
    securityMails.purge();

    assertThat(requestLinkCode(ticket, banned).status()).isEqualTo(202);
    clock.advance(Duration.ofMinutes(2));
    assertThat(requestLinkCode(ticket, unknown).status()).isEqualTo(202);

    securityMails.awaitRegistryDrained();
    assertThat(securityMails.all(LoginMethodLinkCodeMailEvent.class, banned)).isEmpty();
    assertThat(securityMails.all(LoginMethodLinkCodeMailEvent.class, unknown)).isEmpty();
    assertThat(reason(link(ticket, "000000"), "code")).isEqualTo("INVALID");
    assertThat(identitiesOf(bannedId)).isZero();
  }

  @Test
  void should_not_link_with_wrong_code_and_void_after_five_attempts() {
    String email = "flow.inplace.wrong@example.com";
    long userId = verifiedUser(email, "inplace-wrong");
    String ticket = githubTicket(12005L, "inplace-wrong");
    requestLinkCode(ticket, email);
    String code = mailEvent(LoginMethodLinkCodeMailEvent.class, email).code();
    String wrong = "000000".equals(code) ? "111111" : "000000";

    for (int i = 0; i < 4; i++) {
      assertThat(reason(link(ticket, wrong), "code")).isEqualTo("INVALID");
    }
    assertThat(reason(link(ticket, wrong), "code")).isEqualTo("EXPIRED");

    assertThat(reason(link(ticket, code), "code")).isEqualTo("EXPIRED");
    assertThat(identitiesOf(userId)).isZero();
    assertThat(identitiesWithSubject("12005")).isZero();
  }

  /// 凭证用过一次就没了：重放回 ticket / INVALID，不会再绑、再发会话。
  @Test
  void should_reject_replayed_ticket() {
    String email = "flow.inplace.replay@example.com";
    long userId = verifiedUser(email, "inplace-replay");
    String ticket = githubTicket(12006L, "inplace-replay");
    requestLinkCode(ticket, email);
    String code = mailEvent(LoginMethodLinkCodeMailEvent.class, email).code();
    assertThat(link(ticket, code).status()).isEqualTo(200);

    RawResponse replay = link(ticket, code);

    assertThat(replay.status()).as(replay.body()).isEqualTo(422);
    assertThat(reason(replay, "ticket")).isEqualTo("INVALID");
    assertThat(identitiesOf(userId)).isEqualTo(1);
  }

  /// 两种用途的验证码不能混用：注册用途的码绑不了，绑定用途的码也建不了账号，都不计错误次数。
  @Test
  void should_not_mix_code_purposes() {
    String registerEmail = "flow.inplace.reg@example.com";
    String ticket = githubTicket(12007L, "inplace-mix");
    String registerCode = requestCode(ticket, "inplace-mix", registerEmail);

    RawResponse linkWithRegisterCode = link(ticket, registerCode);

    assertThat(reason(linkWithRegisterCode, "code")).isEqualTo("EXPIRED");
    assertThat(completeRegistration(ticket, registerCode).status()).isEqualTo(201);

    String email = "flow.inplace.mix@example.com";
    verifiedUser(email, "inplace-mix-owner");
    String secondTicket = githubTicket(12008L, "inplace-mix2");
    requestLinkCode(secondTicket, email);
    String linkCode = mailEvent(LoginMethodLinkCodeMailEvent.class, email).code();

    RawResponse registerWithLinkCode = completeRegistration(secondTicket, linkCode);

    assertThat(reason(registerWithLinkCode, "code")).isEqualTo("EXPIRED");
    assertThat(link(secondTicket, linkCode).status()).isEqualTo(200);
  }

  @Test
  void should_reject_when_provider_account_taken_by_someone_else() {
    String email = "flow.inplace.taken@example.com";
    long userId = verifiedUser(email, "inplace-taken");
    String ticket = githubTicket(12009L, "inplace-taken");
    requestLinkCode(ticket, email);
    String code = mailEvent(LoginMethodLinkCodeMailEvent.class, email).code();
    registerViaGithub(
        12009L, "inplace-taken", "inplace-taken-2", "flow.inplace.taken2@example.com");

    RawResponse done = link(ticket, code);

    assertThat(done.status()).as(done.body()).isEqualTo(409);
    assertThat(traits(done)).contains("PROVIDER_ACCOUNT_TAKEN");
    assertThat(identitiesOf(userId)).isZero();
  }

  @Test
  void should_reject_when_target_account_already_linked_same_provider() {
    String email = "flow.inplace.twice@example.com";
    long userId = verifiedUser(email, "inplace-twice");
    jdbcTemplate.update(
        "INSERT INTO idn_user_external_identity "
            + "(id, user_id, provider, subject, account_label, linked_at) "
            + "VALUES (?, ?, 'GITHUB', '12099', 'old', now())",
        userId + 1,
        userId);
    String ticket = githubTicket(12010L, "inplace-twice");
    requestLinkCode(ticket, email);
    String code = mailEvent(LoginMethodLinkCodeMailEvent.class, email).code();

    RawResponse done = link(ticket, code);

    assertThat(done.status()).as(done.body()).isEqualTo(409);
    assertThat(traits(done)).contains("PROVIDER_ALREADY_LINKED");
    assertThat(identitiesWithSubject("12010")).isZero();
  }

  /// 两个标签页：同一个第三方账号已经绑到这个账号了，按登录处理，不再发一封。
  @Test
  void should_be_idempotent_when_already_linked_to_same_account() {
    String email = "flow.inplace.same@example.com";
    long userId = verifiedUser(email, "inplace-same");
    String first = githubTicket(12011L, "inplace-same");
    String second = githubTicket(12011L, "inplace-same");
    requestLinkCode(first, email);
    String firstCode = mailEvent(LoginMethodLinkCodeMailEvent.class, email).code();
    assertThat(link(first, firstCode).status()).isEqualTo(200);
    mailEvent(LoginMethodLinkedMailEvent.class, email);
    clock.advance(Duration.ofMinutes(2));
    securityMails.purge();
    requestLinkCode(second, email);
    String secondCode = mailEvent(LoginMethodLinkCodeMailEvent.class, email).code();

    RawResponse done = link(second, secondCode);

    assertThat(done.status()).as(done.body()).isEqualTo(200);
    assertThat(userIdOf(done)).isEqualTo(userId);
    securityMails.awaitRegistryDrained();
    List<LoginMethodLinkedMailEvent> mails =
        securityMails.all(LoginMethodLinkedMailEvent.class, email);
    assertThat(mails).isEmpty();
    assertThat(identitiesOf(userId)).isEqualTo(1);
  }

  /// 注销冷静期的账号：和登录分流一致，照常绑定并建会话，带到期时间。
  @Test
  void should_link_pending_deletion_account_like_login() {
    String email = "flow.inplace.pd@example.com";
    long userId = verifiedUser(email, "inplace-pd");
    applyStatus(userId, "PENDING_DELETION");
    String ticket = githubTicket(12012L, "inplace-pd");
    requestLinkCode(ticket, email);
    String code = mailEvent(LoginMethodLinkCodeMailEvent.class, email).code();

    RawResponse done = link(ticket, code);

    assertThat(done.status()).as(done.body()).isEqualTo(200);
    assertThat(json(done).get("user").get("status").asString()).isEqualTo("PENDING_DELETION");
    assertThat(json(done).get("user").get("deletionDueAt").asString()).isNotBlank();
  }

  /// 发码之后账号被封禁：验证码再对也不绑，和没资格时同样回 INVALID。
  @Test
  void should_recheck_eligibility_when_linking() {
    String email = "flow.inplace.late@example.com";
    long userId = verifiedUser(email, "inplace-late");
    String ticket = githubTicket(12013L, "inplace-late");
    requestLinkCode(ticket, email);
    String code = mailEvent(LoginMethodLinkCodeMailEvent.class, email).code();
    applyStatus(userId, "BANNED");

    RawResponse done = link(ticket, code);

    assertThat(done.status()).as(done.body()).isEqualTo(422);
    assertThat(reason(done, "code")).isEqualTo("INVALID");
    assertThat(identitiesOf(userId)).isZero();
  }

  /// 拿到了对的验证码，账号的邮箱却不再是已验证：对的码也不绑。
  @Test
  void should_not_link_unverified_account_even_with_correct_code() {
    String email = "flow.inplace.correct@example.com";
    long userId = verifiedUser(email, "inplace-correct");
    String ticket = githubTicket(12015L, "inplace-correct");
    requestLinkCode(ticket, email);
    String code = mailEvent(LoginMethodLinkCodeMailEvent.class, email).code();
    jdbcTemplate.update("UPDATE idn_user SET email_verified_at = NULL WHERE id = ?", userId);

    RawResponse done = link(ticket, code);

    assertThat(done.status()).as(done.body()).isEqualTo(422);
    assertThat(reason(done, "code")).isEqualTo("INVALID");
    assertThat(identitiesOf(userId)).isZero();
    assertThat(identitiesWithSubject("12015")).isZero();
  }

  /// 每日上限按邮箱算，在判断资格之前：有资格和没注册的邮箱，到第 11 封都是同样的 429 和同样的等待时间。
  @Test
  void should_answer_same_429_at_daily_limit_whatever_account_state() {
    String eligible = "flow.inplace.daily@example.com";
    verifiedUser(eligible, "inplace-daily");
    String unknown = "flow.inplace.daily.nobody@example.com";
    for (int i = 0; i < 10; i++) {
      String ticket = githubTicket(12016L, "inplace-daily");
      assertThat(requestLinkCode(ticket, eligible).status()).isEqualTo(202);
      assertThat(requestLinkCode(ticket, unknown).status()).isEqualTo(202);
      clock.advance(Duration.ofHours(2));
    }
    String ticket = githubTicket(12016L, "inplace-daily");

    RawResponse first = requestLinkCode(ticket, eligible);
    RawResponse second = requestLinkCode(ticket, unknown);

    assertThat(first.status()).as(first.body()).isEqualTo(429);
    assertThat(second.status()).as(second.body()).isEqualTo(429);
    assertThat(first.header(HttpHeaders.RETRY_AFTER)).isEqualTo("14400");
    assertThat(second.header(HttpHeaders.RETRY_AFTER)).isEqualTo("14400");
    assertThat(traits(first)).isEqualTo(traits(second)).contains("QUOTA_EXCEEDED");
    assertThat(json(first).get("retryAfterSeconds"))
        .isEqualTo(json(second).get("retryAfterSeconds"));
  }

  @Test
  void should_report_field_errors_and_limit_resend() {
    String ticket = githubTicket(12014L, "inplace-limit");

    RawResponse invalid = requestLinkCode(ticket, "not-an-email");
    assertThat(invalid.status()).isEqualTo(422);
    assertThat(reason(invalid, "email")).isEqualTo("INVALID_FORMAT");

    String email = "flow.inplace.limit@example.com";
    assertThat(requestLinkCode(ticket, email).status()).isEqualTo(202);
    RawResponse again = requestLinkCode(ticket, email);
    assertThat(again.status()).isEqualTo(429);
    assertThat(again.header(HttpHeaders.RETRY_AFTER)).isNotNull();

    assertThat(requestLinkCode("A".repeat(43), email).status()).isEqualTo(422);
  }
}
